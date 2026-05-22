package common.cn.kafei.simukraft.network.building.controlbox;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.PlacedBuildingDemolitionService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.controlbox.ResidentialControlBoxService;
import common.cn.kafei.simukraft.registry.ModBlocks;
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
public record ResidentialControlBoxDemolishPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ResidentialControlBoxDemolishPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "residential_control_box_demolish"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResidentialControlBoxDemolishPacket> STREAM_CODEC = StreamCodec.of(ResidentialControlBoxDemolishPacket::encode, ResidentialControlBoxDemolishPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, ResidentialControlBoxDemolishPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    public static ResidentialControlBoxDemolishPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ResidentialControlBoxDemolishPacket(buffer.readBlockPos());
    }

    public static void handle(ResidentialControlBoxDemolishPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            handleFor(level, player, packet.pos());
        }
    }

    private static void handleFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 8.0D)) {
            player.displayClientMessage(Component.translatable("message.simukraft.residential_control_box.too_far"), true);
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.RESIDENTIAL_CONTROL_BOX.get())) {
            player.displayClientMessage(Component.translatable("message.simukraft.residential_control_box.not_found"), true);
            return;
        }
        PlacedBuildingRecord building = ResidentialControlBoxService.findBuilding(level, pos);
        if (building == null) {
            player.displayClientMessage(Component.translatable("message.simukraft.residential_control_box.no_building"), true);
            return;
        }
        if (PlacedBuildingDemolitionService.demolish(level, building)) {
            player.displayClientMessage(Component.translatable("message.simukraft.residential_control_box.demolished"), true);
            PacketDistributor.sendToPlayer(player, ResidentialControlBoxOpenResponsePacket.empty(pos));
        }
    }
}
