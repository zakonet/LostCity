package client.cn.kafei.simukraft.client.buildbox;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import client.cn.kafei.simukraft.client.ui.SlidingInfoPanel;
import common.cn.kafei.simukraft.network.planner.CreatePlanningTaskPacket;
import common.cn.kafei.simukraft.planner.PlanOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.util.List;

/**
 * 建筑盒"规划区域"界面：键盘操作的自由视角，框选 3D 区域并选择操作类型。
 * 确认后由服务端按体积预扣城市资金并派给规划师执行。
 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class PlanningAreaScreen extends Screen implements FreeCameraScreen {
    private static final int MAX_LEN        = 64;
    private static final int MAX_HALF_WIDTH = 32;
    private static final int MAX_HEIGHT     = 64;
    private static final int MAX_FAR        = 32;
    private static final int MAX_SIDE       = 32;
    private static final int MAX_VERTICAL   = 32;

    // 静态状态在 rebuild() 重新创建实例时保留
    private static BlockPos      boxPos;
    private static Direction     facing    = Direction.NORTH;
    private static PlanOperation operation = PlanOperation.REMOVE;
    private static int length = 5, halfWidth = 2, height = 1;
    private static int forward = 1, side = 0, vertical = 0;

    private final SlidingInfoPanel panel = new SlidingInfoPanel();

    private PlanningAreaScreen() {
        super(Component.translatable("gui.simukraft.plan_area.title"));
    }

    public static void open(BlockPos buildBoxPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        boxPos   = buildBoxPos.immutable();
        facing   = mc.player != null ? mc.player.getDirection() : Direction.NORTH;
        operation = PlanOperation.REMOVE;
        length = 5; halfWidth = 2; height = 1;
        forward = 1; side = 0; vertical = 0;
        mc.execute(() -> mc.setScreen(new PlanningAreaScreen()));
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
        Component oKey = Component.literal("O");
        int oCapW = Math.max(10, font.width(oKey) + 6);
        panel.drawKeyCapAt(g, oKey, 2, 4, oCapW, 10);
        g.drawString(font, Component.translatable(operation.translationKey()),
                2 + oCapW + 3, 4 + (10 - font.lineHeight) / 2, opColor(), false);
        g.drawCenteredString(font, Component.translatable("gui.simukraft.plan_area.title"),
                sw / 2, 5, SimuKraftUiTheme.TEXT_PRIMARY_COLOR);
        // ── 面板框架 ──
        panel.beginRender(g, font, sw, sh, 18);
        int kw = 10, kh = 10, step = 11;
        int pX  = panel.getPanelX(), pW = panel.getPanelW();
        int iX  = panel.getInnerX();
        int curY = 18 + 7;
        // ── 区域控制 ──
        panel.drawSectionTitle(g, Component.translatable("gui.simukraft.plan_area.section.adjust"), curY);
        curY += font.lineHeight + 8;
        int arrowCX = pX + 24, eCX = pX + 50, depthCX = pX + 64, sideCX = pX + 78, vertCX = pX + 104;
        panel.drawKeyCap(g, Component.literal("↑"), arrowCX,        curY,        kw, kh);
        panel.drawKeyCap(g, Component.literal("←"), arrowCX - step, curY + step, kw, kh);
        panel.drawKeyCap(g, Component.literal("↓"), arrowCX,        curY + step, kw, kh);
        panel.drawKeyCap(g, Component.literal("→"), arrowCX + step, curY + step, kw, kh);
        g.drawCenteredString(font, Component.translatable("gui.simukraft.plan_area.label.len_wid"),
                arrowCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);
        panel.drawKeyCap(g, Component.literal("E"), eCX, curY,        kw, kh);
        panel.drawKeyCap(g, Component.literal("Q"), eCX, curY + step, kw, kh);
        g.drawCenteredString(font, Component.translatable("gui.simukraft.plan_area.label.height"),
                eCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);
        panel.drawKeyCap(g, Component.literal("]"), depthCX, curY,        kw, kh);
        panel.drawKeyCap(g, Component.literal("["), depthCX, curY + step, kw, kh);
        g.drawCenteredString(font, Component.translatable("gui.simukraft.plan_area.label.depth"),
                depthCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);
        panel.drawKeyCap(g, Component.literal("."), sideCX, curY,        kw, kh);
        panel.drawKeyCap(g, Component.literal(","), sideCX, curY + step, kw, kh);
        g.drawCenteredString(font, Component.translatable("gui.simukraft.plan_area.label.side"),
                sideCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);
        // >>> PLAN_RENDER_PART2
        Component pgU = Component.literal("PgU"), pgD = Component.literal("PgD");
        int pgW = Math.max(kw, font.width(pgU) + 6);
        panel.drawKeyCapAt(g, pgU, vertCX - pgW / 2, curY,        pgW, kh);
        panel.drawKeyCapAt(g, pgD, vertCX - pgW / 2, curY + step, pgW, kh);
        g.drawCenteredString(font, Component.translatable("gui.simukraft.plan_area.label.vertical"),
                vertCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);
        curY += step * 2 + font.lineHeight + 6;
        curY = panel.drawKeyAction(g, Component.literal("R"), iX, curY, kh,
                Component.translatable("gui.simukraft.plan_area.action.align"), SimuKraftUiTheme.TEXT_MUTED_COLOR);
        panel.drawSeparator(g, curY);
        curY += 8;
        // ── 数据 ──
        panel.drawSectionTitle(g, Component.translatable("gui.simukraft.plan_area.section.data"), curY);
        curY += font.lineHeight + 8;
        g.drawString(font, Component.translatable("gui.simukraft.plan_area.operation",
                Component.translatable(operation.translationKey())), iX, curY, opColor(), false);
        curY += font.lineHeight + 2;
        List<FormattedCharSequence> blockLines = font.split(blocksLine(), pW - 16);
        for (FormattedCharSequence line : blockLines) {
            g.drawString(font, line, iX, curY, SimuKraftUiTheme.TEXT_INFO_COLOR, false);
            curY += font.lineHeight + 2;
        }
        g.drawString(font, Component.translatable("gui.simukraft.plan_area.dims",
                length, halfWidth * 2 + 1, height), iX, curY, SimuKraftUiTheme.TEXT_SUCCESS_COLOR, false);
        curY += font.lineHeight + 2;
        g.drawString(font, Component.translatable("gui.simukraft.plan_area.volume", volume()),
                iX, curY, SimuKraftUiTheme.TEXT_SUCCESS_COLOR, false);
        curY += font.lineHeight + 4;
        panel.drawSeparator(g, curY);
        curY += 8;
        // ── 操作键 ──
        curY = panel.drawKeyAction(g, Component.literal("Enter"), iX, curY, kh,
                Component.translatable("gui.simukraft.plan_area.action.confirm"), SimuKraftUiTheme.TEXT_WARNING_COLOR);
        curY = panel.drawKeyAction(g, Component.literal("ESC"), iX, curY, kh,
                Component.translatable("gui.simukraft.plan_area.action.cancel"), SimuKraftUiTheme.TEXT_WARNING_COLOR);
        panel.drawKeyAction(g, Component.literal("Tab"), iX, curY, kh,
                Component.translatable("gui.simukraft.plan_area.action.hide"), SimuKraftUiTheme.TEXT_MUTED_COLOR);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_TAB           -> { panel.toggle(); return true; }
            case GLFW.GLFW_KEY_UP            -> { adjustLength(1);     return true; }
            case GLFW.GLFW_KEY_DOWN          -> { adjustLength(-1);    return true; }
            case GLFW.GLFW_KEY_LEFT          -> { adjustHalfWidth(-1); return true; }
            case GLFW.GLFW_KEY_RIGHT         -> { adjustHalfWidth(1);  return true; }
            case GLFW.GLFW_KEY_E             -> { adjustHeight(1);     return true; }
            case GLFW.GLFW_KEY_Q             -> { adjustHeight(-1);    return true; }
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> { adjustForward(1);    return true; }
            case GLFW.GLFW_KEY_LEFT_BRACKET  -> { adjustForward(-1);   return true; }
            case GLFW.GLFW_KEY_PERIOD        -> { adjustSide(1);       return true; }
            case GLFW.GLFW_KEY_COMMA         -> { adjustSide(-1);      return true; }
            case GLFW.GLFW_KEY_PAGE_UP       -> { adjustVertical(1);   return true; }
            case GLFW.GLFW_KEY_PAGE_DOWN     -> { adjustVertical(-1);  return true; }
            case GLFW.GLFW_KEY_O             -> { operation = next(operation); refreshPreview(); rebuild(); return true; }
            case GLFW.GLFW_KEY_R             -> { facing = Direction.fromYRot(FreeCameraManager.getYaw()); refreshPreview(); rebuild(); return true; }
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

    private void adjustLength(int d)    { length    = clamp(length    + d, 1, MAX_LEN);           refreshPreview(); rebuild(); }
    private void adjustHalfWidth(int d) { halfWidth = clamp(halfWidth + d, 0, MAX_HALF_WIDTH);    refreshPreview(); rebuild(); }
    private void adjustHeight(int d)    { height    = clamp(height    + d, 1, MAX_HEIGHT);        refreshPreview(); rebuild(); }
    private void adjustForward(int d)   { forward   = clamp(forward   + d, 1, MAX_FAR);           refreshPreview(); rebuild(); }
    private void adjustSide(int d)      { side      = clamp(side      + d, -MAX_SIDE,  MAX_SIDE); refreshPreview(); rebuild(); }
    private void adjustVertical(int d)  { vertical  = clamp(vertical  + d, -MAX_VERTICAL, MAX_VERTICAL); refreshPreview(); rebuild(); }

    private void rebuild() {
        if (this.minecraft != null) { this.minecraft.setScreen(new PlanningAreaScreen()); }
    }

    private static int volume() { return length * (halfWidth * 2 + 1) * height; }

    private static BlockPos[] computeBounds() {
        int ax = facing.getStepX(), az = facing.getStepZ(), px = -az, pz = ax;
        int boxX = boxPos.getX(), boxZ = boxPos.getZ(), baseY = boxPos.getY() + vertical;
        int nearX = boxX + ax * forward + px * side, nearZ = boxZ + az * forward + pz * side;
        int farX = nearX + ax * (length - 1), farZ = nearZ + az * (length - 1);
        int[] xs = {nearX + px * halfWidth, nearX - px * halfWidth, farX + px * halfWidth, farX - px * halfWidth};
        int[] zs = {nearZ + pz * halfWidth, nearZ - pz * halfWidth, farZ + pz * halfWidth, farZ - pz * halfWidth};
        int minX = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
        int maxX = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
        int minZ = Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3]));
        int maxZ = Math.max(Math.max(zs[0], zs[1]), Math.max(zs[2], zs[3]));
        return new BlockPos[]{new BlockPos(minX, baseY, minZ), new BlockPos(maxX, baseY + height - 1, maxZ)};
    }

    private static void refreshPreview() {
        BlockPos[] b = computeBounds();
        AABB box = new AABB(b[0].getX(), b[0].getY(), b[0].getZ(), b[1].getX() + 1, b[1].getY() + 1, b[1].getZ() + 1);
        BuildingBoundsRenderer.setBuildingBoundsVisible(boxPos, box, true);
    }

    private static void clearPreview() {
        if (boxPos != null) { BuildingBoundsRenderer.setBuildingBoundsVisible(boxPos, null, false); }
    }

    private Component blocksLine() {
        if (operation == PlanOperation.REMOVE) return Component.translatable("gui.simukraft.plan_area.blocks_none");
        if (operation == PlanOperation.FILL)
            return Component.translatable("gui.simukraft.plan_area.fill_block", blockName(heldBlockId(mainHand())));
        return Component.translatable("gui.simukraft.plan_area.replace_blocks",
                blockName(heldBlockId(mainHand())), blockName(heldBlockId(offHand())));
    }

    private void confirm() {
        BlockPos targetBox = boxPos;
        BlockPos[] bounds = computeBounds();
        String fillId = "", sourceId = "";
        if (operation == PlanOperation.FILL) {
            fillId = heldBlockId(mainHand());
            if (fillId.isEmpty()) { hint("gui.simukraft.plan_area.need_fill_block"); return; }
        } else if (operation == PlanOperation.REPLACE) {
            fillId = heldBlockId(mainHand()); sourceId = heldBlockId(offHand());
            if (fillId.isEmpty() || sourceId.isEmpty()) { hint("gui.simukraft.plan_area.need_replace_blocks"); return; }
        }
        clearPreview();
        PacketDistributor.sendToServer(new CreatePlanningTaskPacket(targetBox, bounds[0], bounds[1], operation, fillId, sourceId));
        if (this.minecraft != null) { this.minecraft.setScreen(null); }
    }

    private void cancel() {
        clearPreview();
        if (this.minecraft != null) { this.minecraft.setScreen(null); }
    }

    private void hint(String key) {
        client.cn.kafei.simukraft.client.toast.ClientInfoToast.show(
                Component.translatable("toast.simukraft.title"), Component.translatable(key), "warning");
    }

    private ItemStack mainHand() {
        return this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.getMainHandItem() : ItemStack.EMPTY;
    }

    private ItemStack offHand() {
        return this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.getOffhandItem() : ItemStack.EMPTY;
    }

    private static String heldBlockId(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof BlockItem bi)
            return BuiltInRegistries.BLOCK.getKey(bi.getBlock()).toString();
        return "";
    }

    private static Component blockName(String id) {
        return (id == null || id.isEmpty())
                ? Component.translatable("gui.simukraft.plan_area.blocks_none")
                : Component.literal(id);
    }

    private static PlanOperation next(PlanOperation cur) {
        PlanOperation[] v = PlanOperation.values();
        return v[(cur.ordinal() + 1) % v.length];
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private int opColor() {
        return switch (operation) {
            case FILL    -> SimuKraftUiTheme.TEXT_INFO_COLOR;
            case REPLACE -> SimuKraftUiTheme.TEXT_SUCCESS_COLOR;
            default      -> SimuKraftUiTheme.TEXT_WARNING_COLOR;
        };
    }
}