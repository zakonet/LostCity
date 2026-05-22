package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.server.level.ServerLevel;

public final class PopulationGrowthService {
    private PopulationGrowthService() {
    }

    public static int tick(ServerLevel level) {
        if (level == null) {
            return 0;
        }
        int interval = ServerConfig.populationGrowthIntervalTicks();
        int maxPerInterval = ServerConfig.populationGrowthMaxPerInterval();
        if (interval <= 0 || maxPerInterval <= 0 || level.getGameTime() % interval != 0L) {
            return 0;
        }
        int spawned = 0;
        for (CityData city : CityManager.get(level).allCities()) {
            if (spawned >= maxPerInterval) {
                break;
            }
            CitizenHousingService.fillVacantHomes(level, city.cityId());
            spawned += CitizenHousingService.spawnCitizensForVacantHomes(level, city.cityId(), city.cityCorePos().above(), maxPerInterval - spawned);
        }
        return spawned;
    }
}
