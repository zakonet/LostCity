package common.cn.kafei.simukraft.network.building.controlbox;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.controlbox.ResidentialControlBoxView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public record ResidentialControlBoxOpenResponsePacket(BlockPos controlBoxPos,
                                                      String buildingName,
                                                      String buildingTypeKey,
                                                      int residentCount,
                                                      int capacity,
                                                      List<ResidentEntry> residents,
                                                      boolean hasBuildingBounds,
                                                      BlockPos boundsMin,
                                                      BlockPos boundsMax,
                                                      List<BlockPos> residentialPoiPositions) implements CustomPacketPayload {
    public static final Type<ResidentialControlBoxOpenResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "residential_control_box_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResidentialControlBoxOpenResponsePacket> STREAM_CODEC = StreamCodec.of(ResidentialControlBoxOpenResponsePacket::encode, ResidentialControlBoxOpenResponsePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ResidentialControlBoxOpenResponsePacket from(ResidentialControlBoxView view) {
        return new ResidentialControlBoxOpenResponsePacket(
                view.controlBoxPos(),
                view.buildingName(),
                view.buildingTypeKey(),
                view.residentCount(),
                view.capacity(),
                view.residents().stream().map(entry -> new ResidentEntry(entry.citizenId(), entry.name())).toList(),
                view.hasBuildingBounds(),
                view.boundsMin(),
                view.boundsMax(),
                view.residentialPoiPositions()
        );
    }

    public static ResidentialControlBoxOpenResponsePacket empty(BlockPos pos) {
        return new ResidentialControlBoxOpenResponsePacket(
                pos,
                "gui.residential_control_box.unknown_building",
                "gui.residential_control_box.building_type",
                0,
                0,
                List.of(),
                false,
                BlockPos.ZERO,
                BlockPos.ZERO,
                List.of()
        );
    }

    public static void encode(RegistryFriendlyByteBuf buffer, ResidentialControlBoxOpenResponsePacket packet) {
        buffer.writeBlockPos(packet.controlBoxPos());
        buffer.writeUtf(packet.buildingName(), 128);
        buffer.writeUtf(packet.buildingTypeKey(), 96);
        buffer.writeVarInt(packet.residentCount());
        buffer.writeVarInt(packet.capacity());
        buffer.writeVarInt(packet.residents().size());
        packet.residents().forEach(resident -> {
            buffer.writeUUID(resident.citizenId());
            buffer.writeUtf(resident.name(), 64);
        });
        buffer.writeBoolean(packet.hasBuildingBounds());
        buffer.writeBlockPos(packet.boundsMin());
        buffer.writeBlockPos(packet.boundsMax());
        buffer.writeVarInt(packet.residentialPoiPositions().size());
        packet.residentialPoiPositions().forEach(buffer::writeBlockPos);
    }

    public static ResidentialControlBoxOpenResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos controlBoxPos = buffer.readBlockPos();
        String buildingName = buffer.readUtf(128);
        String buildingTypeKey = buffer.readUtf(96);
        int residentCount = buffer.readVarInt();
        int capacity = buffer.readVarInt();
        int residentSize = buffer.readVarInt();
        List<ResidentEntry> residents = new ArrayList<>(residentSize);
        for (int index = 0; index < residentSize; index++) {
            residents.add(new ResidentEntry(buffer.readUUID(), buffer.readUtf(64)));
        }
        boolean hasBuildingBounds = buffer.readBoolean();
        BlockPos boundsMin = buffer.readBlockPos();
        BlockPos boundsMax = buffer.readBlockPos();
        int poiSize = buffer.readVarInt();
        List<BlockPos> residentialPoiPositions = new ArrayList<>(poiSize);
        for (int index = 0; index < poiSize; index++) {
            residentialPoiPositions.add(buffer.readBlockPos());
        }
        return new ResidentialControlBoxOpenResponsePacket(
                controlBoxPos,
                buildingName,
                buildingTypeKey,
                residentCount,
                capacity,
                List.copyOf(residents),
                hasBuildingBounds,
                boundsMin,
                boundsMax,
                List.copyOf(residentialPoiPositions)
        );
    }

    public static void handle(ResidentialControlBoxOpenResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> client.cn.kafei.simukraft.client.controlbox.ResidentialControlBoxScreenOpener.open(packet));
    }

    public record ResidentEntry(UUID citizenId, String name) {
    }
}
