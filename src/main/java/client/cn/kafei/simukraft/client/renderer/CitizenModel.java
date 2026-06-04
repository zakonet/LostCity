package client.cn.kafei.simukraft.client.renderer;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

@SuppressWarnings("null")
public class CitizenModel extends PlayerModel<CitizenEntity> {
    public CitizenModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    // setupAnim：先跑原版基础姿势，再把旧版施工曲线固定套到右手。
    @Override
    public void setupAnim(CitizenEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        boolean builderWorkSwing = CitizenAnimationActions.canUseBuilderWorkSwing(entity);
        if (builderWorkSwing) {
            this.attackTime = 0.0F;
        }
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (builderWorkSwing) {
            CitizenAnimationActions.applyBuilderWorkSwing(this, ageInTicks);
        }
    }

}
