package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.citizen.family.FamilyData;
import common.cn.kafei.simukraft.citizen.family.FamilyManager;
import common.cn.kafei.simukraft.citizen.family.FamilyStatus;
import common.cn.kafei.simukraft.city.group.CityGroupMessageService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("null")
public final class NpcChildbirthService {
    private NpcChildbirthService() {
    }

    public static void tickChildbirths(ServerLevel level, RandomSource random, long currentDay) {
        CitizenManager manager = CitizenManager.get(level);
        FamilyManager familyManager = FamilyManager.get(level);
        long duration = ServerConfig.familyPregnancyDurationDays();

        for (FamilyData family : familyManager.allFamilies()) {
            if (family.status() != FamilyStatus.ACTIVE) continue;
            if (family.wifeId() == null) continue;

            CitizenData wife = manager.getCitizen(family.wifeId()).orElse(null);
            if (wife == null || wife.dead() || !wife.pregnant()) continue;
            if (currentDay < wife.pregnantSince() + duration) continue;

            giveBirth(level, manager, familyManager, family, wife, random, currentDay);
        }
    }

    private static void giveBirth(ServerLevel level, CitizenManager manager,
            FamilyManager familyManager, FamilyData family,
            CitizenData wife, RandomSource random, long currentDay) {
        BlockPos spawnPos = resolveSpawnPos(level, wife);
        if (spawnPos == null) return;

        // 必须有空床才能出生，无空床保留孕期状态等下一天
        UUID vacantBedPoiId = findVacantBedInSameBuilding(level, wife);
        if (vacantBedPoiId == null) return;

        Optional<common.cn.kafei.simukraft.entity.CitizenEntity> entityOpt =
                CitizenService.spawnCitizen(level, spawnPos, wife.cityId(), true);
        if (entityOpt.isEmpty()) return;

        CitizenData child = CitizenManager.get(level).getOrCreate(entityOpt.get());
        if (child == null) return;

        String childGender = random.nextDouble() < 0.5D ? "male" : "female";
        child.setGender(childGender);

        CitizenData husband = family.husbandId() != null
                ? manager.getCitizen(family.husbandId()).orElse(null) : null;
        String childName = CitizenProfileGenerator.createChildName(
                husband != null ? husband.name() : "",
                wife.name(), childGender, random);
        child.setName(childName);

        child.setChild(true);
        child.setBornDay(currentDay);
        child.setAge(1);
        child.setHomeId(vacantBedPoiId);
        child.setFamilyId(family.familyId());
        child.setOriginFamilyId(family.familyId());
        child.setCityId(wife.cityId());
        CitizenProfileGenerator.fillChildProfile(child, random, currentDay);

        familyManager.addChild(level, family.familyId(), child.uuid());
        manager.saveCitizenNow(child.uuid());

        wife.setPregnant(false);
        wife.setPregnantSince(0L);
        wife.setStatusLabel("");
        manager.saveCitizenNow(wife.uuid());

        if (wife.cityId() != null) {
            CityGroupMessageService.successToCity(level, wife.cityId(),
                    Component.translatable("message.simukraft.citizen.born", child.name(), wife.name()));
        }
        FamilyRelocationService.tryRelocate(level, family);
    }

    private static BlockPos resolveSpawnPos(ServerLevel level, CitizenData wife) {
        UUID homeId = wife.homeId();
        if (homeId == null) return null;
        CityPoiData poi = CityPoiManager.get(level).getPoi(homeId);
        return poi != null ? poi.pos() : null;
    }

    private static UUID findVacantBedInSameBuilding(ServerLevel level, CitizenData wife) {
        UUID homeId = wife.homeId();
        if (homeId == null) return null;
        CityPoiManager poiManager = CityPoiManager.get(level);
        CityPoiData homePoi = poiManager.getPoi(homeId);
        if (homePoi == null) return null;

        PlacedBuildingRecord building = PlacedBuildingService.findByPoi(level, homeId);
        if (building == null) return null;

        java.util.Set<UUID> occupiedPoiIds = CitizenManager.get(level).allCitizens().stream()
                .filter(c -> !c.dead() && c.homeId() != null)
                .map(CitizenData::homeId)
                .collect(java.util.stream.Collectors.toSet());

        for (var instance : building.poiInstances()) {
            if (instance.poiType() != CityPoiType.RESIDENTIAL) continue;
            CityPoiData poi = poiManager.getPoiAt(instance.worldPos());
            if (poi == null || !poi.active()) continue;
            if (!occupiedPoiIds.contains(poi.poiId())) return poi.poiId();
        }
        return null;
    }
}
