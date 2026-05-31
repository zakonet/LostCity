package common.cn.kafei.simukraft.farmland;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import common.cn.kafei.simukraft.registry.ModBlocks;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 农田盒耕作服务：真种真收。农民被移动到农田后，逐格耕地、消耗箱子里的真实种子种植、
 * 等原版自然生长、收获成熟作物的真实掉落物并入箱。绝不凭空产出。
 *
 * 性能约束：只处理 running 的农田盒；每个盒子有独立节流，每次只处理少量格子（轮转游标），
 * 不每 tick 全量扫描区域；目标区块未加载时跳过，不强制加载区块。
 */
@SuppressWarnings("null")
public final class FarmlandFarmingService {
    private static final ConcurrentMap<String, LevelRuntime> RUNTIMES = new ConcurrentHashMap<>();
    private static final long IDLE_INTERVAL_TICKS = 40L;
    private static final long MOVE_INTERVAL_TICKS = 12L;

    private FarmlandFarmingService() {
    }

    public static void tick(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }
        long gameTime = level.getGameTime();
        FarmlandBoxManager manager = FarmlandBoxManager.get(level);
        LevelRuntime runtime = runtime(level);
        for (FarmlandBoxData data : manager.all()) {
            if (!data.running()) {
                runtime.boxes.remove(data.boxPos());
                continue;
            }
            BoxRuntime boxRuntime = runtime.boxes.computeIfAbsent(data.boxPos(), pos -> new BoxRuntime());
            if (gameTime < boxRuntime.nextActionTick) {
                continue;
            }
            tickBox(level, manager, data, boxRuntime, gameTime);
        }
    }

    public static void clearServerCaches(MinecraftServer server) {
        // runtimeKey 用小写，这里也按小写匹配，避免 Windows 盘符大小写导致清不掉缓存。
        String serverKey = SaveScopedCacheKey.serverKey(server).toLowerCase(Locale.ROOT);
        RUNTIMES.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
    }

    private static void tickBox(ServerLevel level, FarmlandBoxManager manager, FarmlandBoxData data, BoxRuntime boxRuntime, long gameTime) {
        BlockPos boxPos = data.boxPos();
        if (!level.isLoaded(boxPos)) {
            boxRuntime.nextActionTick = gameTime + IDLE_INTERVAL_TICKS;
            return;
        }
        if (!level.getBlockState(boxPos).is(ModBlocks.NSUK_FARMLAND_BOX.get())) {
            // 方块已不在（被替换等异常）：停止运行，破坏事件会负责删除数据。
            data.setRunning(false);
            manager.persist(data);
            return;
        }
        if (!data.isConfigured()) {
            data.setRunning(false);
            manager.persist(data);
            return;
        }
        CitizenData farmer = FarmlandBoxService.findAssignedFarmer(level, boxPos);
        if (farmer == null) {
            // 农民被解雇/死亡：停止耕作，等玩家重新雇佣。
            data.setRunning(false);
            manager.persist(data);
            return;
        }
        CitizenEntity farmerEntity = CitizenTeleportService.findCitizenEntity(level, farmer.uuid());
        if (farmerEntity == null) {
            boxRuntime.nextActionTick = gameTime + IDLE_INTERVAL_TICKS;
            return;
        }

        FarmlandPlot plot = data.plot();
        // 农民待在农田盒处工作，不跑到田中间；耕作由服务端按格子推进。
        Vec3 anchor = Vec3.atBottomCenterOf(boxPos.above());
        double reach = 3.0D;
        if (farmerEntity.position().distanceToSqr(anchor) > reach * reach) {
            boolean moving = CitizenNavigationService.requestMove(level, farmer.uuid(), anchor, MovementIntent.WORK);
            if (!moving) {
                CitizenTeleportService.teleportCitizen(level, farmer.uuid(), anchor);
            }
            applyFarmerStatus(level, farmer, boxRuntime, data.crop());
            boxRuntime.nextActionTick = gameTime + MOVE_INTERVAL_TICKS;
            return;
        }

        // 仓储箱自动检测：只认紧贴农田盒六个面的容器。没有就跳过本轮（无处取种、无处存收成）。
        BlockPos chestPos = FarmlandBoxService.resolveAdjacentChest(level, boxPos);
        if (chestPos == null) {
            boxRuntime.nextActionTick = gameTime + IDLE_INTERVAL_TICKS;
            return;
        }
        applyFarmerStatus(level, farmer, boxRuntime, data.crop());
        int actions = Math.max(1, ServerConfig.farmActionsPerCycle());
        int cells = Math.max(1, plot.cellCount());
        int inspected = 0;
        int performed = 0;
        // 从游标开始最多检查整块一遍，最多执行 actions 次有效动作，避免空转和全量遍历。
        while (inspected < cells && performed < actions) {
            BlockPos cell = plot.cellAt(boxRuntime.cursor);
            boxRuntime.cursor++;
            inspected++;
            if (cell.getX() == boxPos.getX() && cell.getZ() == boxPos.getZ()) {
                continue;
            }
            if (!level.isLoaded(cell)) {
                continue;
            }
            if (workCell(level, data, chestPos, cell)) {
                performed++;
            }
        }
        boxRuntime.nextActionTick = gameTime + Math.max(1, ServerConfig.farmWorkIntervalTicks());
    }

    // 单格作业：先收获腾出空间，再耕地，最后在熟地上消耗种子种植。返回是否产生了有效动作。
    private static boolean workCell(ServerLevel level, FarmlandBoxData data, BlockPos chestPos, BlockPos cropPos) {
        FarmCrop crop = data.crop();
        BlockPos soilPos = cropPos.below();
        BlockState soilState = level.getBlockState(soilPos);
        BlockState cropState = level.getBlockState(cropPos);

        if (crop.isStem()) {
            if (crop.isOwnPlant(cropState) && harvestProduceAround(level, chestPos, crop, cropPos)) {
                return true;
            }
        } else if (crop.isOwnPlant(cropState) && crop.isMatureFull(cropState)) {
            harvestBlock(level, chestPos, cropPos, cropState);
            return true;
        }

        boolean cropCellFree = cropState.isAir() || cropState.canBeReplaced();
        boolean plantableHere = crop.shouldPlantAt(cropPos.getX(), cropPos.getZ());
        if (cropCellFree && plantableHere && isTillable(soilState)) {
            level.setBlock(soilPos, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
            return true;
        }
        if (cropCellFree && plantableHere && soilState.is(Blocks.FARMLAND)) {
            if (consumeSeed(level, chestPos, crop)) {
                level.setBlock(cropPos, crop.plantState(), 3);
                return true;
            }
        }
        return false;
    }

    private static void harvestBlock(ServerLevel level, BlockPos chestPos, BlockPos pos, BlockState state) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        depositDrops(level, chestPos, drops, pos);
    }

    private static boolean harvestProduceAround(ServerLevel level, BlockPos chestPos, FarmCrop crop, BlockPos stemPos) {
        boolean harvested = false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos producePos = stemPos.relative(direction);
            if (!level.isLoaded(producePos)) {
                continue;
            }
            BlockState produceState = level.getBlockState(producePos);
            if (crop.isProduce(produceState)) {
                List<ItemStack> drops = Block.getDrops(produceState, level, producePos, level.getBlockEntity(producePos));
                level.setBlock(producePos, Blocks.AIR.defaultBlockState(), 3);
                depositDrops(level, chestPos, drops, producePos);
                harvested = true;
            }
        }
        return harvested;
    }

    private static boolean consumeSeed(ServerLevel level, BlockPos chestPos, FarmCrop crop) {
        if (chestPos == null) {
            return false;
        }
        List<GenericContainerAccess.SlotSnapshot> slots = GenericContainerAccess.snapshotSlots(level, chestPos);
        for (GenericContainerAccess.SlotSnapshot slot : slots) {
            if (slot.stack().getItem() == crop.seed()) {
                return GenericContainerAccess.consumeSingleItemAtSlot(level, chestPos, slot.slot(), slot.access(), slot.side(), crop.seed());
            }
        }
        return false;
    }

    private static void depositDrops(ServerLevel level, BlockPos chestPos, List<ItemStack> drops, BlockPos fallbackPos) {
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            ItemStack leftover = chestPos != null ? GenericContainerAccess.insert(level, chestPos, drop) : drop;
            if (!leftover.isEmpty()) {
                // 箱子满了就掉在作物处，不丢失产出。
                Block.popResource(level, fallbackPos, leftover);
            }
        }
    }

    private static void applyFarmerStatus(ServerLevel level, CitizenData farmer, BoxRuntime boxRuntime, FarmCrop crop) {
        String label = "耕作中: " + (crop != null ? crop.id() : "");
        if (label.equals(boxRuntime.lastStatusLabel) && farmer.workStatusType() == CitizenWorkStatus.WORKING) {
            return;
        }
        boxRuntime.lastStatusLabel = label;
        farmer.setWorkStatus(CitizenWorkStatus.WORKING);
        farmer.setStatusLabel(label);
        farmer.setWorkNeedDetail("farm:" + boxRuntime.hashCode());
        CitizenService.save(level, farmer.uuid());
    }

    private static boolean isTillable(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.ROOTED_DIRT);
    }

    private static LevelRuntime runtime(ServerLevel level) {
        return RUNTIMES.computeIfAbsent(runtimeKey(level), ignored -> new LevelRuntime());
    }

    private static String runtimeKey(ServerLevel level) {
        return SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT);
    }

    private static final class LevelRuntime {
        private final ConcurrentMap<BlockPos, BoxRuntime> boxes = new ConcurrentHashMap<>();
    }

    private static final class BoxRuntime {
        private int cursor;
        private long nextActionTick;
        private String lastStatusLabel = "";
    }
}
