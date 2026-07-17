package client.cn.kafei.simukraft.client.farmland;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import client.cn.kafei.simukraft.client.ui.SlidingInfoPanel;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxSetAreaPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 作业区域设置界面（面向相对 + 自由视角）。
 * 进入即自由视角，可 WASD/空格/Shift 飞行，鼠标转向；方向键调整区域，R 对齐。
 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class FarmlandAreaScreen extends Screen implements FreeCameraScreen {
    private static final int MAX_LENGTH    = 33;
    private static final int MAX_HALF_WIDTH = 16;
    private static final int MAX_GAP       = 16;
    private static final int MAX_SIDE      = 16;

    private static BlockPos   boxPos;
    private static Direction  facing    = Direction.NORTH;
    private static int length = 7, halfWidth = 3, gap = 1, side = 0;

    private final SlidingInfoPanel panel = new SlidingInfoPanel();

    private FarmlandAreaScreen() {
        super(Component.translatable("gui.simukraft.farmland_box.area_title"));
    }

    public static void open(FarmlandBoxOpenResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        boxPos = packet.boxPos().immutable();
        facing = mc.player != null ? mc.player.getDirection() : Direction.NORTH;
        if (packet.hasPlot()) {
            BlockPos min = packet.plotMin(), max = packet.plotMax();
            boolean alongZ = facing.getAxis() == Direction.Axis.Z;
            int along = alongZ ? (max.getZ() - min.getZ() + 1) : (max.getX() - min.getX() + 1);
            int perp  = alongZ ? (max.getX() - min.getX() + 1) : (max.getZ() - min.getZ() + 1);
            length    = clamp(along, 1, MAX_LENGTH);
            halfWidth = clamp((perp - 1) / 2, 0, MAX_HALF_WIDTH);
        } else {
            length = 7; halfWidth = 3;
        }
        gap = 1; side = 0;
        mc.execute(() -> mc.setScreen(new FarmlandAreaScreen()));
    }

    @Override
    protected void init() {
        super.init();
        refreshPreview();
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
        g.drawCenteredString(font, Component.translatable("gui.simukraft.farmland_box.area_title"),
                sw / 2, 5, SimuKraftUiTheme.TEXT_PRIMARY_COLOR);
        // ── 面板框架 ──
        panel.beginRender(g, font, sw, sh, 18);
        int kw = 10, kh = 10, step = 11;
        int pX = panel.getPanelX(), pW = panel.getPanelW(), iX = panel.getInnerX();
        int curY = 18 + 7;
        // ── 区域控制 ──
        panel.drawSectionTitle(g, Component.translatable("gui.simukraft.farmland_box.section.adjust"), curY);
        curY += font.lineHeight + 8;
        int arrowCX = pX + 26, eqCX = pX + 56, brCX = pX + 76;
        panel.drawKeyCap(g, Component.literal("↑"), arrowCX,        curY,        kw, kh);
        panel.drawKeyCap(g, Component.literal("←"), arrowCX - step, curY + step, kw, kh);
        panel.drawKeyCap(g, Component.literal("↓"), arrowCX,        curY + step, kw, kh);
        panel.drawKeyCap(g, Component.literal("→"), arrowCX + step, curY + step, kw, kh);
        g.drawCenteredString(font, Component.translatable("gui.simukraft.farmland_box.label.len_wid"),
                arrowCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);
        panel.drawKeyCap(g, Component.literal("E"), eqCX, curY,        kw, kh);
        panel.drawKeyCap(g, Component.literal("Q"), eqCX, curY + step, kw, kh);
        g.drawCenteredString(font, Component.translatable("gui.simukraft.farmland_box.label.gap"),
                eqCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);
        panel.drawKeyCap(g, Component.literal("]"), brCX, curY,        kw, kh);
        panel.drawKeyCap(g, Component.literal("["), brCX, curY + step, kw, kh);
        g.drawCenteredString(font, Component.translatable("gui.simukraft.farmland_box.label.side"),
                brCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);
        curY += step * 2 + font.lineHeight + 6;
        curY = panel.drawKeyAction(g, Component.literal("R"), iX, curY, kh,
                Component.translatable("gui.simukraft.farmland_box.action.align"), SimuKraftUiTheme.TEXT_MUTED_COLOR);
        panel.drawSeparator(g, curY); curY += 8;
        // ── 摄像机 ──
        panel.drawSectionTitle(g, Component.translatable("gui.simukraft.farmland_box.section.camera"), curY);
        curY += font.lineHeight + 8;
        var mc = this.minecraft;
        if (mc != null) {
            int wasdCX = pX + 26;
            panel.drawKeyCap(g, mc.options.keyUp.getTranslatedKeyMessage(),    wasdCX,        curY,        kw, kh);
            panel.drawKeyCap(g, mc.options.keyLeft.getTranslatedKeyMessage(),  wasdCX - step, curY + step, kw, kh);
            panel.drawKeyCap(g, mc.options.keyDown.getTranslatedKeyMessage(),  wasdCX,        curY + step, kw, kh);
            panel.drawKeyCap(g, mc.options.keyRight.getTranslatedKeyMessage(), wasdCX + step, curY + step, kw, kh);
            int modX = pX + 58;
            int shiftW = Math.max(14, font.width(mc.options.keyShift.getTranslatedKeyMessage()) + 6);
            int spaceW = pW - 16 - (modX - iX);
            panel.drawKeyCapAt(g, mc.options.keyShift.getTranslatedKeyMessage(), modX, curY,        shiftW, kh);
            panel.drawKeyCapAt(g, mc.options.keyJump.getTranslatedKeyMessage(),  modX, curY + step, spaceW, kh);
        }
        curY += step * 2 + font.lineHeight + 5;
        panel.drawSeparator(g, curY); curY += 8;
        // ── 数据 ──
        panel.drawSectionTitle(g, Component.translatable("gui.simukraft.farmland_box.section.data"), curY);
        curY += font.lineHeight + 8;
        g.drawString(font, Component.translatable("gui.simukraft.farmland_box.area_values",
                length, halfWidth * 2 + 1, gap, side), iX, curY, SimuKraftUiTheme.TEXT_SUCCESS_COLOR, false);
        curY += font.lineHeight + 4;
        panel.drawSeparator(g, curY); curY += 8;
        // ── 操作键 ──
        curY = panel.drawKeyAction(g, Component.literal("Enter"), iX, curY, kh,
                Component.translatable("gui.simukraft.farmland_box.action.confirm"), SimuKraftUiTheme.TEXT_WARNING_COLOR);
        curY = panel.drawKeyAction(g, Component.literal("ESC"), iX, curY, kh,
                Component.translatable("gui.simukraft.farmland_box.action.cancel"), SimuKraftUiTheme.TEXT_WARNING_COLOR);
        panel.drawKeyAction(g, Component.literal("Tab"), iX, curY, kh,
                Component.translatable("gui.simukraft.farmland_box.action.hide"), SimuKraftUiTheme.TEXT_MUTED_COLOR);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_TAB           -> { panel.toggle(); return true; }
            case GLFW.GLFW_KEY_UP            -> { adjustLength(1);     return true; }
            case GLFW.GLFW_KEY_DOWN          -> { adjustLength(-1);    return true; }
            case GLFW.GLFW_KEY_RIGHT         -> { adjustHalfWidth(1);  return true; }
            case GLFW.GLFW_KEY_LEFT          -> { adjustHalfWidth(-1); return true; }
            case GLFW.GLFW_KEY_E             -> { adjustGap(1);        return true; }
            case GLFW.GLFW_KEY_Q             -> { adjustGap(-1);       return true; }
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> { adjustSide(1);       return true; }
            case GLFW.GLFW_KEY_LEFT_BRACKET  -> { adjustSide(-1);      return true; }
            case GLFW.GLFW_KEY_R             -> { facing = Direction.fromYRot(FreeCameraManager.getYaw()); refreshPreview(); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { confirm(); return true; }
            case GLFW.GLFW_KEY_ESCAPE        -> { cancel(); return true; }
            default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        super.removed();
        FreeCameraManager.deactivate();
        clearPreview();
        if (this.minecraft != null && this.minecraft.mouseHandler.isMouseGrabbed()) {
            this.minecraft.mouseHandler.releaseMouse();
        }
    }

    private void adjustLength(int d)    { length    = clamp(length    + d, 1, MAX_LENGTH);               refreshPreview(); }
    private void adjustHalfWidth(int d) { halfWidth = clamp(halfWidth + d, 0, MAX_HALF_WIDTH);            refreshPreview(); }
    private void adjustGap(int d)       { gap       = clamp(gap       + d, 0, MAX_GAP);                  refreshPreview(); }
    private void adjustSide(int d)      { side      = clamp(side      + d, -MAX_SIDE, MAX_SIDE);          refreshPreview(); }

    private static BlockPos[] computeBounds() {
        int ax = facing.getStepX(), az = facing.getStepZ();
        int px = -az, pz = ax; // 垂直方向
        int bX = boxPos.getX(), bZ = boxPos.getZ(), bY = boxPos.getY();
        int nearX = bX + ax * gap + px * side, nearZ = bZ + az * gap + pz * side;
        int farX = nearX + ax * (length - 1), farZ = nearZ + az * (length - 1);
        int[] xs = {nearX + px * halfWidth, nearX - px * halfWidth, farX + px * halfWidth, farX - px * halfWidth};
        int[] zs = {nearZ + pz * halfWidth, nearZ - pz * halfWidth, farZ + pz * halfWidth, farZ - pz * halfWidth};
        int minX = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
        int maxX = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
        int minZ = Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3]));
        int maxZ = Math.max(Math.max(zs[0], zs[1]), Math.max(zs[2], zs[3]));
        return new BlockPos[]{new BlockPos(minX, bY, minZ), new BlockPos(maxX, bY, maxZ)};
    }

    private static void refreshPreview() {
        if (boxPos == null) return;
        BlockPos[] b = computeBounds();
        AABB box = new AABB(b[0].getX(), b[0].getY(), b[0].getZ(),
                b[1].getX() + 1, b[1].getY() + 1, b[1].getZ() + 1);
        BuildingBoundsRenderer.setBuildingBoundsVisible(boxPos, box, true);
    }

    private static void clearPreview() {
        if (boxPos != null) BuildingBoundsRenderer.setBuildingBoundsVisible(boxPos, null, false);
    }

    private void confirm() {
        BlockPos[] bounds = computeBounds();
        clearPreview();
        PacketDistributor.sendToServer(new FarmlandBoxSetAreaPacket(boxPos, bounds[0], bounds[1]));
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    private void cancel() {
        clearPreview();
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}