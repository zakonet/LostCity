package common.cn.kafei.simukraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public record BuildingBlockData(BlockPos relativePos, BlockState state, BlockPos originalStructurePos) {
}
