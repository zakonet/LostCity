package client.cn.kafei.simukraft.client.logistics;

import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraScreen;
import client.cn.kafei.simukraft.client.input.SimuKraftKeyMappings;
import client.cn.kafei.simukraft.client.selection.TwoPointSelectionManager;
import client.cn.kafei.simukraft.client.toast.ClientInfoToast;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import client.cn.kafei.simukraft.client.ui.SlidingInfoPanel;
import common.cn.kafei.simukraft.logistics.LogisticsDirection;
import common.cn.kafei.simukraft.network.logistics.LogisticsBoxActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class LogisticsAreaSelectionScreen extends Screen implements FreeCameraScreen {
    private static final double REACH = 128.0D;

    private final BlockPos boxPos;
    private final LogisticsBoxActionPacket.Action action;
    private final SlidingInfoPanel panel = new SlidingInfoPanel();

    private LogisticsAreaSelectionScreen(BlockPos boxPos, LogisticsBoxActionPacket.Action action) {
        super(Component.translatable("gui.simukraft.logistics.area_selection.title"));
        this.boxPos  = boxPos.immutable();
        this.action  = action;
    }

    /** openWarehouseBinding: 打开仓库容器批量绑定选区。 */
    public static void openWarehouseBinding(BlockPos boxPos) {
        open(boxPos, LogisticsBoxActionPacket.Action.BIND_WAREHOUSE_AREA);
    }

    /** openClientBinding: 打开客户端端口批量绑定选区。 */
    public static void openClientBinding(BlockPos boxPos) {
        open(boxPos, LogisticsBoxActionPacket.Action.BIND_CLIENT_AREA);
    }

    private static void open(BlockPos boxPos, LogisticsBoxActionPacket.Action action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.execute(() -> mc.setScreen(new LogisticsAreaSelectionScreen(boxPos, action)));
    }

    @Override
    protected void init() {
        TwoPointSelectionManager.start(TwoPointSelectionManager.SelectionMode.LOGISTICS, boxPos, null);
        FreeCameraManager.activate();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 不绘制暗底，保持世界可见
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int sw = this.width, sh = this.height;
        // ── 顶部标题条 ──
        g.fill(0, 0, sw, 18, 0xAA000000);
        g.fill(0, 17, sw, 18, 0x55AAAACC);
        g.drawCenteredString(font, Component.translatable("gui.simukraft.logistics.area_selection.header", actionName()),
                sw / 2, 5, SimuKraftUiTheme.TEXT_WARNING_COLOR);
        // ── 面板框架 ──
        panel.beginRender(g, font, sw, sh, 18);
        int kh = 10, iX = panel.getInnerX();
        int curY = 18 + 7;
        // ── 选区操作 ──
        panel.drawSectionTitle(g, Component.translatable("gui.simukraft.logistics.area_selection.section.selection"), curY);
        curY += font.lineHeight + 8;
        curY = panel.drawKeyAction(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.SELECTION_POINT_1), iX, curY, kh,
                Component.translatable("gui.simukraft.area_selection.point1.label"), SimuKraftUiTheme.TEXT_INFO_COLOR);
        curY = panel.drawKeyAction(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.SELECTION_POINT_2), iX, curY, kh,
                Component.translatable("gui.simukraft.area_selection.point2.label"), SimuKraftUiTheme.TEXT_INFO_COLOR);
        panel.drawSeparator(g, curY); curY += 8;
        // ── 摄像机 ──
        panel.drawSectionTitle(g, Component.translatable("gui.simukraft.logistics.area_selection.section.camera"), curY);
        curY += font.lineHeight + 8;
        var mc = this.minecraft;
        if (mc != null) {
            int step = 11, kw = 10, wasdCX = panel.getPanelX() + 26;
            panel.drawKeyCap(g, mc.options.keyUp.getTranslatedKeyMessage(),    wasdCX,        curY,        kw, kh);
            panel.drawKeyCap(g, mc.options.keyLeft.getTranslatedKeyMessage(),  wasdCX - step, curY + step, kw, kh);
            panel.drawKeyCap(g, mc.options.keyDown.getTranslatedKeyMessage(),  wasdCX,        curY + step, kw, kh);
            panel.drawKeyCap(g, mc.options.keyRight.getTranslatedKeyMessage(), wasdCX + step, curY + step, kw, kh);
            int modX = panel.getPanelX() + 58;
            int shiftW = Math.max(14, font.width(mc.options.keyShift.getTranslatedKeyMessage()) + 6);
            int spaceW = panel.getPanelW() - 16 - (modX - iX);
            panel.drawKeyCapAt(g, mc.options.keyShift.getTranslatedKeyMessage(), modX, curY,        shiftW, kh);
            panel.drawKeyCapAt(g, mc.options.keyJump.getTranslatedKeyMessage(),  modX, curY + step, spaceW, kh);
            curY += step * 2 + font.lineHeight + 5;
        }
        panel.drawSeparator(g, curY); curY += 8;
        // ── 数据 ──
        panel.drawSectionTitle(g, Component.translatable("gui.simukraft.logistics.area_selection.section.data"), curY);
        curY += font.lineHeight + 8;
        TwoPointSelectionManager.SelectionState state = TwoPointSelectionManager.state();
        curY += font.lineHeight + 2;
        g.drawString(font, pointLine("gui.simukraft.area_selection.point1", state.point1()),
                iX, curY, SimuKraftUiTheme.TEXT_SECONDARY_COLOR, false); curY += font.lineHeight + 2;
        g.drawString(font, pointLine("gui.simukraft.area_selection.point2", state.point2()),
                iX, curY, SimuKraftUiTheme.TEXT_SECONDARY_COLOR, false); curY += font.lineHeight + 2;
        if (state.point1() != null && state.point2() != null) {
            BlockPos min = TwoPointSelectionManager.min(state.point1(), state.point2());
            BlockPos max = TwoPointSelectionManager.max(state.point1(), state.point2());
            g.drawString(font, Component.translatable("gui.simukraft.logistics.area_selection.volume",
                    selectedVolume(min, max)), iX, curY, SimuKraftUiTheme.TEXT_SUCCESS_COLOR, false);
            curY += font.lineHeight + 2;
        }
        panel.drawSeparator(g, curY); curY += 8;
        // ── 操作键 ──
        curY = panel.drawKeyAction(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.SELECTION_CONFIRM), iX, curY, kh,
                Component.translatable("gui.simukraft.logistics.area_selection.action.confirm"), SimuKraftUiTheme.TEXT_WARNING_COLOR);
        curY = panel.drawKeyAction(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.SELECTION_CANCEL), iX, curY, kh,
                Component.translatable("gui.simukraft.logistics.area_selection.action.cancel"), SimuKraftUiTheme.TEXT_WARNING_COLOR);
        panel.drawKeyAction(g, Component.literal("Tab"), iX, curY, kh,
                Component.translatable("gui.simukraft.logistics.area_selection.action.hide"), SimuKraftUiTheme.TEXT_MUTED_COLOR);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            panel.toggle(); return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.SELECTION_POINT_1, keyCode, scanCode)) {
            setPoint(true); return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.SELECTION_POINT_2, keyCode, scanCode)) {
            setPoint(false); return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.SELECTION_CONFIRM, keyCode, scanCode)) {
            confirm(); return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.SELECTION_CANCEL, keyCode, scanCode)) {
            cancel(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        super.removed();
        TwoPointSelectionManager.clear();
        FreeCameraManager.deactivate();
        if (this.minecraft != null && this.minecraft.mouseHandler.isMouseGrabbed()) {
            this.minecraft.mouseHandler.releaseMouse();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (SimuKraftKeyMappings.matchesMouse(SimuKraftKeyMappings.SELECTION_POINT_1, button)) {
            setPoint(true); return true;
        }
        if (SimuKraftKeyMappings.matchesMouse(SimuKraftKeyMappings.SELECTION_POINT_2, button)) {
            setPoint(false); return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void setPoint(boolean first) {
        BlockPos hit = raycastBlock();
        if (hit == null) {
            ClientInfoToast.show(Component.translatable("toast.simukraft.title"),
                    Component.translatable("gui.simukraft.area_selection.no_target"), "warning");
            return;
        }
        if (first) TwoPointSelectionManager.setPoint1(hit);
        else        TwoPointSelectionManager.setPoint2(hit);
    }

    @Nullable
    private BlockPos raycastBlock() {
        var mc = this.minecraft;
        if (mc == null || mc.level == null || mc.player == null) return null;
        Vec3 cameraPos = FreeCameraManager.getPosition();
        double yawRad   = Math.toRadians(FreeCameraManager.getYaw());
        double pitchRad = Math.toRadians(FreeCameraManager.getPitch());
        Vec3 look = new Vec3(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad)).normalize();
        BlockHitResult result = mc.level.clip(new ClipContext(
                cameraPos, cameraPos.add(look.scale(REACH)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return result.getType() == HitResult.Type.BLOCK ? result.getBlockPos() : null;
    }

    private void confirm() {
        TwoPointSelectionManager.SelectionState state = TwoPointSelectionManager.state();
        if (state.point1() == null || state.point2() == null) {
            ClientInfoToast.show(Component.translatable("toast.simukraft.title"),
                    Component.translatable("gui.simukraft.area_selection.need_points"), "warning");
            return;
        }
        BlockPos min = TwoPointSelectionManager.min(state.point1(), state.point2());
        BlockPos max = TwoPointSelectionManager.max(state.point1(), state.point2());
        TwoPointSelectionManager.clear();
        PacketDistributor.sendToServer(new LogisticsBoxActionPacket(
                boxPos, action, new UUID(0, 0), new UUID(0, 0),
                BlockPos.ZERO, "", LogisticsDirection.WAREHOUSE_TO_CLIENT, min, max, List.of()));
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    private void cancel() {
        TwoPointSelectionManager.clear();
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    private String actionName() {
        return Component.translatable("gui.simukraft.logistics.action." + action.name().toLowerCase()).getString();
    }

    private Component pointLine(String key, @Nullable BlockPos pos) {
        return pos == null
            ? Component.translatable(key + ".empty")
            : Component.translatable(key + ".set", pos.getX(), pos.getY(), pos.getZ());
    }

    private static int selectedVolume(BlockPos min, BlockPos max) {
        return (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
    }
}