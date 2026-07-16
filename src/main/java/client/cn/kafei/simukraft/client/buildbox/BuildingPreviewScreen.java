package client.cn.kafei.simukraft.client.buildbox;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraScreen;
import client.cn.kafei.simukraft.client.input.SimuKraftKeyMappings;
import client.cn.kafei.simukraft.client.toast.ClientInfoToast;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import common.cn.kafei.simukraft.building.BuildingStructure;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.network.building.BuildBoxStartConstructionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.util.List;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class BuildingPreviewScreen extends Screen implements FreeCameraScreen {
    private final Screen parent;
    private final BuildingCacheService.BuildingMeta building;
    private final BlockPos buildBoxPos;
    private final BuildingStructure structure;

    private boolean panelVisible = true;
    private float slideProgress = 1.0f;
    private boolean replaceWithAir = false;

    public BuildingPreviewScreen(Screen parent, BuildingCacheService.BuildingMeta building, BlockPos buildBoxPos, BuildingStructure structure) {
        super(Component.translatable("gui.building_preview.title"));
        this.parent = parent;
        this.building = building;
        this.buildBoxPos = buildBoxPos;
        this.structure = structure;
    }

    @Override
    protected void init() {
        super.init();
        BlockPos previewOrigin = buildBoxPos.offset(1, 0, 1);
        BuildingPreviewManager.startPreview(structure, previewOrigin);
        if (!BuildingPreviewManager.isPreviewActive()) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
            return;
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            BuildingBoundsRenderer.setPreviewPlayerId(this.minecraft.player.getUUID());
        }
        FreeCameraManager.activate();
    }

    @Override
    public void removed() {
        super.removed();
        releasePreviewMouse();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int sw = this.width, sh = this.height;

        // ── 顶部标题条 ──────────────────────────────────────────
        g.fill(0, 0, sw, 18, 0xAA000000);
        g.fill(0, 17, sw, 18, 0x55AAAACC);
        // 左上角：屏障图标 + 替换模式状态
        g.renderItem(new ItemStack(Items.BARRIER), 2, 1);
        Component modeText = Component.translatable(replaceWithAir ? "gui.building_preview.mode.replace_air" : "gui.building_preview.mode.keep_obstacles");
        int modeColor = replaceWithAir ? SimuKraftUiTheme.TEXT_SUCCESS_COLOR : SimuKraftUiTheme.TEXT_MUTED_COLOR;
        Component eKey = Component.literal("E");
        int eCapW = Math.max(10, font.width(eKey) + 6);
        drawKeyCapAt(g, eKey, 22, 4, eCapW, 10);
        g.drawString(font, modeText, 22 + eCapW + 3, 4 + (10 - font.lineHeight) / 2, modeColor, false);
        g.drawCenteredString(font,
                Component.translatable("gui.building_preview.title_with_name", building.name()),
                sw / 2, 5, SimuKraftUiTheme.TEXT_PRIMARY_COLOR);

        // ── 滑动动画 ────────────────────────────────────────────
        float slideTarget = panelVisible ? 1.0f : 0.0f;
        float delta = slideTarget - slideProgress;
        slideProgress = Math.abs(delta) > 0.004f ? slideProgress + delta * 0.2f : slideTarget;

        int panelW = 132, panelY = 18;
        int kw = 10, kh = 10, step = 11;

        // 预计算换行文本（用于动态面板高度）
        BlockPos origin = BuildingPreviewManager.getPreviewOrigin();
        Component originText = Component.translatable("gui.building_preview.origin_info",
                origin.getX(), origin.getY(), origin.getZ(), BuildingPreviewManager.getRotationDegrees());
        Component blockText = Component.translatable("gui.building_preview.block_count", structure.blockCount());
        List<FormattedCharSequence> originLines = font.split(originText, panelW - 16);
        List<FormattedCharSequence> blockLines  = font.split(blockText,  panelW - 16);

        // 两个控制区共用同一高度（WASD T形 + 标签行）
        int sectionH  = step * 2 + font.lineHeight + 5;
        // 区块标题行高度
        int titleH    = font.lineHeight + 8;
        int panelH = sh - panelY;

        int panelX = sw - (int)(slideProgress * (panelW + 6));

        // ── 拉片 ────────────────────────────────────────────────
        int tabX = panelX - 12, tabY = panelY + panelH / 2 - 8;
        g.fill(tabX, tabY, tabX + 12, tabY + 16, 0xAA000000);
        g.fill(tabX, tabY, tabX + 12, tabY + 1,  0x44AAAACC);
        g.fill(tabX, tabY, tabX + 1,  tabY + 16, 0x44AAAACC);
        g.drawCenteredString(font, Component.literal(panelVisible ? "▶" : "◀"), tabX + 6, tabY + 4, 0xFF8888AA);

        // ── 面板背景 ────────────────────────────────────────────
        drawPanel(g, panelX, panelY, panelW, panelH);

        int curY   = panelY + 7;
        int innerX = panelX + 8;

        // ── 预览移动（方向键）──────────────────────────────────
        g.drawCenteredString(font, Component.translatable("gui.building_preview.section.preview_move"), panelX + panelW / 2, curY, 0xFFCC9944);
        curY += titleH;
        int arrowCX = panelX + 30;
        drawKeyCap(g, Component.literal("↑"), arrowCX,        curY,        kw, kh);
        drawKeyCap(g, Component.literal("←"), arrowCX - step, curY + step, kw, kh);
        drawKeyCap(g, Component.literal("↓"), arrowCX,        curY + step, kw, kh);
        drawKeyCap(g, Component.literal("→"), arrowCX + step, curY + step, kw, kh);
        g.drawCenteredString(font, Component.translatable("gui.building_preview.label.move"), arrowCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);

        int heightCX = panelX + 62;
        drawKeyCap(g, Component.literal("+"), heightCX, curY,        kw, kh);
        drawKeyCap(g, Component.literal("−"), heightCX, curY + step, kw, kh);
        g.drawCenteredString(font, Component.translatable("gui.building_preview.label.height"), heightCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);

        int rotateCX = panelX + 86;
        drawKeyCap(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_ROTATE), rotateCX, curY + step / 2, kw, kh);
        g.drawCenteredString(font, Component.translatable("gui.building_preview.label.rotate"), rotateCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);

        curY += sectionH;
        g.fill(innerX, curY, panelX + panelW - 8, curY + 1, 0x33FFFFFF);
        curY += 8;

        // ── 摄像机移动（WASD + Shift/Space）──────────────────
        g.drawCenteredString(font, Component.translatable("gui.building_preview.section.camera"), panelX + panelW / 2, curY, 0xFFCC9944);
        curY += titleH;
        var mc = this.minecraft;
        if (mc != null) {
            int wasdCX = panelX + 30;
            drawKeyCap(g, mc.options.keyUp.getTranslatedKeyMessage(),    wasdCX,        curY,        kw, kh);
            drawKeyCap(g, mc.options.keyLeft.getTranslatedKeyMessage(),  wasdCX - step, curY + step, kw, kh);
            drawKeyCap(g, mc.options.keyDown.getTranslatedKeyMessage(),  wasdCX,        curY + step, kw, kh);
            drawKeyCap(g, mc.options.keyRight.getTranslatedKeyMessage(), wasdCX + step, curY + step, kw, kh);
            g.drawCenteredString(font, Component.translatable("gui.building_preview.label.move"), wasdCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);

            // Shift（下降）在上，Space 长条（上升）在下
            int modX = panelX + 58;
            Component shiftLabel = mc.options.keyShift.getTranslatedKeyMessage();
            Component spaceLabel = mc.options.keyJump.getTranslatedKeyMessage();
            int shiftCapW = Math.max(14, font.width(shiftLabel) + 6);
            int spaceCapW = panelW - 16 - (modX - innerX); // 填满剩余宽度，形成长条
            drawKeyCapAt(g, shiftLabel, modX, curY,        shiftCapW, kh);
            drawKeyCapAt(g, spaceLabel, modX, curY + step, spaceCapW, kh);
            int ctrlCapW = Math.max(kw, font.width(Component.literal("Ctrl")) + 6);
            drawKeyCapAt(g, Component.literal("Ctrl"), modX, curY + step * 2 + 2, ctrlCapW, kh);
            g.drawString(font, Component.translatable("gui.building_preview.label.boost"), modX + ctrlCapW + 3, curY + step * 2 + 2 + (kh - font.lineHeight) / 2, SimuKraftUiTheme.TEXT_MUTED_COLOR, false);
        }

        curY += sectionH;
        g.fill(innerX, curY, panelX + panelW - 8, curY + 1, 0x33FFFFFF);
        curY += 8;

        // ── 坐标与方块数（自动换行）────────────────────────────
        g.drawCenteredString(font, Component.translatable("gui.building_preview.section.data"), panelX + panelW / 2, curY, 0xFFCC9944);
        curY += titleH;
        for (FormattedCharSequence line : originLines) {
            g.drawString(font, line, innerX, curY, SimuKraftUiTheme.TEXT_SUCCESS_COLOR, false);
            curY += font.lineHeight + 2;
        }
        for (FormattedCharSequence line : blockLines) {
            g.drawString(font, line, innerX, curY, SimuKraftUiTheme.TEXT_INFO_COLOR, false);
            curY += font.lineHeight + 2;
        }

        g.fill(innerX, curY + 2, panelX + panelW - 8, curY + 3, 0x33FFFFFF);
        curY += 8;

        // ── 操作 ───────────────────────────────────────────────
        Component confirmKey = SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_CONFIRM);
        Component cancelKey  = SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_CANCEL);
        int confirmCapW = Math.max(kw, font.width(confirmKey) + 6);
        int cancelCapW  = Math.max(kw, font.width(cancelKey)  + 6);
        drawKeyCapAt(g, confirmKey, innerX, curY, confirmCapW, kh);
        g.drawString(font, Component.translatable("gui.building_preview.action.start"), innerX + confirmCapW + 3, curY + (kh - font.lineHeight) / 2, SimuKraftUiTheme.TEXT_WARNING_COLOR, false);
        curY += kh + 3;
        drawKeyCapAt(g, cancelKey, innerX, curY, cancelCapW, kh);
        g.drawString(font, Component.translatable("gui.building_preview.action.cancel"), innerX + cancelCapW + 3, curY + (kh - font.lineHeight) / 2, SimuKraftUiTheme.TEXT_WARNING_COLOR, false);
        curY += kh + 3;
        Component tabKey = SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_TOGGLE_HUD);
        int tabCapW = Math.max(kw, font.width(tabKey) + 6);
        drawKeyCapAt(g, tabKey, innerX, curY, tabCapW, kh);
        g.drawString(font, Component.translatable("gui.building_preview.action.hide"), innerX + tabCapW + 3, curY + (kh - font.lineHeight) / 2, SimuKraftUiTheme.TEXT_MUTED_COLOR, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_E) {
            replaceWithAir = !replaceWithAir;
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_TOGGLE_HUD, keyCode, scanCode)) {
            panelVisible = !panelVisible;
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_CANCEL, keyCode, scanCode)) {
            exitPreview();
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_FORWARD, keyCode, scanCode)) {
            BuildingPreviewManager.movePreviewRelativeToCamera(0, 1);
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_BACKWARD, keyCode, scanCode)) {
            BuildingPreviewManager.movePreviewRelativeToCamera(0, -1);
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_LEFT, keyCode, scanCode)) {
            BuildingPreviewManager.movePreviewRelativeToCamera(-1, 0);
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_RIGHT, keyCode, scanCode)) {
            BuildingPreviewManager.movePreviewRelativeToCamera(1, 0);
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_UP, keyCode, scanCode)) {
            BuildingPreviewManager.movePreviewVertical(1);
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_DOWN, keyCode, scanCode)) {
            BuildingPreviewManager.movePreviewVertical(-1);
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_ROTATE, keyCode, scanCode)) {
            BuildingPreviewManager.rotatePreview(structure);
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_CONFIRM, keyCode, scanCode)) {
            if (ServerConfig.claimProtectionEnabled() && !BuildingBoundsRenderer.isEntireBuildingInCityTerritory()) {
                if (this.minecraft != null && this.minecraft.player != null) {
                    ClientInfoToast.show(
                            Component.translatable("toast.simukraft.title"),
                            Component.translatable("message.simukraft.construction.outside_city"),
                            "warning"
                    );
                }
                return true;
            }
            startConstruction();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void exitPreview() {
        var minecraft = this.minecraft;
        BuildingPreviewManager.clearPreview();
        FreeCameraManager.deactivate();
        BuildingBoundsRenderer.setPreviewPlayerId(null);
        releasePreviewMouse();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void startConstruction() {
        var minecraft = this.minecraft;
        if (minecraft == null) {
            return;
        }
        PacketDistributor.sendToServer(new BuildBoxStartConstructionPacket(buildBoxPos, building.category(), stripExtension(building.metaFileName()), BuildingPreviewManager.getPreviewOrigin(), BuildingPreviewManager.getRotationDegrees(), replaceWithAir));
        BuildingPreviewManager.clearPreview();
        FreeCameraManager.deactivate();
        BuildingBoundsRenderer.setPreviewPlayerId(null);
        releasePreviewMouse();
        minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private void releasePreviewMouse() {
        if (this.minecraft != null && this.minecraft.mouseHandler.isMouseGrabbed()) {
            this.minecraft.mouseHandler.releaseMouse();
        }
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xAA000000);
        g.fill(x, y, x + w, y + 1,         0x66AAAACC);
        g.fill(x, y, x + 1, y + h,         0x66AAAACC);
        g.fill(x, y + h - 1, x + w, y + h, 0x33444466);
        g.fill(x + w - 1, y, x + w, y + h, 0x33444466);
    }

    private void drawKeyCap(GuiGraphics g, Component label, int cx, int cy, int w, int h) {
        drawKeyCapAt(g, label, cx - w / 2, cy - h / 2, w, h);
    }

    private void drawKeyCapAt(GuiGraphics g, Component label, int x, int y, int w, int h) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xCC1E1E2E);
        g.fill(x, y, x + w, y + 1,         0xFF9999BB);
        g.fill(x, y, x + 1, y + h,         0xFF9999BB);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF333344);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF333344);
        g.drawCenteredString(font, label, x + w / 2, y + (h - font.lineHeight) / 2, 0xFFFFFFFF);
    }
}
