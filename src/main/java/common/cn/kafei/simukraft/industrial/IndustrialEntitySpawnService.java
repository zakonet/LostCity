package common.cn.kafei.simukraft.industrial;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

import java.util.Optional;

@SuppressWarnings("null")
public final class IndustrialEntitySpawnService {
    private static final int SPAWN_ATTEMPTS = 24;

    private IndustrialEntitySpawnService() {
    }

    public static void ensureSpawned(ServerLevel level, IndustrialBoxManager manager, IndustrialBoxData data, PlacedBuildingRecord building, IndustrialDefinition definition) {
        if (level == null || manager == null || data == null || building == null || definition == null || definition.spawnEntity() == null || data.spawnEntityDone()) {
            return;
        }
        IndustrialDefinition.SpawnEntityDefinition spawn = definition.spawnEntity();
        if (!spawn.enabled() || spawn.entityType().isBlank() || spawn.count() <= 0) {
            return;
        }
        Optional<EntityType<?>> type = entityType(spawn.entityType());
        if (type.isEmpty()) {
            return;
        }
        if (!hasExistingSpawnedEntity(level, building, type.get())) {
            for (int index = 0; index < spawn.count(); index++) {
                spawnOne(level, data.boxPos(), building, type.get());
            }
        }
        data.setSpawnEntityDone(true);
        manager.persist(data);
    }

    private static void spawnOne(ServerLevel level, BlockPos boxPos, PlacedBuildingRecord building, EntityType<?> type) {
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            BlockPos pos = randomSpawnPos(level, boxPos);
            if (!inside(building, pos) || !isClear(level, pos)) {
                continue;
            }
            Entity entity = type.create(level);
            if (entity == null) {
                return;
            }
            entity.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            if (level.noCollision(entity.getBoundingBox())) {
                level.addFreshEntity(entity);
                return;
            }
        }
    }

    private static BlockPos randomSpawnPos(ServerLevel level, BlockPos boxPos) {
        int x = boxPos.getX() + level.random.nextInt(5) - 2;
        int z = boxPos.getZ() + level.random.nextInt(5) - 2;
        return new BlockPos(x, boxPos.getY() + 1, z);
    }

    private static boolean isClear(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos)
                && level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    private static boolean inside(PlacedBuildingRecord building, BlockPos pos) {
        return pos.getX() >= Math.min(building.minPos().getX(), building.maxPos().getX())
                && pos.getX() <= Math.max(building.minPos().getX(), building.maxPos().getX())
                && pos.getY() >= Math.min(building.minPos().getY(), building.maxPos().getY())
                && pos.getY() <= Math.max(building.minPos().getY(), building.maxPos().getY()) + 1
                && pos.getZ() >= Math.min(building.minPos().getZ(), building.maxPos().getZ())
                && pos.getZ() <= Math.max(building.minPos().getZ(), building.maxPos().getZ());
    }

    private static boolean hasExistingSpawnedEntity(ServerLevel level, PlacedBuildingRecord building, EntityType<?> type) {
        AABB bounds = buildingBounds(building).inflate(2.0D);
        return !level.getEntitiesOfClass(Entity.class, bounds, entity -> entity.getType() == type).isEmpty();
    }

    private static AABB buildingBounds(PlacedBuildingRecord building) {
        int minX = Math.min(building.minPos().getX(), building.maxPos().getX());
        int minY = Math.min(building.minPos().getY(), building.maxPos().getY());
        int minZ = Math.min(building.minPos().getZ(), building.maxPos().getZ());
        int maxX = Math.max(building.minPos().getX(), building.maxPos().getX()) + 1;
        int maxY = Math.max(building.minPos().getY(), building.maxPos().getY()) + 2;
        int maxZ = Math.max(building.minPos().getZ(), building.maxPos().getZ()) + 1;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Optional<EntityType<?>> entityType(String id) {
        try {
            return BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(id));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }
}
