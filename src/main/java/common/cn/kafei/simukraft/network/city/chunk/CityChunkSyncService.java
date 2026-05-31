package common.cn.kafei.simukraft.network.city.chunk;

import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CityChunkSyncService {
    private CityChunkSyncService() {
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, buildPacket(level, player.getUUID()));
    }

    public static void syncToAll(ServerLevel level) {
        if (level == null || level.getServer() == null) {
            return;
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(level.dimension())) {
                PacketDistributor.sendToPlayer(player, buildPacket(level, player.getUUID()));
            }
        }
    }

    private static CityChunkSyncPacket buildPacket(ServerLevel level, UUID playerId) {
        CityManager cityManager = CityManager.get(level);
        CityChunkManager chunkManager = CityChunkManager.get(level);
        UUID currentCityId = cityManager.getPlayerCity(playerId).map(CityData::cityId).orElse(null);
        Map<UUID, Set<Long>> chunks = new ConcurrentHashMap<>();
        Map<UUID, CityChunkSyncPacket.CityCoreEntry> cores = new ConcurrentHashMap<>();
        for (CityData city : cityManager.allCities()) {
            chunks.put(city.cityId(), Set.copyOf(chunkManager.getCityChunks(city.cityId())));
            cores.put(city.cityId(), new CityChunkSyncPacket.CityCoreEntry(city.cityCorePos(), city.cityName()));
        }
        return new CityChunkSyncPacket(currentCityId, Map.copyOf(chunks), Map.copyOf(cores));
    }
}
