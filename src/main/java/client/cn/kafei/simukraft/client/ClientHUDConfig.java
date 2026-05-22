package client.cn.kafei.simukraft.client;

public final class ClientHUDConfig {
    private static final Anchor DEFAULT_ANCHOR = Anchor.TOP_RIGHT;
    private static final int DEFAULT_POS_X = -5;
    private static final int DEFAULT_POS_Y = 5;

    private static Anchor anchor = DEFAULT_ANCHOR;
    private static int posX = DEFAULT_POS_X;
    private static int posY = DEFAULT_POS_Y;

    public enum Anchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        TOP_CENTER,
        BOTTOM_CENTER
    }

    private ClientHUDConfig() {
    }

    public static synchronized Anchor getAnchor() {
        return anchor;
    }

    public static synchronized void setAnchor(Anchor newAnchor) {
        anchor = newAnchor != null ? newAnchor : DEFAULT_ANCHOR;
    }

    public static synchronized int getPosX() {
        return posX;
    }

    public static synchronized void setPosX(int newPosX) {
        posX = newPosX;
    }

    public static synchronized int getPosY() {
        return posY;
    }

    public static synchronized void setPosY(int newPosY) {
        posY = newPosY;
    }

    public static int[] calculatePosition(int screenWidth, int screenHeight, int textWidth) {
        Anchor currentAnchor;
        int currentPosX;
        int currentPosY;
        synchronized (ClientHUDConfig.class) {
            currentAnchor = anchor;
            currentPosX = posX;
            currentPosY = posY;
        }

        int x;
        int y;
        switch (currentAnchor) {
            case TOP_LEFT -> {
                x = currentPosX;
                y = currentPosY;
            }
            case TOP_RIGHT -> {
                x = screenWidth - textWidth + currentPosX;
                y = currentPosY;
            }
            case BOTTOM_LEFT -> {
                x = currentPosX;
                y = screenHeight - 10 + currentPosY;
            }
            case BOTTOM_RIGHT -> {
                x = screenWidth - textWidth + currentPosX;
                y = screenHeight - 10 + currentPosY;
            }
            case TOP_CENTER -> {
                x = (screenWidth - textWidth) / 2 + currentPosX;
                y = currentPosY;
            }
            case BOTTOM_CENTER -> {
                x = (screenWidth - textWidth) / 2 + currentPosX;
                y = screenHeight - 10 + currentPosY;
            }
            default -> {
                x = screenWidth - textWidth - 5;
                y = 5;
            }
        }
        return new int[]{x, y};
    }

    public static synchronized void reset() {
        anchor = DEFAULT_ANCHOR;
        posX = DEFAULT_POS_X;
        posY = DEFAULT_POS_Y;
    }
}
