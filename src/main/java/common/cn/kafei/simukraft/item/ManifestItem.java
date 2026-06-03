package common.cn.kafei.simukraft.item;

import common.cn.kafei.simukraft.building.BuildingStructure;
import common.cn.kafei.simukraft.building.BuildingStructureService;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("null")
public final class ManifestItem extends Item {
    private static final String TAG_MATERIALS = "Materials";
    private static final String TAG_CHECKED = "Checked";
    private static final String TAG_AVAILABLE = "Available";
    private static final String TAG_BUILDING_NAME = "BuildingName";
    private static final String TAG_BUILD_BOX_POS = "BuildBoxPos";
    private static final String TAG_SOURCE_TYPE = "SourceType";
    private static final String TAG_PROGRESS_CURRENT = "ProgressCurrent";
    private static final String TAG_PROGRESS_TOTAL = "ProgressTotal";

    public ManifestItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public @Nonnull InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            openManifestScreen(stack, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public @Nonnull InteractionResult useOn(@Nonnull net.minecraft.world.item.context.UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        BlockState state = level.getBlockState(context.getClickedPos());
        if (!isBuildBox(state)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            fillFromBuildBox(context.getItemInHand(), serverLevel, context.getClickedPos(), (ServerPlayer) player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context, @Nonnull List<Component> tooltipComponents, @Nonnull TooltipFlag isAdvanced) {
        super.appendHoverText(stack, context, tooltipComponents, isAdvanced);
        if (hasData(stack)) {
            tooltipComponents.add(Component.translatable("tooltip.simukraft.manifest.building", getBuildingName(stack)));
            tooltipComponents.add(Component.translatable("tooltip.simukraft.manifest.progress", getProgressCurrent(stack), getProgressTotal(stack)));
        } else {
            tooltipComponents.add(Component.translatable("tooltip.simukraft.manifest.empty"));
        }
    }

    private static boolean isBuildBox(BlockState state) {
        return state.is(ModBlocks.BUILD_BOX.get());
    }

    private static void fillFromBuildBox(ItemStack stack, ServerLevel level, BlockPos buildBoxPos, ServerPlayer player) {
        UUID buildTaskOwner = findBuildTaskOwner(level, buildBoxPos);
        if (buildTaskOwner == null) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.manifest.no_task"));
            return;
        }

        BuildingTaskData task = SimuSqliteStorage.loadBuildingTask(level, buildTaskOwner);
        if (task == null) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.manifest.no_task"));
            return;
        }

        Optional<BuildingStructure> structureOptional = BuildingStructureService.loadStructure(task.category(), task.buildingFileName());
        if (structureOptional.isEmpty()) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.manifest.no_building"));
            return;
        }

        BuildingStructure structure = structureOptional.get();
        List<BuildingBlockData> placedBlocks = sortedPlacedBlocks(structure, task);
        int currentIndex = clamp(task.currentBlockIndex(), 0, placedBlocks.size());
        Map<String, Integer> chestMaterials = countAdjacentContainerItems(level, buildBoxPos);
        ManifestMaterialAvailability.Snapshot materialSnapshot = ManifestMaterialAvailability.calculate(placedBlocks, currentIndex, chestMaterials);
        if (materialSnapshot.required().isEmpty()) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.manifest.no_building"));
            return;
        }

