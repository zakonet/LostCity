package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.citizen.family.FamilyData;
import common.cn.kafei.simukraft.citizen.family.FamilyManager;
import common.cn.kafei.simukraft.citizen.family.FamilyStatus;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

@SuppressWarnings("null")
public final class NpcPregnancyService {
    private NpcPregnancyService() {
    }

    public static void tickPregnancies(ServerLevel level, RandomSource random, long currentDay) {
        CitizenManager manager = CitizenManager.get(level);
        FamilyManager familyManager = FamilyManager.get(level);
        double chance = ServerConfig.familyPregnancyChancePerDay();

        for (FamilyData family : familyManager.allFamilies()) {
            tryPregnancy(family, manager, familyManager, level, random, chance, currentDay);
        }
        // 已怀孕但仍在岗的女NPC自动解雇，防止永久空闲
        for (CitizenData data : manager.allCitizens()) {
            if (data.pregnant() && !data.dead() && data.jobType() != common.cn.kafei.simukraft.job.CityJobType.UNEMPLOYED) {
                common.cn.kafei.simukraft.job.CitizenEmploymentService.fire(level, data.uuid(), null, null, data.workplacePos(), "pregnant");
            }
        }
    }

    static void tickPregnanciesForCity(ServerLevel level, RandomSource random, long currentDay,
            java.util.UUID cityId) {
        CitizenManager manager = CitizenManager.get(level);
        FamilyManager familyManager = FamilyManager.get(level);
        double chance = ServerConfig.familyPregnancyChancePerDay();

        for (FamilyData family : familyManager.getCityFamilies(cityId)) {
            tryPregnancy(family, manager, familyManager, level, random, chance, currentDay);
        }
    }

    private static void tryPregnancy(FamilyData family, CitizenManager manager,
            FamilyManager familyManager, ServerLevel level,
            RandomSource random, double chance, long currentDay) {
        if (family.status() != FamilyStatus.ACTIVE) return;
        if (family.wifeId() == null) return;

        CitizenData wife = manager.getCitizen(family.wifeId()).orElse(null);
        if (wife == null || wife.dead() || wife.child() || wife.pregnant()) return;

        // 家庭当前成员数 + 孩子已有数，需要还有空余床位才允许怀孕
        if (!hasVacantBedForBaby(level, manager, family, wife)) return;

        if (random.nextDouble() >= chance) return;

        wife.setPregnant(true);
        wife.setPregnantSince(currentDay);
        wife.setStatusLabel("pregnant");
        manager.saveCitizenNow(wife.uuid());
    }

    private static boolean hasVacantBedForBaby(ServerLevel level, CitizenManager manager,
            FamilyData family, CitizenData wife) {
        if (wife.homeId() == null) return false;
        var building = PlacedBuildingService.findByPoi(level, wife.homeId());
        if (building == null) return false;
        var poiManager = CityPoiManager.get(level);
        java.util.Set<java.util.UUID> occupied = manager.allCitizens().stream()
                .filter(c -> !c.dead() && c.homeId() != null)
                .map(CitizenData::homeId)
                .collect(java.util.stream.Collectors.toSet());
        long vacantBeds = building.poiInstances().stream()
                .filter(inst -> inst.poiType() == CityPoiType.RESIDENTIAL)
                .map(inst -> poiManager.getPoiAt(inst.worldPos()))
                .filter(poi -> poi != null && poi.active() && !occupied.contains(poi.poiId()))
                .count();
        return vacantBeds > 0;
    }
}
