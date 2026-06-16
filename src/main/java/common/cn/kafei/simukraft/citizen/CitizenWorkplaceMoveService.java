package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.building.BuilderConstructionService;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class CitizenWorkplaceMoveService {
    private static final double ARRIVED_DISTANCE_SQR = 4.0D;
    private static final int[] WORKPLACE_Y_OFFSETS = {0, 1, -1};
    private static final int[][] WORKPLACE_STAND_OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2}
    };
    private static final int[][] CONTROL_BOX_STAND_OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private CitizenWorkplaceMoveService() {
    }

    private record CachedTarget(UUID workplaceId, BlockPos workplacePos, Vec3 target) {
        boolean matches(CitizenData citizen) {
            return Objects.equals(workplaceId, citizen.workplaceId())
                    && Objects.equals(workplacePos, citizen.workplacePos());
        }
    }

    // 工作地点落点缓存，按存档+维度分区，避免换存档时混用。
    private static final ConcurrentMap<String, ConcurrentMap<UUID, CachedTarget>> WORKPLACE_TARGETS_BY_LEVEL = new ConcurrentHashMap<>();

    public static void clearServerCaches(MinecraftServer server) {
        String prefix = common.cn.kafei.simukraft.util.SaveScopedCacheKey.serverKey(server) + "|";
        WORKPLACE_TARGETS_BY_LEVEL.keySet().removeIf(key -> key.startsWith(prefix));
    }

    // returnToWorkplace：让市民恢复上班时优先走寻路，失败或距离过远时由传送兜底。
    public static boolean returnToWorkplace(ServerLevel level, CitizenData citizen) {
        if (level == null || citizen == null || citizen.dead() || citizen.workplaceId() == null || citizen.jobType() == CityJobType.UNEMPLOYED) {
            return false;
        }
        if (CitizenSelfFeedingService.isSelfFeeding(level, citizen.uuid())) {
            return false;
        }
        Optional<Vec3> targetOptional = resolveWorkplaceTarget(level, citizen);
        if (targetOptional.isEmpty()) {
            return false;
        }
        Vec3 target = targetOptional.get();
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, citizen.uuid());
        if (entity != null && entity.position().distanceToSqr(target) <= ARRIVED_DISTANCE_SQR) {
            return true;
        }
        CitizenNavigationService.stop(level, citizen.uuid());
        if (entity != null && CitizenNavigationService.requestMove(level, citizen.uuid(), target, MovementIntent.WORK)) {
            return true;
        }
        return CitizenTeleportService.teleportOrSpawnCitizen(level, citizen, target);
    }

    // resolveWorkplaceTarget：解析普通城市 POI 或建筑师当前施工控制盒的岗位落点。
    public static Optional<Vec3> resolveWorkplaceTarget(ServerLevel level, CitizenData citizen) {
        if (level == null || citizen == null || citizen.workplaceId() == null && citizen.workplacePos() == null
                && citizen.jobType() != CityJobType.BUILDER) {
            return Optional.empty();
        }
        // 建造师目标随施工进度频繁变化，跳过缓存。
        if (citizen.jobType() != CityJobType.BUILDER) {
            String levelKey = common.cn.kafei.simukraft.util.SaveScopedCacheKey.levelKey(level);
            ConcurrentMap<UUID, CachedTarget> cache = WORKPLACE_TARGETS_BY_LEVEL.computeIfAbsent(levelKey, k -> new ConcurrentHashMap<>());
            CachedTarget cached = cache.get(citizen.uuid());
            if (cached != null && cached.matches(citizen)) {
                return Optional.of(cached.target());
            }
            Optional<Vec3> result = computeWorkplaceTarget(level, citizen);
            result.ifPresent(vec -> cache.put(citizen.uuid(), new CachedTarget(citizen.workplaceId(), citizen.workplacePos(), vec)));
            return result;
        }
        return computeWorkplaceTarget(level, citizen);
    }

    private static Optional<Vec3> computeWorkplaceTarget(ServerLevel level, CitizenData citizen) {
        if (citizen.workplaceId() == null && citizen.workplacePos() == null && citizen.jobType() != CityJobType.BUILDER) {
            return Optional.empty();
        }
        CityPoiData poi = CityPoiManager.get(level).getPoi(citizen.workplaceId());
        if (poi != null) {
            return poi.active() && (citizen.cityId() == null || citizen.cityId().equals(poi.cityId()))
                    ? targetNearWorkplace(level, poi.pos())
                    : Optional.empty();
        }
        if (citizen.jobType() == CityJobType.BUILDER) {
            BlockPos buildBoxPos = BuilderConstructionService.findBuildBoxPos(level, citizen.uuid());
            if (buildBoxPos != null) {
                return targetNearWorkplace(level, buildBoxPos);
            }
        }
        if (citizen.jobType() == CityJobType.COMMERCIAL_WORKER && citizen.workplacePos() != null) {
            return targetAdjacentToWorkplace(level, citizen.workplacePos());
        }
        if (citizen.workplacePos() != null) {
            return targetNearWorkplace(level, citizen.workplacePos());
        }
        return Optional.empty();
    }

    // targetAdjacentToWorkplace：商业控制箱岗位只允许紧邻四格落点，避免职员隔墙站到建筑外侧。
    public static Optional<Vec3> targetAdjacentToWorkplace(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return Optional.empty();
        }
        for (int yOffset : WORKPLACE_Y_OFFSETS) {
            for (int[] offset : CONTROL_BOX_STAND_OFFSETS) {
                Vec3 landing = safeLanding(level, pos.offset(offset[0], yOffset, offset[1]));
                if (landing != null) {
                    return Optional.of(landing);
                }
            }
        }
        Vec3 topLanding = safeLanding(level, pos.above());
        return Optional.ofNullable(topLanding);
    }

    // targetNearWorkplace：优先选择岗位方块周围的安全脚底点，避免把 NPC 放到工作盒顶上或旧路径外侧。
    public static Optional<Vec3> targetNearWorkplace(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return Optional.empty();
        }
        for (int yOffset : WORKPLACE_Y_OFFSETS) {
            for (int[] offset : WORKPLACE_STAND_OFFSETS) {
                Vec3 landing = safeLanding(level, pos.offset(offset[0], yOffset, offset[1]));
                if (landing != null) {
                    return Optional.of(landing);
                }
            }
        }
        Vec3 topLanding = safeLanding(level, pos.above());
        return Optional.of(topLanding != null ? topLanding : Vec3.atBottomCenterOf(pos.above()));
    }

    private static Vec3 safeLanding(ServerLevel level, BlockPos feet) {
        return level.isLoaded(feet) ? CitizenTeleportService.safeLandingTarget(level, feet) : null;
    }
}
