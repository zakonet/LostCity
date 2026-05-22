package client.cn.kafei.simukraft.client.buildbox;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public record PreviewBlockData(BlockPos pos, BlockState state, int packedLight) {
}