        CompoundTag tag = customTag(stack);
        tag.putString(TAG_BUILDING_NAME, structure.displayName());
        tag.putString(TAG_SOURCE_TYPE, "build");
        tag.putLong(TAG_BUILD_BOX_POS, buildBoxPos.asLong());
        tag.putInt(TAG_PROGRESS_CURRENT, currentIndex);
        tag.putInt(TAG_PROGRESS_TOTAL, Math.max(0, task.totalBlocks()));
        writeMaterials(tag, materialSnapshot.required(), materialSnapshot.available());
        applyCustomTag(stack, tag);
        InfoToastService.success(player, Component.translatable("message.simukraft.manifest.filled"));
    }

    private static List<BuildingBlockData> sortedPlacedBlocks(BuildingStructure structure, BuildingTaskData task) {
        return BuildingStructureService.resolvePlacedBlocks(structure, task.origin(), task.rotationDegrees()).stream()
                .sorted(Comparator.comparingInt((BuildingBlockData block) -> block.relativePos().getY())
                        .thenComparingInt(block -> block.relativePos().getX())
                        .thenComparingInt(block -> block.relativePos().getZ()))
                .toList();
    }

    private static Map<String, Integer> countAdjacentContainerItems(ServerLevel level, BlockPos buildBoxPos) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (BlockPos containerPos : adjacentContainers(level, buildBoxPos)) {
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, containerPos)) {
                ItemStack stack = snapshot.stack();
                if (stack.isEmpty() || stack.getItem() == Items.AIR) {
                    continue;
                }
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                items.merge(itemId, stack.getCount(), Integer::sum);
            }
        }
        return items;
    }

    private static Set<BlockPos> adjacentContainers(ServerLevel level, BlockPos buildBoxPos) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = buildBoxPos.relative(direction);
            if (!GenericContainerAccess.isContainer(level, adjacentPos)) {
                continue;
            }
            positions.add(GenericContainerAccess.canonicalContainerPos(level, adjacentPos));
        }
        return positions;
    }

    private static void writeMaterials(@Nonnull CompoundTag tag, @Nonnull Map<String, Integer> materials, @Nonnull Map<String, Integer> availableMaterials) {
        ListTag materialsList = new ListTag();
        ListTag checkedList = new ListTag();
        materials.forEach((itemId, count) -> {
            int available = Math.min(count, Math.max(0, availableMaterials.getOrDefault(itemId, 0)));
            CompoundTag materialTag = new CompoundTag();
            materialTag.putString("Item", itemId);
            materialTag.putInt("Count", count);
            materialTag.putInt(TAG_AVAILABLE, available);
            materialsList.add(materialTag);
            checkedList.add(StringTag.valueOf(Boolean.toString(available >= count)));
        });
        tag.put(TAG_MATERIALS, materialsList);
        tag.put(TAG_CHECKED, checkedList);
    }

    private static boolean hasData(ItemStack stack) {
        return !stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).isEmpty();
    }

    @Nonnull
    public static List<MaterialEntry> getMaterials(@Nonnull ItemStack stack) {
        List<MaterialEntry> result = new ArrayList<>();
        CompoundTag tag = customTag(stack);
        if (tag.isEmpty() || !tag.contains(TAG_MATERIALS)) {
            return result;
        }
        ListTag materialsList = tag.getList(TAG_MATERIALS, Tag.TAG_COMPOUND);
        ListTag checkedList = tag.getList(TAG_CHECKED, Tag.TAG_STRING);
        for (int i = 0; i < materialsList.size(); i++) {
            CompoundTag materialTag = materialsList.getCompound(i);
            String itemId = materialTag.getString("Item");
            int count = materialTag.getInt("Count");
            boolean checked = i < checkedList.size() && Boolean.parseBoolean(checkedList.getString(i));
            int available = materialTag.contains(TAG_AVAILABLE) ? materialTag.getInt(TAG_AVAILABLE) : (checked ? count : 0);
            result.add(new MaterialEntry(itemId, count, Math.min(count, Math.max(0, available)), checked, i));
        }
        return result;
    }

    @Nonnull
    public static String getBuildingName(@Nonnull ItemStack stack) {
        CompoundTag tag = customTag(stack);
        return tag.contains(TAG_BUILDING_NAME) ? tag.getString(TAG_BUILDING_NAME) : "";
    }

    public static int getProgressCurrent(@Nonnull ItemStack stack) {
        CompoundTag tag = customTag(stack);
        return tag.contains(TAG_PROGRESS_CURRENT) ? Math.max(0, tag.getInt(TAG_PROGRESS_CURRENT)) : 0;
    }

    public static int getProgressTotal(@Nonnull ItemStack stack) {
        CompoundTag tag = customTag(stack);
        return tag.contains(TAG_PROGRESS_TOTAL) ? Math.max(0, tag.getInt(TAG_PROGRESS_TOTAL)) : 0;
    }

    @Nonnull
    public static String getSourceType(@Nonnull ItemStack stack) {
        CompoundTag tag = customTag(stack);
        return tag.contains(TAG_SOURCE_TYPE) ? tag.getString(TAG_SOURCE_TYPE) : "";
    }

    public static int getTotalMaterials(@Nonnull ItemStack stack) {
        return getMaterials(stack).size();
    }

    public static int getCheckedCount(@Nonnull ItemStack stack) {
        int count = 0;
        for (MaterialEntry entry : getMaterials(stack)) {
            if (entry.checked()) {
                count++;
            }
        }
        return count;
    }

    public static void setChecked(@Nonnull ItemStack stack, int index, boolean checked) {
        CompoundTag tag = customTag(stack);
        if (tag.isEmpty() || !tag.contains(TAG_CHECKED)) {
            return;
        }
        ListTag checkedList = tag.getList(TAG_CHECKED, Tag.TAG_STRING);
        if (index < 0 || index >= checkedList.size()) {
            return;
        }
        checkedList.set(index, StringTag.valueOf(Boolean.toString(checked)));
        tag.put(TAG_CHECKED, checkedList);
        applyCustomTag(stack, tag);
    }

    private static UUID findBuildTaskOwner(ServerLevel level, BlockPos buildBoxPos) {
        return CitizenEmploymentService.findAssigned(level, "build_box", "builder", buildBoxPos)
                .map(citizen -> citizen.uuid())
                .orElse(null);
    }

    private void openManifestScreen(@Nonnull ItemStack stack, @Nonnull InteractionHand hand) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            client.cn.kafei.simukraft.client.manifest.ManifestScreen.open(stack, hand);
        }
    }

    private static CompoundTag customTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void applyCustomTag(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record MaterialEntry(@Nonnull String itemId, int count, int available, boolean checked, int index) {
    }
}
