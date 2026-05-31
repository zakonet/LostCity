package client.cn.kafei.simukraft.client.farmland;

import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
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
 * 作业区域设置界面（面向相对 + 自由视角）：
 * - 打开即进入自由视角，可 WASD/空格/Shift 飞行、移动鼠标转向，自由观察地形；结束后恢复玩家视角。
 * - 长 = 沿朝向纵深；宽 = 横向；离盒距离 = 近边离农田盒多少格（默认 1，即盒外一格为底线）；左右 = 横向平移。
 * - 因为自由视角锁定了鼠标，界面用键盘操作，不画暗色背景，世界里的线框预览（复用 BuildingBoundsRenderer）始终可见。
 */
public final class FarmlandAreaScreen extends Screen implements FreeCameraScreen {
    private static final int MAX_LENGTH = 33;
    private static final int MAX_HALF_WIDTH = 16;
    private static final int MAX_GAP = 16;
    private static final int MAX_SIDE = 16;

    private static BlockPos boxPos;
    private static Direction facing = Direction.NORTH;
    private static int length = 7;
    private static int halfWidth = 3;
    private static int gap = 1;
    private static int side;

    private FarmlandAreaScreen() {
        super(Component.translatable("gui.simukraft.farmland_box.area_title"));
    }

    public static void open(FarmlandBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        boxPos = packet.boxPos().immutable();
        facing = minecraft.player != null ? minecraft.player.getDirection() : Direction.NORTH;
        // 重开时保留尺寸，但把位置贴回"朝向、盒外一格"的默认锚点。
        if (packet.hasPlot()) {
            BlockPos min = packet.plotMin();
            BlockPos max = packet.plotMax();
            boolean alongZ = facing.getAxis() == Direction.Axis.Z;
            int alongDim = alongZ ? (max.getZ() - min.getZ() + 1) : (max.getX() - min.getX() + 1);
            int perpDim = alongZ ? (max.getX() - min.getX() + 1) : (max.getZ() - min.getZ() + 1);
            length = clamp(alongDim, 1, MAX_LENGTH);
            halfWidth = clamp((perpDim - 1) / 2, 0, MAX_HALF_WIDTH);
        } else {
            length = 7;
            halfWidth = 3;
        }
        gap = 1;
        side = 0;
        minecraft.execute(() -> minecraft.setScreen(new FarmlandAreaScreen()));
    }

    @Override
    protected void init() {
        super.init();
        refreshPreview();
        FreeCameraManager.activate();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 不调用 super.render，避免暗化/模糊世界，保证线框预览清晰。
        int x = 8;
        int y = 8;
        guiGraphics.drawString(this.font, Component.translatable("gui.simukraft.farmland_box.area_title"), x, y, SimuKraftUiTheme.TEXT_PRIMARY_COLOR);
        y += 14;
        guiGraphics.drawString(this.font, Component.translatable("gui.simukraft.farmland_box.area_values", length, halfWidth * 2 + 1, gap, side), x, y, SimuKraftUiTheme.TEXT_SUCCESS_COLOR);
        y += 12;
        guiGraphics.drawString(this.font, Component.translatable("gui.simukraft.farmland_box.area_fly_hint"), x, y, SimuKraftUiTheme.TEXT_INFO_COLOR);
        y += 12;
        guiGraphics.drawString(this.font, Component.translatable("gui.simukraft.farmland_box.area_controls"), x, y, SimuKraftUiTheme.TEXT_WARNING_COLOR);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                adjust(1, 0, 0, 0);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                adjust(-1, 0, 0, 0);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                adjust(0, -1, 0, 0);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                adjust(0, 1, 0, 0);
                return true;
            }
            case GLFW.GLFW_KEY_E -> {
                adjust(0, 0, 1, 0);
                return true;
            }
            case GLFW.GLFW_KEY_Q -> {
                adjust(0, 0, -1, 0);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> {
                adjust(0, 0, 0, 1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT_BRACKET -> {
                adjust(0, 0, 0, -1);
                return true;
            }
            case GLFW.GLFW_KEY_R -> {
                // 把作业区域对齐到当前相机朝向，方便飞到合适角度后一键摆正。
                facing = Direction.fromYRot(FreeCameraManager.getYaw());
                refreshPreview();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                confirm();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                cancel();
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        FreeCameraManager.deactivate();
        clearPreview();
        // 退出自由视角后释放鼠标抓取，避免回到界面/游戏时光标卡住。
        if (this.minecraft != null && this.minecraft.mouseHandler.isMouseGrabbed()) {
            this.minecraft.mouseHandler.releaseMouse();
        }
    }

    // 由朝向、长、宽、离盒距离、左右平移算出世界中的矩形 min/max（Y 取农田盒所在层）。
    private static BlockPos[] computeBounds() {
        int ax = facing.getStepX();
        int az = facing.getStepZ();
        int px = -az; // 朝向顺时针旋转 90° 得到横向单位向量
        int pz = ax;
        int boxX = boxPos.getX();
        int boxZ = boxPos.getZ();
        int y = boxPos.getY();
        int nearX = boxX + ax * gap + px * side;
        int nearZ = boxZ + az * gap + pz * side;
        int farX = nearX + ax * (length - 1);
        int farZ = nearZ + az * (length - 1);
        int[] xs = {nearX + px * halfWidth, nearX - px * halfWidth, farX + px * halfWidth, farX - px * halfWidth};
        int[] zs = {nearZ + pz * halfWidth, nearZ - pz * halfWidth, farZ + pz * halfWidth, farZ - pz * halfWidth};
        int minX = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
        int maxX = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
        int minZ = Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3]));
        int maxZ = Math.max(Math.max(zs[0], zs[1]), Math.max(zs[2], zs[3]));
        return new BlockPos[]{new BlockPos(minX, y, minZ), new BlockPos(maxX, y, maxZ)};
    }

    private static void refreshPreview() {
        BlockPos[] bounds = computeBounds();
        AABB box = new AABB(bounds[0].getX(), bounds[0].getY(), bounds[0].getZ(),
                bounds[1].getX() + 1, bounds[1].getY() + 1, bounds[1].getZ() + 1);
        BuildingBoundsRenderer.setBuildingBoundsVisible(boxPos, box, true);
    }

    private static void clearPreview() {
        if (boxPos != null) {
            BuildingBoundsRenderer.setBuildingBoundsVisible(boxPos, null, false);
        }
    }

    private static void adjust(int dLength, int dHalfWidth, int dGap, int dSide) {
        length = clamp(length + dLength, 1, MAX_LENGTH);
        halfWidth = clamp(halfWidth + dHalfWidth, 0, MAX_HALF_WIDTH);
        gap = clamp(gap + dGap, 1, MAX_GAP);
        side = clamp(side + dSide, -MAX_SIDE, MAX_SIDE);
        refreshPreview();
    }

    private void confirm() {
        BlockPos targetBox = boxPos;
        BlockPos[] bounds = computeBounds();
        // 服务端回包会自动重开农田盒主界面；removed() 负责退出自由视角并清除预览。
        PacketDistributor.sendToServer(new FarmlandBoxSetAreaPacket(targetBox, bounds[0], bounds[1]));
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    private void cancel() {
        BlockPos targetBox = boxPos;
        // ESC/取消：丢弃未保存的选区（不发送设置区域包），并立即清除候选线框预览。
        clearPreview();
        PacketDistributor.sendToServer(new FarmlandBoxOpenRequestPacket(targetBox));
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
