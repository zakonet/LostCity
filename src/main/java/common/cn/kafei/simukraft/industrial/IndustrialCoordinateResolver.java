package common.cn.kafei.simukraft.industrial;

import common.cn.kafei.simukraft.building.BuildingTransform;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("null")
public final class IndustrialCoordinateResolver {
    private IndustrialCoordinateResolver() {
    }

    public static List<BlockPos> resolvePositions(PlacedBuildingRecord building, List<BlockPos> structurePositions) {
        if (building == null || structurePositions == null || structurePositions.isEmpty()) {
            return List.of();
        }
        int rotation = rotationDegrees(building.facing());
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos structurePos : structurePositions) {
            if (structurePos == null) {
                continue;
            }
            BlockPos worldPos = building.worldOrigin().offset(BuildingTransform.rotatePosition(structurePos, rotation));
            if (insideBuilding(building, worldPos)) {
                positions.add(worldPos.immutable());
            }
        }
        return List.copyOf(positions);
    }

    public static BlockPos selectPoint(PlacedBuildingRecord building, IndustrialDefinition.PointDefinition point, Vec3 origin) {
        List<BlockPos> positions = resolvePositions(building, point != null ? point.positions() : List.of());
        if (positions.isEmpty()) {
            return null;
        }
        if (point.selectionMode() == IndustrialDefinition.SelectionMode.ORDERED || origin == null) {
            return positions.getFirst();
        }
        return positions.stream()
                .min(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(origin)))
                .orElse(positions.getFirst());
    }

    public static boolean insideBuilding(PlacedBuildingRecord building, BlockPos pos) {
        if (building == null || pos == null) {
            return false;
        }
        return pos.getX() >= Math.min(building.minPos().getX(), building.maxPos().getX())
                && pos.getX() <= Math.max(building.minPos().getX(), building.maxPos().getX())
                && pos.getY() >= Math.min(building.minPos().getY(), building.maxPos().getY())
                && pos.getY() <= Math.max(building.minPos().getY(), building.maxPos().getY())
                && pos.getZ() >= Math.min(building.minPos().getZ(), building.maxPos().getZ())
                && pos.getZ() <= Math.max(building.minPos().getZ(), building.maxPos().getZ());
    }

    private static int rotationDegrees(String facing) {
        String normalized = facing == null ? "" : facing.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "east" -> 90;
            case "south" -> 180;
            case "west" -> 270;
            default -> 0;
        };
    }
}
