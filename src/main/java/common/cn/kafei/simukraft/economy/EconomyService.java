package common.cn.kafei.simukraft.economy;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.FinanceTransactionData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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

    public static boolean depositCityFunds(ServerLevel level, UUID cityId, ServerPlayer actor, double amount, String reason) {
        return depositCityFunds(level, cityId, actor, amount, reason, true);
    }

    public static boolean depositCityFunds(ServerLevel level, UUID cityId, ServerPlayer actor, double amount, String reason, boolean recordLedger) {
        double normalized = normalizeAmount(amount);
        if (level == null || cityId == null || normalized <= 0.0D) {
            return false;
        }
        if (!CityService.depositFunds(level, cityId, normalized)) {
            return false;
        }
        if (recordLedger) {
            FinanceLedgerService.record(level, cityId, actor, normalized, getCityBalance(level, cityId), FinanceTransactionData.Type.INCOME, reason);
        }
        return true;
    }

    public static double normalizeAmount(double amount) {
        if (!Double.isFinite(amount)) {
            return 0.0D;
        }
        return BigDecimal.valueOf(Math.max(0.0D, amount)).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
