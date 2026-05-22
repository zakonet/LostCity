package common.cn.kafei.simukraft.building.controlbox;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

public record ResidentialControlBoxView(BlockPos controlBoxPos,
                                        String buildingName,
                                        String buildingTypeKey,
                                        int residentCount,
                                        int capacity,
                                        List<ResidentEntry> residents,
                                        boolean hasBuildingBounds,
                                        BlockPos boundsMin,
                                        BlockPos boundsMax,
                                        List<BlockPos> residentialPoiPositions) {
    public record ResidentEntry(UUID citizenId, String name) {
    }
}
