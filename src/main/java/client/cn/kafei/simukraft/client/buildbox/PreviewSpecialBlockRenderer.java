package client.cn.kafei.simukraft.client.buildbox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class PreviewSpecialBlockRenderer {
    private static ModelPart bedHeadRoot;
    private static ModelPart bedFootRoot;

    private PreviewSpecialBlockRenderer() {
    }

    public static void render(List<PreviewBlockData> blocks, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 cameraPos) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        for (PreviewBlockData block : blocks) {
            BlockState state = block.state();
            if (state.getBlock() instanceof BedBlock bedBlock) {
                // 床没有普通方块模型，必须按方块实体模型渲染。
                renderBed(block, bedBlock, poseStack, bufferSource, cameraPos);
            }
        }

        bufferSource.endBatch(Sheets.bedSheet());
    }

    private static void renderBed(PreviewBlockData block, BedBlock bedBlock, PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        ensureBedModels();
        BlockState state = block.state();
        ModelPart bedPartRoot = state.getValue(BedBlock.PART) == BedPart.HEAD ? bedHeadRoot : bedFootRoot;
        Material material = Sheets.BED_TEXTURES[bedBlock.getColor().getId()];

        poseStack.pushPose();
        poseStack.translate(block.pos().getX() - cameraPos.x, block.pos().getY() - cameraPos.y, block.pos().getZ() - cameraPos.z);
        renderBedPiece(poseStack, bufferSource, bedPartRoot, state.getValue(BedBlock.FACING), material, block.packedLight());
        poseStack.popPose();
    }

    private static void ensureBedModels() {
        if (bedHeadRoot != null && bedFootRoot != null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // 模型层由 Minecraft 资源重载系统维护，首次需要时再烘焙即可。
        bedHeadRoot = minecraft.getEntityModels().bakeLayer(ModelLayers.BED_HEAD);
        bedFootRoot = minecraft.getEntityModels().bakeLayer(ModelLayers.BED_FOOT);
    }

    private static void renderBedPiece(PoseStack poseStack, MultiBufferSource bufferSource, ModelPart modelPart, Direction facing, Material material, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.5625F, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F + facing.toYRot()));
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        VertexConsumer vertexConsumer = material.buffer(bufferSource, RenderType::entitySolid);
        modelPart.render(poseStack, vertexConsumer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
