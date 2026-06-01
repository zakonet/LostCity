package common.cn.kafei.simukraft.industrial;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

@SuppressWarnings("null")
public record IndustrialItemStackSpec(String itemId, String potionId) {
    private static final String WATER_POTION = "minecraft:water";

    public static IndustrialItemStackSpec of(String itemId, String potionId) {
        return new IndustrialItemStackSpec(itemId != null ? itemId : "", potionId != null ? potionId : "");
    }

    public ItemStack stack(int count) {
        Item item = itemById(itemId);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        if (isWaterPotion()) {
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
        }
        return stack;
    }

    public boolean matches(ItemStack stack) {
        Item item = itemById(itemId);
        if (stack == null || stack.isEmpty() || stack.getItem() != item) {
            return false;
        }
        if (potionId == null || potionId.isBlank()) {
            return true;
        }
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return isWaterPotion() && contents.is(Potions.WATER);
    }

    private boolean isWaterPotion() {
        return WATER_POTION.equals(potionId);
    }

    private static Item itemById(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Items.AIR;
        }
        try {
            return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(Items.AIR);
        } catch (Exception exception) {
            return Items.AIR;
        }
    }
}
