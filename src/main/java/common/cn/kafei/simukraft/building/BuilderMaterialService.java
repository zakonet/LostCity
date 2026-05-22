package common.cn.kafei.simukraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import common.cn.kafei.simukraft.config.ServerConfig;

import java.util.Locale;

@SuppressWarnings("null")
public final class BuilderMaterialService {
    private BuilderMaterialService() {
    }

    public static MaterialResult tryConsumeForBlock(ServerLevel level, BlockPos buildBoxPos, BlockState targetState) {
        if (level == null || buildBoxPos == null || targetState == null || targetState.isAir()) {
            return MaterialResult.available(ItemStack.EMPTY);
        }
        if (!BuilderMaterialPolicy.requiresMaterial(targetState)) {
            return MaterialResult.available(ItemStack.EMPTY);
        }
        Item requiredItem = targetState.getBlock().asItem();
        if (requiredItem == null || requiredItem == ItemStack.EMPTY.getItem()) {
            return MaterialResult.available(ItemStack.EMPTY);
        }
        ItemStack request = new ItemStack(requiredItem);
        if (request.isEmpty()) {
            return MaterialResult.available(ItemStack.EMPTY);
        }
        if (consumeFromAdjacentChests(level, buildBoxPos, requiredItem)) {
            return MaterialResult.available(request);
        }
        int searchRadius = ServerConfig.builderMaterialSearchRadius();
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos pos = buildBoxPos.offset(x, y, z);
                    if (isAdjacentFace(buildBoxPos, pos)) {
                        continue;
                    }
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (!(blockEntity instanceof Container container)) {
                        continue;
                    }
                    if (consumeFromContainer(container, requiredItem)) {
                        container.setChanged();
                        return MaterialResult.available(request);
                    }
                }
            }
        }
        return MaterialResult.missing(request);
    }

    private static boolean consumeFromAdjacentChests(ServerLevel level, BlockPos buildBoxPos, Item requiredItem) {
        for (Direction direction : Direction.values()) {
            BlockPos chestPos = buildBoxPos.relative(direction);
            Container container = resolveAdjacentChestContainer(level, chestPos);
            if (container == null) {
                continue;
            }
            if (consumeFromContainer(container, requiredItem)) {
                container.setChanged();
                return true;
            }
        }
        return false;
    }

    private static Container resolveAdjacentChestContainer(ServerLevel level, BlockPos chestPos) {
        BlockState state = level.getBlockState(chestPos);
        if (!(state.getBlock() instanceof ChestBlock chestBlock)) {
            return null;
        }
        return ChestBlock.getContainer(chestBlock, state, level, chestPos, true);
    }

    private static boolean isAdjacentFace(BlockPos center, BlockPos target) {
        for (Direction direction : Direction.values()) {
            if (center.relative(direction).equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean consumeFromContainer(Container container, Item item) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || stack.getItem() != item) {
                continue;
            }
            stack.shrink(1);
            if (stack.isEmpty()) {
                container.setItem(slot, ItemStack.EMPTY);
            } else {
                container.setItem(slot, stack);
            }
            return true;
        }
        return false;
    }

    public static String describe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "未知材料";
        }
        String translated = stack.getHoverName().getString();
        if (!translated.isBlank()) {
            return translated;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
    }

    public record MaterialResult(boolean available, ItemStack requested) {
        public static MaterialResult available(ItemStack requested) {
            return new MaterialResult(true, requested.copy());
        }

        public static MaterialResult missing(ItemStack requested) {
            return new MaterialResult(false, requested.copy());
        }

        public Component displayName() {
            return requested.isEmpty() ? Component.literal("未知材料") : requested.getHoverName();
        }
    }
}
