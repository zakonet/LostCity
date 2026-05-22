package common.cn.kafei.simukraft.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

@SuppressWarnings("null")
public final class SaveScopedCacheKey {
    private SaveScopedCacheKey() {
    }

    // 获取当前存档根目录标识，用于阻止静态缓存跨存档复用。
    public static String serverKey(MinecraftServer server) {
        if (server == null) {
            return "unknown_server";
        }
        try {
            Path worldPath = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            return worldPath.toString();
        } catch (RuntimeException exception) {
            return "server@" + Integer.toHexString(System.identityHashCode(server));
        }
    }

    // 获取存档加维度标识，用于维度级运行时缓存隔离。
    public static String levelKey(ServerLevel level) {
        if (level == null) {
            return "unknown_server|unknown_dimension";
        }
        return serverKey(level.getServer()) + "|" + level.dimension().location();
    }

    // 获取玩家在当前存档内的标识，用于 HUD 等玩家级缓存隔离。
    public static String playerKey(ServerPlayer player) {
        if (player == null) {
            return "unknown_server|unknown_player";
        }
        return serverKey(player.getServer()) + "|player=" + player.getUUID();
    }
}
