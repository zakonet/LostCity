package common.cn.kafei.simukraft.registry;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.stream.Stream;

@SuppressWarnings("null")
public final class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SimuKraft.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SIMUKRAFT_TAB = CREATIVE_MODE_TABS.register("simukraft_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.simukraft"))
            .icon(() -> new ItemStack(ModBlocks.BUILD_BOX.get()))
            .displayItems((parameters, output) -> Stream.concat(ModBlocks.ITEMS.getEntries().stream(), ModItems.ITEMS.getEntries().stream())
                    .map(DeferredHolder::get)
                    .forEach(output::accept))
            .build());

    private ModCreativeModeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
