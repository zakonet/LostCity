package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.job.CityJobType;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

public final class CitizenLevelService {
    private static final int[] LEVEL_THRESHOLDS = {
            50, 150, 350, 650, 1150, 1850, 2850, 4050, 5550,
            7350, 9450, 11850, 14550, 17550, 20850, 24450, 28350, 32550, 37050
    };
    private static final String LEVEL_SUFFIX = ".level";
    private static final String XP_SUFFIX = ".xp";

    private CitizenLevelService() {
    }

    public static CitizenSkillSnapshot snapshot(CitizenData data, CityJobType skillType) {
        return snapshot(data, skillType, configuredMaxLevel());
    }

    public static CitizenSkillSnapshot snapshot(CitizenData data, CityJobType skillType, int maxLevel) {
        CityJobType normalizedType = normalizeSkillType(skillType);
        int normalizedMaxLevel = normalizeMaxLevel(maxLevel);
        if (data == null) {
            return new CitizenSkillSnapshot(normalizedType, 1, 0, normalizedMaxLevel);
        }
        int xp = Math.clamp(readSkillValue(data, xpKey(normalizedType), 0), 0, maxStoredXp(normalizedMaxLevel));
        int level = levelForXp(xp, normalizedMaxLevel);
        return new CitizenSkillSnapshot(normalizedType, level, xp, normalizedMaxLevel);
    }

    public static LevelUpdateResult addExperience(ServerLevel level, UUID citizenId, CityJobType skillType, int amount) {
        CityJobType normalizedType = normalizeSkillType(skillType);
        if (level == null || citizenId == null || amount <= 0) {
            return LevelUpdateResult.unchanged(new CitizenSkillSnapshot(normalizedType, 1, 0, configuredMaxLevel()));
        }
        CitizenManager manager = CitizenManager.get(level);
        Optional<CitizenData> optionalCitizen = manager.getCitizen(citizenId);
        if (optionalCitizen.isEmpty()) {
            return LevelUpdateResult.unchanged(new CitizenSkillSnapshot(normalizedType, 1, 0, configuredMaxLevel()));
        }
        CitizenData data = optionalCitizen.get();
        LevelUpdateResult result;
        synchronized (data) {
            CitizenSkillSnapshot before = snapshot(data, normalizedType);
            int cappedXp = (int) Math.min((long) before.xp() + amount, maxStoredXp(before.maxLevel()));
            int nextLevel = levelForXp(cappedXp, before.maxLevel());
            CitizenSkillSnapshot after = new CitizenSkillSnapshot(normalizedType, nextLevel, cappedXp, before.maxLevel());
            if (before.level() == after.level() && before.xp() == after.xp()) {
                return LevelUpdateResult.unchanged(before);
            }
            writeSkillValue(data, levelKey(normalizedType), after.level());
            writeSkillValue(data, xpKey(normalizedType), after.xp());
            result = new LevelUpdateResult(before, after);
        }
        manager.saveCitizenNow(citizenId);
        return result;
    }

    public static int xpForCurrentLevel(int level) {
        if (level <= 1) {
            return 0;
        }
        int index = Math.min(level - 2, LEVEL_THRESHOLDS.length - 1);
        return LEVEL_THRESHOLDS[index];
    }

    public static int xpForNextLevel(int level, int maxLevel) {
        int normalizedMaxLevel = normalizeMaxLevel(maxLevel);
        if (level >= normalizedMaxLevel) {
            return -1;
        }
        int index = level - 1;
        if (index < 0 || index >= LEVEL_THRESHOLDS.length) {
            return -1;
        }
        return LEVEL_THRESHOLDS[index];
    }

    public static int xpInCurrentLevel(CitizenSkillSnapshot snapshot) {
        if (snapshot == null || snapshot.maxLevelReached()) {
            return 0;
        }
        return Math.max(0, snapshot.xp() - xpForCurrentLevel(snapshot.level()));
    }

    public static int xpNeededForCurrentLevel(CitizenSkillSnapshot snapshot) {
        if (snapshot == null || snapshot.maxLevelReached()) {
            return 0;
        }
        int nextLevelXp = xpForNextLevel(snapshot.level(), snapshot.maxLevel());
        if (nextLevelXp < 0) {
            return 0;
        }
        return Math.max(0, nextLevelXp - xpForCurrentLevel(snapshot.level()));
    }

    public static float progress(CitizenSkillSnapshot snapshot) {
        if (snapshot == null || snapshot.maxLevelReached()) {
            return 1.0F;
        }
        int needed = xpNeededForCurrentLevel(snapshot);
        if (needed <= 0) {
            return 1.0F;
        }
        return Math.clamp(xpInCurrentLevel(snapshot) / (float) needed, 0.0F, 1.0F);
    }

    public static String skillKey(CityJobType skillType) {
        return normalizeSkillType(skillType).name().toLowerCase(Locale.ROOT);
    }

    private static int levelForXp(int xp, int maxLevel) {
        int normalizedMaxLevel = normalizeMaxLevel(maxLevel);
        int normalizedXp = Math.clamp(xp, 0, maxStoredXp(normalizedMaxLevel));
        int level = 1;
        while (level < normalizedMaxLevel
                && level - 1 < LEVEL_THRESHOLDS.length
                && normalizedXp >= LEVEL_THRESHOLDS[level - 1]) {
            level++;
        }
        return level;
    }

    private static int configuredMaxLevel() {
        return normalizeMaxLevel(ServerConfig.npcMaxLevel());
    }

    private static int normalizeMaxLevel(int maxLevel) {
        return Math.clamp(maxLevel, 1, LEVEL_THRESHOLDS.length + 1);
    }

    private static int maxStoredXp(int maxLevel) {
        int normalizedMaxLevel = normalizeMaxLevel(maxLevel);
        if (normalizedMaxLevel <= 1) {
            return 0;
        }
        return LEVEL_THRESHOLDS[normalizedMaxLevel - 2];
    }

    private static CityJobType normalizeSkillType(CityJobType skillType) {
        if (skillType == null || skillType == CityJobType.UNEMPLOYED) {
            return CityJobType.RESIDENT;
        }
        return skillType;
    }

    private static int readSkillValue(CitizenData data, String key, int fallback) {
        Integer value = data.skills().get(key);
        return value != null ? value : fallback;
    }

    private static void writeSkillValue(CitizenData data, String key, int value) {
        ConcurrentMap<String, Integer> skills = data.skills();
        skills.put(key, Math.max(0, value));
    }

    private static String levelKey(CityJobType skillType) {
        return skillKey(skillType) + LEVEL_SUFFIX;
    }

    private static String xpKey(CityJobType skillType) {
        return skillKey(skillType) + XP_SUFFIX;
    }

    public record LevelUpdateResult(CitizenSkillSnapshot before, CitizenSkillSnapshot after) {
        private static LevelUpdateResult unchanged(CitizenSkillSnapshot snapshot) {
            return new LevelUpdateResult(snapshot, snapshot);
        }

        public boolean changed() {
            return before.level() != after.level() || before.xp() != after.xp();
        }

        public boolean leveledUp() {
            return after.level() > before.level();
        }
    }
}
