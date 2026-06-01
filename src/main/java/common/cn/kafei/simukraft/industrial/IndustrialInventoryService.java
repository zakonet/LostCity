package common.cn.kafei.simukraft.industrial;

import common.cn.kafei.simukraft.material.GenericContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public final class IndustrialInventoryService {
    private IndustrialInventoryService() {
    }

    public static boolean hasInputs(ServerLevel level, List<BlockPos> containers, List<IndustrialDefinition.ItemRequirement> inputs) {
        for (IndustrialDefinition.ItemRequirement input : inputs) {
            if (countItem(level, containers, input) < input.count()) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasOutputSpace(ServerLevel level, List<BlockPos> containers, List<ItemStack> outputs) {
        List<ItemStack> remaining = outputs.stream().map(ItemStack::copy).toList();
        for (BlockPos container : containers) {
            if (!GenericContainerAccess.isContainer(level, container)) {
                continue;
            }
            for (ItemStack stack : remaining) {
                if (stack.isEmpty()) {
                    continue;
                }
                int insertable = GenericContainerAccess.countInsertable(level, container, stack);
                stack.shrink(insertable);
            }
        }
        return remaining.stream().allMatch(ItemStack::isEmpty);
    }

    public static boolean craftRecipe(ServerLevel level,
                                      List<BlockPos> inputContainers,
                                      List<BlockPos> outputContainers,
                                      IndustrialDefinition.RecipeDefinition recipe,
                                      double outputMultiplier,
                                      RandomSource random) {
        if (level == null || recipe == null || !hasInputs(level, inputContainers, recipe.inputs())) {
            return false;
        }
        List<ItemStack> outputs = buildOutputs(recipe, outputMultiplier, random);
        if (!hasOutputSpace(level, outputContainers, outputs)) {
            return false;
        }
        for (IndustrialDefinition.ItemRequirement input : recipe.inputs()) {
            if (!input.consume()) {
                continue;
            }
            if (!consumeItem(level, input, inputContainers, input.count())) {
                return false;
            }
        }
        return insertOutputs(level, outputContainers, outputs);
    }

    public static List<ItemStack> buildOutputs(IndustrialDefinition.RecipeDefinition recipe, double outputMultiplier, RandomSource random) {
        List<ItemStack> outputs = new ArrayList<>();
        RandomSource safeRandom = random != null ? random : RandomSource.create();
        double safeMultiplier = Math.max(0.0D, outputMultiplier);
        for (IndustrialDefinition.ProductOutput output : recipe.outputs()) {
            if (safeRandom.nextDouble() > output.probability()) {
                continue;
            }
            int randomBonus = output.randomRange() > 0 ? safeRandom.nextInt(output.randomRange()) : 0;
            int amount = output.baseAmount() + randomBonus;
            if (!output.ignoreMultiplier()) {
                amount = Math.max(1, (int) Math.floor(amount * safeMultiplier));
            }
            ItemStack stack = IndustrialItemStackSpec.of(output.itemId(), output.potionId()).stack(amount);
            if (!stack.isEmpty()) {
                outputs.add(stack);
            }
        }
        return List.copyOf(outputs);
    }

    public static ItemStack stackForItem(String itemId, int count) {
        return IndustrialItemStackSpec.of(itemId, "").stack(count);
    }

    public static ItemStack stackForItem(String itemId, String potionId, int count) {
        return IndustrialItemStackSpec.of(itemId, potionId).stack(count);
    }

    private static int countItem(ServerLevel level, List<BlockPos> containers, IndustrialDefinition.ItemRequirement input) {
        IndustrialItemStackSpec spec = IndustrialItemStackSpec.of(input.itemId(), input.potionId());
        int count = 0;
        for (BlockPos container : containers) {
            if (!GenericContainerAccess.isContainer(level, container)) {
                continue;
            }
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (spec.matches(snapshot.stack())) {
                    count += snapshot.stack().getCount();
                }
            }
        }
        return count;
    }

    private static boolean consumeItem(ServerLevel level, IndustrialDefinition.ItemRequirement input, List<BlockPos> containers, int count) {
        if (count <= 0) {
            return false;
        }
        IndustrialItemStackSpec spec = IndustrialItemStackSpec.of(input.itemId(), input.potionId());
        int remaining = count;
        for (BlockPos container : containers) {
            if (!GenericContainerAccess.isContainer(level, container)) {
                continue;
            }
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (!spec.matches(snapshot.stack())) {
                    continue;
                }
                int slotCount = Math.min(snapshot.stack().getCount(), remaining);
                for (int i = 0; i < slotCount; i++) {
                    if (!GenericContainerAccess.consumeSingleItemAtSlot(level, container, snapshot.slot(), snapshot.access(), snapshot.side(), spec::matches)) {
                        return false;
                    }
                    remaining--;
                    if (remaining <= 0) {
                        return true;
                    }
                }
            }
        }
        return remaining <= 0;
    }

    private static boolean insertOutputs(ServerLevel level, List<BlockPos> containers, List<ItemStack> outputs) {
        for (ItemStack output : outputs) {
            ItemStack remaining = output.copy();
            for (BlockPos container : containers) {
                if (remaining.isEmpty()) {
                    break;
                }
                if (GenericContainerAccess.isContainer(level, container)) {
                    remaining = GenericContainerAccess.insert(level, container, remaining);
                }
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

}
