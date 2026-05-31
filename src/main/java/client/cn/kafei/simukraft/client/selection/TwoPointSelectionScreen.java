package client.cn.kafei.simukraft.client.selection;

import client.cn.kafei.simukraft.client.buildbox.PlannerOperationScreenOpener;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraScreen;
import client.cn.kafei.simukraft.client.input.SimuKraftKeyMappings;
import client.cn.kafei.simukraft.client.toast.ClientInfoToast;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxSetAreaPacket;
import common.cn.kafei.simukraft.planner.PlanOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

public final class TwoPointSelectionScreen extends Screen implements FreeCameraScreen {
    private static final double REACH_DISTANCE = 128.0D;

    private final TwoPointSelectionManager.SelectionMode mode;
    private final BlockPos ownerPos;
    @Nullable
    private final PlanOperation operation;

    private TwoPointSelectionScreen(TwoPointSelectionManager.SelectionMode mode, BlockPos ownerPos, @Nullable PlanOperation operation) {
        super(Component.translatable("gui.simukraft.area_selection.title"));
        this.mode = mode;
        this.ownerPos = ownerPos.immutable();
        this.operation = operation;
    }

    public static void openPlanning(BlockPos buildBoxPos, PlanOperation operation) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> minecraft.setScreen(new TwoPointSelectionScreen(TwoPointSelectionManager.SelectionMode.PLANNING, buildBoxPos, operation)));
    }

    public static void openFarmland(FarmlandBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> minecraft.setScreen(new TwoPointSelectionScreen(TwoPointSelectionManager.SelectionMode.FARMLAND, packet.boxPos(), null)));
    }

    @Override
    protected void init() {
        super.init();
        TwoPointSelectionManager.start(mode, ownerPos, operation);
        FreeCameraManager.activate();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;
        int y = 10;
        Component modeName = mode == TwoPointSelectionManager.SelectionMode.FARMLAND
                ? Component.translatable("gui.simukraft.area_selection.mode.farmland")
                : Component.translatable(operation != null ? operation.translationKey() : "gui.simukraft.plan_area.op.remove");
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.simukraft.area_selection.title_mode", modeName), centerX, y, SimuKraftUiTheme.TEXT_PRIMARY_COLOR);
        y += 15;
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.simukraft.area_selection.controls",
                SimuKraftKeyMappings.display(SimuKraftKeyMappings.SELECTION_POINT_1),
                SimuKraftKeyMappings.display(SimuKraftKeyMappings.SELECTION_POINT_2),
                SimuKraftKeyMappings.display(SimuKraftKeyMappings.SELECTION_CONFIRM),
                SimuKraftKeyMappings.display(SimuKraftKeyMappings.SELECTION_CANCEL)), centerX, y, SimuKraftUiTheme.TEXT_WARNING_COLOR);
        y += 12;
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.simukraft.area_selection.camera_hint"), centerX, y, SimuKraftUiTheme.TEXT_INFO_COLOR);
        y += 16;
        TwoPointSelectionManager.SelectionState state = TwoPointSelectionManager.state();
        guiGraphics.drawCenteredString(this.font, pointLine("gui.simukraft.area_selection.point1", state.point1()), centerX, y, SimuKraftUiTheme.TEXT_SECONDARY_COLOR);
        y += 12;
        guiGraphics.drawCenteredString(this.font, pointLine("gui.simukraft.area_selection.point2", state.point2()), centerX, y, SimuKraftUiTheme.TEXT_SECONDARY_COLOR);
        y += 12;
        if (state.point1() != null && state.point2() != null) {
            BlockPos min = TwoPointSelectionManager.min(state.point1(), state.point2());
            BlockPos max = TwoPointSelectionManager.max(state.point1(), state.point2());
            int volume = (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui.simukraft.area_selection.bounds",
                    min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ(), volume), centerX, y, SimuKraftUiTheme.TEXT_SUCCESS_COLOR);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.SELECTION_POINT_1, keyCode, scanCode)) {
            setPoint(true);
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.SELECTION_POINT_2, keyCode, scanCode)) {
            setPoint(false);
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.SELECTION_CONFIRM, keyCode, scanCode)) {
            confirm();
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.SELECTION_CANCEL, keyCode, scanCode)) {
            cancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (SimuKraftKeyMappings.matchesMouse(SimuKraftKeyMappings.SELECTION_POINT_1, button)) {
            setPoint(true);
            return true;
        }
        if (SimuKraftKeyMappings.matchesMouse(SimuKraftKeyMappings.SELECTION_POINT_2, button)) {
            setPoint(false);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        FreeCameraManager.deactivate();
        TwoPointSelectionManager.clear();
        if (this.minecraft != null && this.minecraft.mouseHandler.isMouseGrabbed()) {
            this.minecraft.mouseHandler.releaseMouse();
        }
    }

    private void setPoint(boolean first) {
        BlockPos hit = raycastBlock();
        if (hit == null) {
            ClientInfoToast.show(Component.translatable("toast.simukraft.title"), Component.translatable("gui.simukraft.area_selection.no_target"), "warning");
            return;
        }
        if (first) {
            TwoPointSelectionManager.setPoint1(hit);
        } else {
            TwoPointSelectionManager.setPoint2(hit);
        }
    }

    @Nullable
    private BlockPos raycastBlock() {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) {
            return null;
        }
        Vec3 cameraPos = FreeCameraManager.getPosition();
        double yawRad = Math.toRadians(FreeCameraManager.getYaw());
        double pitchRad = Math.toRadians(FreeCameraManager.getPitch());
        Vec3 look = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad)).normalize();
        BlockHitResult result = this.minecraft.level.clip(new ClipContext(
                cameraPos,
                cameraPos.add(look.scale(REACH_DISTANCE)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                this.minecraft.player));
        return result.getType() == HitResult.Type.BLOCK ? result.getBlockPos() : null;
    }

    private void confirm() {
        TwoPointSelectionManager.SelectionState state = TwoPointSelectionManager.state();
        if (state.point1() == null || state.point2() == null) {
            ClientInfoToast.show(Component.translatable("toast.simukraft.title"), Component.translatable("gui.simukraft.area_selection.need_points"), "warning");
            return;
        }
        BlockPos min = TwoPointSelectionManager.min(state.point1(), state.point2());
        BlockPos max = TwoPointSelectionManager.max(state.point1(), state.point2());
        if (mode == TwoPointSelectionManager.SelectionMode.FARMLAND) {
            PacketDistributor.sendToServer(new FarmlandBoxSetAreaPacket(ownerPos, min, max));
            closeSelection(null);
            return;
        }
        if (operation != null) {
            closeSelection(() -> PlannerOperationScreenOpener.afterAreaSelected(ownerPos, operation, min, max));
        }
    }

    private void cancel() {
        if (mode == TwoPointSelectionManager.SelectionMode.FARMLAND) {
            PacketDistributor.sendToServer(new FarmlandBoxOpenRequestPacket(ownerPos));
            closeSelection(null);
            return;
        }
        closeSelection(() -> PlannerOperationScreenOpener.open(ownerPos));
    }

    private void closeSelection(@Nullable Runnable next) {
        Minecraft minecraft = this.minecraft;
        TwoPointSelectionManager.clear();
        FreeCameraManager.deactivate();
        if (minecraft != null && minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.releaseMouse();
        }
        if (minecraft != null) {
            minecraft.setScreen(null);
            if (next != null) {
                minecraft.execute(next);
            }
        }
    }

    private Component pointLine(String key, @Nullable BlockPos pos) {
        if (pos == null) {
            return Component.translatable(key + ".empty");
        }
        return Component.translatable(key + ".set", pos.getX(), pos.getY(), pos.getZ());
    }
}
