package common.cn.kafei.simukraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

@SuppressWarnings("null")
public final class BuildingBlockPlacementService {
    private BuildingBlockPlacementService() {
    }

    /**
     * refreshedPlacementState：结构方块落地前按当前世界邻居刷新连接形状。
     */
    public static BlockState refreshedPlacementState(ServerLevel level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || state == null || state.isAir()) {
            return state;
        }
        return Block.updateFromNeighbourShapes(state, level, pos);
    }
}
