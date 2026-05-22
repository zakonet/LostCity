package common.cn.kafei.simukraft.economy;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import net.minecraft.server.level.ServerLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public final class EconomyService {
    private EconomyService() {
    }

    public static double getCityBalance(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) {
            return 0.0D;
        }
        return CityManager.get(level).getCity(cityId).map(CityData::funds).orElse(0.0D);
    }

    public static boolean canAfford(ServerLevel level, UUID cityId, double amount) {
        return getCityBalance(level, cityId) >= normalizeAmount(amount);
    }

    private static double normalizeAmount(double amount) {
        if (!Double.isFinite(amount)) {
            return 0.0D;
        }
        return BigDecimal.valueOf(Math.max(0.0D, amount)).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
