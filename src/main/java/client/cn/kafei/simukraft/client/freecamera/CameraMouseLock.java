package client.cn.kafei.simukraft.client.freecamera;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class CameraMouseLock {
    private static boolean locked;

    private CameraMouseLock() {
    }

    public static void setLocked(boolean locked) {
        CameraMouseLock.locked = locked;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> {
            long window = minecraft.getWindow().getWindow();
            if (window == 0L) {
                return;
            }
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, locked ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL);
        });
    }

    public static boolean isLocked() {
        return locked;
    }
}
