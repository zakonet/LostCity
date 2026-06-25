package common.cn.kafei.simukraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

@SuppressWarnings("null")
public final class BuildingBlockPlacementService {
    private BuildingBlockPlacementService() {
    }

    /**
     * refreshedPlacementState：仅刷新安全连接方块，避免床/梯子等施工中间态缺少邻居时被判定为空气。
     */
    public static BlockState refreshedPlacementState(ServerLevel level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || state == null || state.isAir()) {
            return state;
        }
        if (!needsConnectionRefresh(state)) {
            return state;
        }

        BlockState refreshed = Block.updateFromNeighbourShapes(state, level, pos);
        if (refreshed == null || refreshed.isAir() || refreshed.getBlock() != state.getBlock()) {
            return state;
        }
        return refreshed;
    }

    /**
     * needsConnectionRefresh：筛选栅栏、墙、铁栏杆/玻璃板等连接方块。
     */
    private static boolean needsConnectionRefresh(BlockState state) {
        return state.is(BlockTags.FENCES)
                || state.is(BlockTags.WALLS)
                || state.getBlock() instanceof IronBarsBlock;
    }
}
