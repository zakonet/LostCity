package client.cn.kafei.simukraft.mixin;

import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
    private void simukraft$setup(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo callbackInfo) {
        if (FreeCameraManager.isActive() && entity instanceof LocalPlayer) {
            callbackInfo.cancel();
            setPosition(FreeCameraManager.getPosition());
            setRotation(FreeCameraManager.getYaw(), FreeCameraManager.getPitch());
        }
    }
}
