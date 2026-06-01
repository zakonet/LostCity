package common.cn.kafei.simukraft.network.industrial;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.industrial.IndustrialControlBoxService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record IndustrialControlBoxActionPacket(BlockPos pos, Action action, String recipeId) implements CustomPacketPayload {
    public static final Type<IndustrialControlBoxActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "industrial_control_box_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IndustrialControlBoxActionPacket> STREAM_CODEC = StreamCodec.of(IndustrialControlBoxActionPacket::encode, IndustrialControlBoxActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, IndustrialControlBoxActionPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeEnum(packet.action());
        buffer.writeUtf(packet.recipeId(), 128);
    }

    public static IndustrialControlBoxActionPacket decode(RegistryFriendlyByteBuf buffer) {
        return new IndustrialControlBoxActionPacket(buffer.readBlockPos(), buffer.readEnum(Action.class), buffer.readUtf(128));
    }

    public static void handle(IndustrialControlBoxActionPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(packet.pos(), 16.0D)) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.build_box.too_far"));
                return;
            }
            switch (packet.action()) {
                case SELECT_RECIPE -> IndustrialControlBoxService.selectRecipe(level, packet.pos(), packet.recipeId());
                case TOGGLE_RUN -> IndustrialControlBoxService.toggleRunning(level, packet.pos());
                case FIRE -> IndustrialControlBoxService.fireWorker(level, packet.pos());
            }
            PacketDistributor.sendToPlayer(player, IndustrialControlBoxViewUpdatePacket.from(IndustrialControlBoxService.buildView(level, packet.pos())));
        }
    }

    public enum Action {
        SELECT_RECIPE,
        TOGGLE_RUN,
        FIRE
    }
}
