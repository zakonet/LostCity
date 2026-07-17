package client.cn.kafei.simukraft.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 通用右侧滑动信息面板，供各自由视角操作界面复用。
 * 使用方式：声明实例字段，render() 中调用 beginRender()，keyPressed() 中调用 toggle()。
 */
@OnlyIn(Dist.CLIENT)
public final class SlidingInfoPanel {

    private static final int PANEL_W = 132;

    private boolean visible = true;
    private float slideProgress = 1.0f;

    // beginRender() 后有效的布局值
    private Font font;
    private int panelX, panelY, panelW, panelH, innerX;

    /** 切换面板显隐。 */
    public void toggle() {
        visible = !visible;
    }

    public boolean isVisible() {
        return visible;
    }

    /**
     * 每帧调用：更新滑动动画并绘制面板框（拉片 + 背景）。
     * @param panelTop 面板顶部 Y（通常为标题栏底部，如 18）
     */
    public void beginRender(GuiGraphics g, Font font, int screenW, int screenH, int panelTop) {
        this.font  = font;
        this.panelW = PANEL_W;
        this.panelY = panelTop;
        this.panelH = screenH - panelTop;
        float slideTarget = visible ? 1.0f : 0.0f;
        float delta = slideTarget - slideProgress;
        slideProgress = Math.abs(delta) > 0.004f ? slideProgress + delta * 0.2f : slideTarget;
        panelX = screenW - (int)(slideProgress * (panelW + 6));
        innerX = panelX + 8;
        // 拉片
        int tabX = panelX - 12, tabY = panelY + panelH / 2 - 8;
        g.fill(tabX, tabY, tabX + 12, tabY + 16, 0xAA000000);
        g.fill(tabX, tabY, tabX + 12, tabY + 1,  0x44AAAACC);
        g.fill(tabX, tabY, tabX + 1,  tabY + 16, 0x44AAAACC);
        g.drawCenteredString(font, Component.literal(visible ? "▶" : "◀"), tabX + 6, tabY + 4, 0xFF8888AA);
        // 面板背景
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xAA000000);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1,           0x66AAAACC);
        g.fill(panelX, panelY, panelX + 1,      panelY + panelH,      0x66AAAACC);
        g.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0x33444466);
        g.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0x33444466);
    }

    // ── 布局访问器 ─────────────────────────────────────────────

    public int getPanelX()    { return panelX; }
    public int getInnerX()    { return innerX; }
    public int getPanelW()    { return panelW; }
    public int getCenterX()   { return panelX + panelW / 2; }
    public int getInnerRight(){ return panelX + panelW - 8; }

    // ── 绘制辅助 ───────────────────────────────────────────────

    /** 绘制橙色区段标题。 */
    public void drawSectionTitle(GuiGraphics g, Component text, int y) {
        g.drawCenteredString(font, text, getCenterX(), y, 0xFFCC9944);
    }

    /** 绘制水平分隔线。 */
    public void drawSeparator(GuiGraphics g, int y) {
        g.fill(innerX, y, getInnerRight(), y + 1, 0x33FFFFFF);
    }

    /** 绘制以 (cx,cy) 为中心的键帽。 */
    public void drawKeyCap(GuiGraphics g, Component label, int cx, int cy, int w, int h) {
        drawKeyCapAt(g, label, cx - w / 2, cy - h / 2, w, h);
    }

    /** 绘制以 (x,y) 为左上角的键帽。 */
    public void drawKeyCapAt(GuiGraphics g, Component label, int x, int y, int w, int h) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xCC1E1E2E);
        g.fill(x, y, x + w, y + 1,         0xFF9999BB);
        g.fill(x, y, x + 1, y + h,         0xFF9999BB);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF333344);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF333344);
        g.drawCenteredString(font, label, x + w / 2, y + (h - font.lineHeight) / 2, 0xFFFFFFFF);
    }

    /**
     * 绘制一行"键帽 + 动作说明"：键帽在左，说明紧跟右侧。
     * @return 下一行的 Y 坐标（curY + kh + 3）
     */
    public int drawKeyAction(GuiGraphics g, Component key, int x, int y,
                             int kh, Component label, int labelColor) {
        int kw = Math.max(10, font.width(key) + 6);
        drawKeyCapAt(g, key, x, y, kw, kh);
        g.drawString(font, label, x + kw + 3, y + (kh - font.lineHeight) / 2, labelColor, false);
        return y + kh + 3;
    }
}