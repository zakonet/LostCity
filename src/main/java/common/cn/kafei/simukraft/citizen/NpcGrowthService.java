package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.citizen.family.FamilyManager;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.UUID;

public final class NpcGrowthService {
    private NpcGrowthService() {
    }

    public static void tickGrowth(ServerLevel level, RandomSource random, long currentDay) {
        CitizenManager manager = CitizenManager.get(level);
        FamilyManager familyManager = FamilyManager.get(level);

        for (CitizenData data : manager.allCitizens()) {
            if (data.dead()) continue;
            // 每游戏日 age+1
            data.setAge(data.age() + 1);

            if (data.child()) {
                // 孩子：18岁成年
                if (data.age() >= 18) {
                    graduate(level, manager, familyManager, data, random, currentDay);
                } else {
                    manager.saveCitizenNow(data.uuid());
                }
            } else {
                // 成年：超过寿命则自然死亡
                if (data.age() >= data.lifespan()) {
                    CitizenEntity entity =
                            CitizenTeleportService.findCitizenEntity(level, data.uuid());
                    if (entity != null) {
                        CitizenDeathService.handleDeath(level, entity);
                    } else {
                        // 实体不在线：直接标记死亡
                        manager.markCitizenDead(data.uuid(), currentDay);
                        if (data.familyId() != null) {
                            familyManager.handleMemberDeath(level, data.familyId(), data.uuid());
                        }
                    }
                } else {
                    manager.saveCitizenNow(data.uuid());
                }
            }
        }
    }

    private static void graduate(ServerLevel level, CitizenManager manager,
            FamilyManager familyManager, CitizenData data, RandomSource random, long currentDay) {
        data.setChild(false);
        CitizenProfileGenerator.promoteToAdult(data, random);

        UUID originFamilyId = data.originFamilyId();
        if (originFamilyId != null) {
            familyManager.leaveFamily(level, originFamilyId, data.uuid());
        }

        String gender = data.gender();
        UUID cityId = data.cityId();
        var newFamily = familyManager.createSingle(level, cityId, data.uuid(), gender);
        data.setFamilyId(newFamily.familyId());

        // Link ancestry
        if (originFamilyId != null && newFamily.generation() <= 10) {
            if ("female".equals(gender)) {
                newFamily.setMaternalFamilyId(originFamilyId);
            } else {
                newFamily.setPaternalFamilyId(originFamilyId);
            }
            common.cn.kafei.simukraft.storage.SimuSqliteStorage.saveFamily(level, newFamily);
        }

        manager.saveCitizenNow(data.uuid());
        CitizenHousingService.fillVacantHomes(level, cityId, 1);
    }
}
