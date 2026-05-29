package common.cn.kafei.simukraft.material;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

@SuppressWarnings("null")
public record WorkMaterialResult(boolean available, ItemStack requested) {
    public WorkMaterialResult {
        requested = requested != null ? requested.copy() : ItemStack.EMPTY;
    }

    public static WorkMaterialResult available(ItemStack requested) {
        return new WorkMaterialResult(true, requested);
    }

    public static WorkMaterialResult missing(ItemStack requested) {
        return new WorkMaterialResult(false, requested);
    }

    public Component displayName() {
        return requested.isEmpty() ? Component.literal("未知材料") : requested.getHoverName();
    }

    public String materialName() {
        if (requested.isEmpty()) {
            return "未知材料";
        }
        String translated = requested.getHoverName().getString();
        if (!translated.isBlank()) {
            return translated;
        }
        return BuiltInRegistries.ITEM.getKey(requested.getItem()).toString().toLowerCase(Locale.ROOT);
    }
}
