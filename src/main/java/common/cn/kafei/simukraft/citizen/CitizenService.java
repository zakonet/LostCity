package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CitizenService {
    private CitizenService() {
    }

    public static CitizenData ensureCitizen(ServerLevel level, CitizenEntity entity) {
        if (level == null || entity == null) {
            return null;
        }
        return CitizenManager.get(level).getOrCreate(entity);
    }

    public static void syncEntity(ServerLevel level, CitizenEntity entity) {
        if (level == null || entity == null) {
            return;
        }
        CitizenManager.get(level).syncEntity(entity);
    }

    public static void updateProfile(ServerLevel level, UUID citizenId, String name, String statusLabel, String skinPath) {
        if (level == null || citizenId == null) {
            return;
        }
        CitizenManager manager = CitizenManager.get(level);
        manager.getCitizen(citizenId).ifPresent(data -> {
            data.setName(name);
            data.setStatusLabel(statusLabel);
            data.setSkinPath(skinPath);
            manager.saveCitizenNow(citizenId);
        });
    }

    public static void setCity(ServerLevel level, UUID citizenId, UUID cityId) {
        if (level == null || citizenId == null) {
            return;
        }
        CitizenManager manager = CitizenManager.get(level);
        manager.getCitizen(citizenId).ifPresent(data -> {
            data.setCityId(cityId);
            manager.saveCitizenNow(citizenId);
        });
    }

    public static void setHome(ServerLevel level, UUID citizenId, UUID homeId) {
        if (level == null || citizenId == null) {
            return;
        }
        CitizenManager manager = CitizenManager.get(level);
        manager.getCitizen(citizenId).ifPresent(data -> {
            data.setHomeId(homeId);
            manager.saveCitizenNow(citizenId);
        });
    }

    public static void setWorkplace(ServerLevel level, UUID citizenId, UUID workplaceId) {
        if (level == null || citizenId == null) {
            return;
        }
        CitizenManager manager = CitizenManager.get(level);
        manager.getCitizen(citizenId).ifPresent(data -> {
            data.setWorkplaceId(workplaceId);
            manager.saveCitizenNow(citizenId);
        });
    }

    public static void save(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return;
        }
        CitizenManager.get(level).saveCitizenNow(citizenId);
    }

    public static void applyEmployment(ServerLevel level, UUID citizenId, CityJobType jobType, UUID workplaceId, String statusLabel) {
        if (level == null || citizenId == null || workplaceId == null) {
            return;
        }
        CitizenManager manager = CitizenManager.get(level);
        manager.getCitizen(citizenId).ifPresent(data -> {
            data.setJobType(jobType != null ? jobType : CityJobType.OTHER);
            data.setWorkStatus(CitizenWorkStatus.WORKING);
            data.setWorkplaceId(workplaceId);
            data.setStatusLabel(statusLabel != null ? statusLabel : "");
            manager.saveCitizenNow(citizenId);
        });
    }

    public static void clearEmployment(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return;
        }
        CitizenManager manager = CitizenManager.get(level);
        manager.getCitizen(citizenId).ifPresent(data -> {
            data.setJobType(CityJobType.UNEMPLOYED);
            data.setWorkStatus(CitizenWorkStatus.IDLE);
            data.setWorkplaceId(null);
            data.setStatusLabel("");
            data.setWorkNeedDetail("");
            manager.saveCitizenNow(citizenId);
        });
    }

    public static Optional<CitizenData> findCitizen(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return Optional.empty();
        }
        return CitizenManager.get(level).getCitizen(citizenId);
    }

    public static boolean isHireable(CitizenData data) {
        return data != null
                && !data.child()
                && data.jobType() == CityJobType.UNEMPLOYED
                && data.workplaceId() == null;
    }

    public static UUID findAssignedCitizen(ServerLevel level, UUID workplaceId) {
        if (level == null || workplaceId == null) {
            return null;
        }
        return CitizenManager.get(level).allCitizens().stream()
                .filter(data -> workplaceId.equals(data.workplaceId()))
                .map(CitizenData::uuid)
                .findFirst()
                .orElse(null);
    }

    public static List<CitizenData> listHireableCitizens(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        return CitizenManager.get(level).allCitizens().stream()
                .filter(CitizenService::isHireable)
                .sorted(Comparator.comparing(CitizenData::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public static List<CitizenData> listCitizensByCity(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) {
            return List.of();
        }
        return CitizenManager.get(level).allCitizens().stream()
                .filter(data -> cityId.equals(data.cityId()))
                .toList();
    }

    public static boolean belongsToCity(CitizenData data, UUID cityId) {
        return data != null && cityId != null && cityId.equals(data.cityId());
    }

    public static boolean canAddCitizen(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) {
            return false;
        }
        return CitizenHousingService.vacantHomeCount(level, cityId) > 0;
    }

    public static Optional<CitizenEntity> spawnCitizen(ServerLevel level, BlockPos pos, UUID cityId) {
        return spawnCitizen(level, pos, cityId, false);
    }

    public static Optional<CitizenEntity> spawnCitizen(ServerLevel level, BlockPos pos, UUID cityId, boolean ignoreHousingCapacity) {
        if (level == null || pos == null || cityId == null) {
            return Optional.empty();
        }
        if (!ignoreHousingCapacity && !canAddCitizen(level, cityId)) {
            return Optional.empty();
        }
        CitizenEntity entity = ModEntities.CITIZEN.get().create(level);
        if (entity == null) {
            return Optional.empty();
        }
        entity.moveTo(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        entity.setPersistenceRequired();
        CitizenData data = ensureCitizen(level, entity);
        if (data != null) {
            data.setCityId(cityId);
            CitizenManager manager = CitizenManager.get(level);
            manager.saveCitizenNow(data.uuid());
            manager.syncEntity(entity);
            manager.markChanged();
        }
        level.addFreshEntity(entity);
        return Optional.of(entity);
    }
}
