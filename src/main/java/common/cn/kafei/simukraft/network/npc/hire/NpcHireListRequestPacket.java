package common.cn.kafei.simukraft.network.npc.hire;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public record NpcHireListRequestPacket(BlockPos sourcePos, String sourceType, String role) implements CustomPacketPayload {
    public static final Type<NpcHireListRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "npc_hire_list_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcHireListRequestPacket> STREAM_CODEC = StreamCodec.of(NpcHireListRequestPacket::encode, NpcHireListRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, NpcHireListRequestPacket packet) {
        buffer.writeBlockPos(packet.sourcePos());
        buffer.writeUtf(packet.sourceType(), 32);
        buffer.writeUtf(packet.role(), 32);
    }

    public static NpcHireListRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new NpcHireListRequestPacket(buffer.readBlockPos(), buffer.readUtf(32), buffer.readUtf(32));
    }

    public static void handle(NpcHireListRequestPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(packet.sourcePos(), 16.0D)) {
                player.displayClientMessage(Component.translatable("message.simukraft.build_box.too_far"), true);
                return;
            }
            UUID workplaceId = UUID.nameUUIDFromBytes((packet.sourceType() + ":" + packet.role() + "@" + packet.sourcePos().toShortString()).getBytes(StandardCharsets.UTF_8));
            UUID assignedCitizenId = CitizenService.findAssignedCitizen(level, workplaceId);
            List<NpcHireListResponsePacket.HireCandidate> candidates = CitizenService.listHireableCitizens(level).stream()
                    .map(data -> new NpcHireListResponsePacket.HireCandidate(
                            data.uuid(),
                            data.name(),
                            data.gender(),
                            data.age(),
                            data.health(),
                            data.hunger(),
                            data.skinPath(),
                            data.jobType().name(),
                            data.workStatus(),
                            1
                    ))
                    .toList();
            PacketDistributor.sendToPlayer(player, new NpcHireListResponsePacket(packet.sourcePos(), packet.sourceType(), packet.role(), assignedCitizenId, candidates));
        }
    }
}
