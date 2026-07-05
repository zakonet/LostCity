package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.citizen.family.FamilyData;
import common.cn.kafei.simukraft.citizen.family.FamilyManager;
import common.cn.kafei.simukraft.city.group.CityGroupMessageService;
import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.*;

public final class NpcMarriageService {
    private NpcMarriageService() {
    }

    public static void tickMarriages(ServerLevel level, RandomSource random, long currentDay) {
        CitizenManager manager = CitizenManager.get(level);
        FamilyManager familyManager = FamilyManager.get(level);
        double chance = ServerConfig.familyMarriageChancePerDay();

        // 按城市分组，在同一城市内配对
        Map<UUID, List<CitizenData>> malesByCityId = new HashMap<>();
        Map<UUID, List<CitizenData>> femalesByCityId = new HashMap<>();

        for (CitizenData data : manager.allCitizens()) {
            if (data.dead() || data.child() || data.cityId() == null) continue;
            if (data.familyId() != null) continue; // 已有家庭
            if ("female".equals(data.gender())) {
                femalesByCityId.computeIfAbsent(data.cityId(), k -> new ArrayList<>()).add(data);
            } else {
                malesByCityId.computeIfAbsent(data.cityId(), k -> new ArrayList<>()).add(data);
            }
        }

        Set<UUID> married = new HashSet<>();
        for (UUID cityId : malesByCityId.keySet()) {
            List<CitizenData> males = malesByCityId.get(cityId);
            List<CitizenData> females = femalesByCityId.getOrDefault(cityId, List.of());
            if (females.isEmpty()) continue;

            for (CitizenData male : males) {
                if (married.contains(male.uuid())) continue;
                if (random.nextDouble() >= chance) continue;

                // Pick a random available female
                CitizenData female = pickRandom(females, married, random);
                if (female == null) continue;

                marry(level, manager, familyManager, male, female);
                married.add(male.uuid());
                married.add(female.uuid());
            }
        }
    }

    private static CitizenData pickRandom(List<CitizenData> candidates, Set<UUID> excluded, RandomSource random) {
        List<CitizenData> available = candidates.stream()
                .filter(c -> !excluded.contains(c.uuid()))
                .toList();
        if (available.isEmpty()) return null;
        return available.get(random.nextInt(available.size()));
    }

    private static void marry(ServerLevel level, CitizenManager manager,
            FamilyManager familyManager, CitizenData husband, CitizenData wife) {
        FamilyData family = familyManager.createFamily(level, husband.cityId(), husband.uuid(), wife.uuid());
        husband.setFamilyId(family.familyId());
        wife.setFamilyId(family.familyId());
        husband.setHappiness(Math.min(100.0, husband.happiness() + 10.0));
        wife.setHappiness(Math.min(100.0, wife.happiness() + 10.0));
        manager.saveCitizenNow(husband.uuid());
        manager.saveCitizenNow(wife.uuid());
        if (husband.cityId() != null) {
            CityGroupMessageService.successToCity(level, husband.cityId(),
                    Component.translatable("message.simukraft.citizen.married", husband.name(), wife.name()));
        }
        FamilyRelocationService.tryRelocate(level, family);
    }

}
