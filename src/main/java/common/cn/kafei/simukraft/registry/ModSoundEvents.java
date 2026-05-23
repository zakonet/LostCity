package common.cn.kafei.simukraft.registry;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("null")
public final class ModSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, SimuKraft.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_WAKE_UP = registerSound("player.wake_up"); // 清晨结算前播放鸡叫音效。
    public static final DeferredHolder<SoundEvent, SoundEvent> MONEY_COLLECT = registerSound("money.collect"); // 城市收入到账时播放收钱音效。

    private ModSoundEvents() {
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, name)));
    }
}
