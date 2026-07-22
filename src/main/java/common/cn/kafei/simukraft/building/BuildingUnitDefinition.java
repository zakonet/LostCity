package common.cn.kafei.simukraft.building;

import net.minecraft.core.BlockPos;

import java.util.List;

public record BuildingUnitDefinition(String label, BlockPos min, BlockPos max, List<BlockPos> points) {

    // 范围模式（无显式点列表）
    public BuildingUnitDefinition(String label, BlockPos min, BlockPos max) {
        this(label, min, max, List.of());
    }

    // 点模式（显式坐标列表）
    public BuildingUnitDefinition(String label, List<BlockPos> points) {
        this(label, BlockPos.ZERO, BlockPos.ZERO, List.copyOf(points));
    }

    public boolean contains(BlockPos relativePos) {
        if (!points.isEmpty()) {
            return points.contains(relativePos.immutable());
        }
        return relativePos.getX() >= min.getX() && relativePos.getX() <= max.getX()
                && relativePos.getY() >= min.getY() && relativePos.getY() <= max.getY()
                && relativePos.getZ() >= min.getZ() && relativePos.getZ() <= max.getZ();
    }
}
