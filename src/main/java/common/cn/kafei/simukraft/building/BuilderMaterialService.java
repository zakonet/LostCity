package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.material.NpcWorkMaterialService;
import common.cn.kafei.simukraft.material.WorkMaterialCache;
import common.cn.kafei.simukraft.material.WorkMaterialPolicy;
import common.cn.kafei.simukraft.material.WorkMaterialRequest;
import common.cn.kafei.simukraft.material.WorkMaterialResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class BuilderMaterialService {
    private BuilderMaterialService() {
    }

    public static WorkMaterialResult tryConsumeForBlock(ServerLevel level, WorkMaterialCache materialCache, BlockState targetState) {
        if (level == null || materialCache == null || targetState == null || targetState.isAir()) {
            return WorkMaterialResult.available(ItemStack.EMPTY);
        }
        WorkMaterialRequest request = WorkMaterialPolicy.requestForBlock(targetState);
        if (request.isEmpty()) {
            return WorkMaterialResult.available(ItemStack.EMPTY);
        }
        return NpcWorkMaterialService.tryConsume(level, materialCache, request);
    }

    public static String describe(ItemStack stack) {
        return NpcWorkMaterialService.describe(stack);
    }
}
