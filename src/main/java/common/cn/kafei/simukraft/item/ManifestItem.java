package common.cn.kafei.simukraft.item;

import common.cn.kafei.simukraft.building.BuildingStructure;
import common.cn.kafei.simukraft.building.BuildingStructureService;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("null")
public final class ManifestItem extends Item {
    private static final String TAG_MATERIALS = "Materials";
    private static final String TAG_CHECKED = "Checked";
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
        Map<String, Integer> materials = countMaterials(structure);
        if (materials.isEmpty()) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.manifest.no_building"));
            return;
        }

        CompoundTag tag = customTag(stack);
        tag.putString(TAG_BUILDING_NAME, structure.displayName());
        tag.putString(TAG_SOURCE_TYPE, "build");
        tag.putLong(TAG_BUILD_BOX_POS, buildBoxPos.asLong());
        tag.putInt(TAG_PROGRESS_CURRENT, Math.max(0, task.currentBlockIndex()));
        tag.putInt(TAG_PROGRESS_TOTAL, Math.max(0, task.totalBlocks()));
        writeMaterials(tag, materials);
        applyCustomTag(stack, tag);
        InfoToastService.success(player, Component.translatable("message.simukraft.manifest.filled"));
    }

    private static Map<String, Integer> countMaterials(BuildingStructure structure) {
        Map<String, Integer> materials = new LinkedHashMap<>();
        for (var block : structure.blocks()) {
            if (block == null || block.state() == null || block.state().isAir()) {
                continue;
            }
            Item item = block.state().getBlock().asItem();
            if (item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
            materials.merge(itemId, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(materials.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        Map<String, Integer> sorted = new LinkedHashMap<>();
        entries.forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    private static void writeMaterials(@Nonnull CompoundTag tag, @Nonnull Map<String, Integer> materials) {
        ListTag materialsList = new ListTag();
        ListTag checkedList = new ListTag();
        materials.forEach((itemId, count) -> {
            CompoundTag materialTag = new CompoundTag();
            materialTag.putString("Item", itemId);
            materialTag.putInt("Count", count);
            materialsList.add(materialTag);
            checkedList.add(StringTag.valueOf("false"));
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
            result.add(new MaterialEntry(itemId, count, checked, i));
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

    public record MaterialEntry(@Nonnull String itemId, int count, boolean checked, int index) {
    }
}
