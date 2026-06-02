package common.cn.kafei.simukraft.block;

import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

@SuppressWarnings("null")
public class MilkLiquidBlock extends LiquidBlock {
    private static final int COAGULATION_TICKS = 1200;

    public MilkLiquidBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    // 放置后注册凝固 tick：到点仍为牛奶时会变成奶酪块
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, COAGULATION_TICKS);
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        if (state.is(this) && state.getFluidState().isSource()) {
            level.scheduleTick(pos, this, COAGULATION_TICKS);
        }
    }

    // 服务端 tick 用于执行旧版牛奶凝固逻辑
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        if (state.is(this) && state.getFluidState().isSource()) {
            level.setBlockAndUpdate(pos, ModBlocks.CHEESE_BLOCK.get().defaultBlockState());
        }
    }
}
