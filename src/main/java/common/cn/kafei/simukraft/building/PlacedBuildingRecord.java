package common.cn.kafei.simukraft.building;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

public record PlacedBuildingRecord(UUID buildingId,
                                   UUID cityId,
                                   String dimensionId,
                                   String category,
                                   String buildingFileName,
                                   String displayName,
                                   String amount,
                                   String structureFileName,
                                   String facing,
                                   BlockPos worldOrigin,
                                   BlockPos structureAnchor,
                                   BlockPos minPos,
                                   BlockPos maxPos,
                                   long completedAt,
                                   List<BuildingBlockData> blocks,
                                   List<BuildingPoiDefinition> poiDefinitions,
                                   List<BuildingPoiInstance> poiInstances,
                                   List<BuildingUnitDefinition> unitDefinitions,
                                   List<BuildingUnitInstance> unitInstances) {
}
