package common.cn.kafei.simukraft.building;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

public record BuildingTaskData(UUID taskId,
                               UUID citizenId,
                               UUID cityId,
                               String dimensionId,
                               BlockPos buildBoxPos,
                               String category,
                               String buildingFileName,
                               String displayName,
                               String amount,
                               String structureFileName,
                               BlockPos origin,
                               int rotationDegrees,
                               int currentBlockIndex,
                               int totalBlocks,
                               String status,
                               long createdAt,
                               long updatedAt,
                               List<BuildingPoiDefinition> poiDefinitions) {
}
