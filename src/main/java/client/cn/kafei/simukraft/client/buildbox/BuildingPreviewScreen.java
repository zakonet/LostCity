package client.cn.kafei.simukraft.client.buildbox;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraScreen;
import client.cn.kafei.simukraft.client.input.SimuKraftKeyMappings;
import client.cn.kafei.simukraft.client.toast.ClientInfoToast;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import client.cn.kafei.simukraft.client.ui.SlidingInfoPanel;
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

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class BuildingPreviewScreen extends Screen implements FreeCameraScreen {
    private final Screen parent;
    private final BuildingCacheService.BuildingMeta building;
    private final BlockPos buildBoxPos;
    private final BuildingStructure structure;

    private final SlidingInfoPanel panel = new SlidingInfoPanel();
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
        // ── 顶部标题条 ──
        g.fill(0, 0, sw, 18, 0xAA000000);
        g.fill(0, 17, sw, 18, 0x55AAAACC);
        g.renderItem(new ItemStack(Items.BARRIER), 2, 1);
        Component modeText = Component.translatable(replaceWithAir
                ? "gui.building_preview.mode.replace_air" : "gui.building_preview.mode.keep_obstacles");
        int modeColor = replaceWithAir ? SimuKraftUiTheme.TEXT_SUCCESS_COLOR : SimuKraftUiTheme.TEXT_MUTED_COLOR;
        Component eKey = Component.literal("E");
        int eCapW = Math.max(10, font.width(eKey) + 6);
        panel.drawKeyCapAt(g, eKey, 22, 4, eCapW, 10);
        g.drawString(font, modeText, 22 + eCapW + 3, 4 + (10 - font.lineHeight) / 2, modeColor, false);
        g.drawCenteredString(font,
                Component.translatable("gui.building_preview.title_with_name", building.name()),
                sw / 2, 5, SimuKraftUiTheme.TEXT_PRIMARY_COLOR);
        // ── 滑动面板 ──
        panel.beginRender(g, font, sw, sh, 18);
        int kw = 10, kh = 10, step = 11;
        int pX = panel.getPanelX(), pW = panel.getPanelW(), iX = panel.getInnerX();
        int curY = 18 + 7;
        // ── 预览移动（方向键）──
        panel.drawSectionTitle(g, Component.translatable("gui.building_preview.section.preview_move"), curY);
        curY += font.lineHeight + 8;
        int arrowCX = pX + 30;
        panel.drawKeyCap(g, Component.literal("↑"), arrowCX,        curY,        kw, kh);
        panel.drawKeyCap(g, Component.literal("←"), arrowCX - step, curY + step, kw, kh);
        panel.drawKeyCap(g, Component.literal("↓"), arrowCX,        curY + step, kw, kh);
        panel.drawKeyCap(g, Component.literal("→"), arrowCX + step, curY + step, kw, kh);
        g.drawCenteredString(font, Component.translatable("gui.building_preview.label.move"),
                arrowCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);
        int heightCX = pX + 62;
        panel.drawKeyCap(g, Component.literal("+"), heightCX, curY,        kw, kh);
        panel.drawKeyCap(g, Component.literal("-"), heightCX, curY + step, kw, kh);
        g.drawCenteredString(font, Component.translatable("gui.building_preview.label.height"),
                heightCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);
        int rotateCX = pX + 86;
        panel.drawKeyCap(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_ROTATE),
                rotateCX, curY + step / 2, kw, kh);
        g.drawCenteredString(font, Component.translatable("gui.building_preview.label.rotate"),
                rotateCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);
        curY += step * 2 + font.lineHeight + 5;
        panel.drawSeparator(g, curY); curY += 8;
        // ── 摄像机移动 ──
        panel.drawSectionTitle(g, Component.translatable("gui.building_preview.section.camera"), curY);
        curY += font.lineHeight + 8;
        var mc = this.minecraft;
        if (mc != null) {
            int wasdCX = pX + 30;
            panel.drawKeyCap(g, mc.options.keyUp.getTranslatedKeyMessage(),    wasdCX,        curY,        kw, kh);
            panel.drawKeyCap(g, mc.options.keyLeft.getTranslatedKeyMessage(),  wasdCX - step, curY + step, kw, kh);
            panel.drawKeyCap(g, mc.options.keyDown.getTranslatedKeyMessage(),  wasdCX,        curY + step, kw, kh);
            panel.drawKeyCap(g, mc.options.keyRight.getTranslatedKeyMessage(), wasdCX + step, curY + step, kw, kh);
            g.drawCenteredString(font, Component.translatable("gui.building_preview.label.move"),
                    wasdCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);
            int modX = pX + 58;
            int shiftCapW = Math.max(14, font.width(mc.options.keyShift.getTranslatedKeyMessage()) + 6);
            int spaceCapW = pW - 16 - (modX - iX);
            panel.drawKeyCapAt(g, mc.options.keyShift.getTranslatedKeyMessage(), modX, curY,        shiftCapW, kh);
            panel.drawKeyCapAt(g, mc.options.keyJump.getTranslatedKeyMessage(),  modX, curY + step, spaceCapW, kh);
            int ctrlCapW = Math.max(kw, font.width(Component.literal("Ctrl")) + 6);
            panel.drawKeyCapAt(g, Component.literal("Ctrl"), modX, curY + step * 2 + 2, ctrlCapW, kh);
            g.drawString(font, Component.translatable("gui.building_preview.label.boost"),
                    modX + ctrlCapW + 3, curY + step * 2 + 2 + (kh - font.lineHeight) / 2,
                    SimuKraftUiTheme.TEXT_MUTED_COLOR, false);
        }
        curY += step * 2 + font.lineHeight + 5;
        panel.drawSeparator(g, curY); curY += 8;
        // ── 坐标与方块数 ──
        panel.drawSectionTitle(g, Component.translatable("gui.building_preview.section.data"), curY);
        curY += font.lineHeight + 8;
        BlockPos origin = BuildingPreviewManager.getPreviewOrigin();
        Component originText = Component.translatable("gui.building_preview.origin_info",
                origin.getX(), origin.getY(), origin.getZ(), BuildingPreviewManager.getRotationDegrees());
        Component blockText = Component.translatable("gui.building_preview.block_count", structure.blockCount());
        for (FormattedCharSequence line : font.split(originText, pW - 16)) {
            g.drawString(font, line, iX, curY, SimuKraftUiTheme.TEXT_SUCCESS_COLOR, false);
            curY += font.lineHeight + 2;
        }
        for (FormattedCharSequence line : font.split(blockText, pW - 16)) {
            g.drawString(font, line, iX, curY, SimuKraftUiTheme.TEXT_INFO_COLOR, false);
            curY += font.lineHeight + 2;
        }
        panel.drawSeparator(g, curY); curY += 8;
        // ── 操作键 ──
        curY = panel.drawKeyAction(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_CONFIRM),
                iX, curY, kh, Component.translatable("gui.building_preview.action.start"),
                SimuKraftUiTheme.TEXT_WARNING_COLOR);
        curY = panel.drawKeyAction(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_CANCEL),
                iX, curY, kh, Component.translatable("gui.building_preview.action.cancel"),
                SimuKraftUiTheme.TEXT_WARNING_COLOR);
        panel.drawKeyAction(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_TOGGLE_HUD),
                iX, curY, kh, Component.translatable("gui.building_preview.action.hide"),
                SimuKraftUiTheme.TEXT_MUTED_COLOR);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_E) {
            replaceWithAir = !replaceWithAir;
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_TOGGLE_HUD, keyCode, scanCode)) {
            panel.toggle();
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
}
