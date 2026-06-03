package client.cn.kafei.simukraft.client.city.map;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 地图 region 的本地磁盘缓存。
 */
public class SimuMapStorage {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int MAGIC = 0x534D5200;
    private static final short VERSION = 1;
    private static final String ROOT_DIR = "simukraft_mapdata";

    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SimuMap-Save");
        thread.setDaemon(true);
        return thread;
    });

    private static final ExecutorService LOAD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SimuMap-Load");
        thread.setDaemon(true);
        return thread;
    });

    private SimuMapStorage() {
    }

    /**
     * 单人使用存档名，多人使用服务器地址，避免不同世界互相覆盖缓存。
     */
    public static String getCurrentWorldId() {
        Minecraft mc = Minecraft.getInstance();
        var singleplayerServer = mc.getSingleplayerServer();
        if (singleplayerServer != null) {
            return sanitize(singleplayerServer.getWorldData().getLevelName());
        }
        var currentServer = mc.getCurrentServer();
        if (currentServer != null) {
            return "mp_" + sanitize(currentServer.ip);
        }
        return "unknown";
    }

    public static String dimensionToDir(ResourceKey<Level> dimension) {
        return sanitize(dimension.location().getNamespace() + "_" + dimension.location().getPath());
    }

    public static Path getRegionDir(String worldId, ResourceKey<Level> dimension) {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        return gameDir.resolve(ROOT_DIR).resolve(worldId).resolve(dimensionToDir(dimension));
    }

    public static Path getRegionFile(String worldId, ResourceKey<Level> dimension, int regionX, int regionZ) {
        return getRegionDir(worldId, dimension).resolve(regionX + "_" + regionZ + ".smr");
    }

    public static void saveRegion(String worldId, ResourceKey<Level> dimension, SimuMapRegion region) {
        SimuMapRegionData data = region.getData();
        if (data == null || data.isEmpty()) {
            return;
        }

        Path file = getRegionFile(worldId, dimension, region.regionX, region.regionZ);
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
                out.writeInt(MAGIC);
                out.writeShort(VERSION);
                for (short height : data.height) {
                    out.writeShort(height);
                }
                for (int color : data.color) {
                    out.writeInt(color);
                }
                for (short flags : data.flags) {
                    out.writeShort(flags);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Simukraft: Failed to save map region ({}, {}) for world={} dim={}",
                    region.regionX, region.regionZ, worldId, dimensionToDir(dimension), e);
        }
    }

    public static void saveAll(String worldId, ResourceKey<Level> dimension, Collection<SimuMapRegion> regions) {
        for (SimuMapRegion region : regions) {
            saveRegion(worldId, dimension, region);
        }
        LOGGER.debug("Simukraft: Saved {} regions for world={} dim={}",
                regions.size(), worldId, dimensionToDir(dimension));
    }

    public static void saveAllAsync(String worldId, ResourceKey<Level> dimension,
                                    Collection<SimuMapRegion> regions, String reason) {
        saveAllAsync(worldId, dimension, regions, reason, true);
    }

    /**
     * `discardAfterSave=false` 用于周期缓存，保留内存数据继续渲染未加载区块。
     */
    public static void saveAllAsync(String worldId, ResourceKey<Level> dimension,
                                    Collection<SimuMapRegion> regions, String reason,
                                    boolean discardAfterSave) {
        List<SimuMapRegion> regionSnapshot = new ArrayList<>(regions);
        if (regionSnapshot.isEmpty()) {
            return;
        }

        SAVE_EXECUTOR.execute(() -> {
            saveAll(worldId, dimension, regionSnapshot);
            if (discardAfterSave) {
                for (SimuMapRegion region : regionSnapshot) {
                    region.discardData();
                }
            }
            LOGGER.info("Simukraft: Async-saved {} regions for world={} dim={} reason={} discardAfterSave={}",
                    regionSnapshot.size(), worldId, dimensionToDir(dimension), reason, discardAfterSave);
        });
    }

    public static void loadAll(String worldId, ResourceKey<Level> dimension, Map<Long, SimuMapRegion> regions) {
        Path dir = getRegionDir(worldId, dimension);
        if (!Files.isDirectory(dir)) {
            return;
        }

        try (var stream = Files.list(dir)) {
            stream.filter(path -> path.toString().endsWith(".smr")).forEach(file -> {
                String name = file.getFileName().toString();
                name = name.substring(0, name.length() - 4);
                String[] parts = name.split("_", 2);
                if (parts.length != 2) {
                    return;
                }

                try {
                    int regionX = Integer.parseInt(parts[0]);
                    int regionZ = Integer.parseInt(parts[1]);
                    SimuMapRegionData data = readRegionFile(file);
                    if (data == null) {
                        return;
                    }

                    SimuMapRegion region = new SimuMapRegion(regionX, regionZ);
                    region.setData(data);
                    data.markDirty();
                    regions.put(regionKey(regionX, regionZ), region);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Simukraft: Skipping malformed region file: {}", file.getFileName());
                }
            });
        } catch (IOException e) {
            LOGGER.error("Simukraft: Failed to list region files for world={} dim={}",
                    worldId, dimensionToDir(dimension), e);
        }

        LOGGER.debug("Simukraft: Loaded {} regions from world={} dim={}",
                regions.size(), worldId, dimensionToDir(dimension));
    }

    public static void loadAllAsync(String worldId, ResourceKey<Level> dimension, Map<Long, SimuMapRegion> regions) {
        LOAD_EXECUTOR.execute(() -> loadAll(worldId, dimension, regions));
    }

    public static void loadAllAsync(String worldId, ResourceKey<Level> dimension,
                                    Consumer<Map<Long, SimuMapRegion>> callback) {
        LOAD_EXECUTOR.execute(() -> {
            Map<Long, SimuMapRegion> loadedRegions = new ConcurrentHashMap<>();
            loadAll(worldId, dimension, loadedRegions);
            callback.accept(loadedRegions);
        });
    }

    private static SimuMapRegionData readRegionFile(Path file) {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                LOGGER.warn("Simukraft: Invalid magic in {}", file.getFileName());
                return null;
            }

            short version = in.readShort();
            if (version != VERSION) {
                LOGGER.warn("Simukraft: Unsupported version {} in {}", version, file.getFileName());
                return null;
            }

            String name = file.getFileName().toString();
            name = name.substring(0, name.length() - 4);
            String[] parts = name.split("_", 2);
            int regionX = Integer.parseInt(parts[0]);
            int regionZ = Integer.parseInt(parts[1]);

            SimuMapRegionData data = new SimuMapRegionData(regionX, regionZ);
            for (int i = 0; i < SimuMapRegionData.AREA; i++) {
                data.height[i] = in.readShort();
            }
            for (int i = 0; i < SimuMapRegionData.AREA; i++) {
                data.color[i] = in.readInt();
            }
            for (int i = 0; i < SimuMapRegionData.AREA; i++) {
                data.flags[i] = in.readShort();
            }
            return data;
        } catch (IOException e) {
            LOGGER.error("Simukraft: Failed to read region file {}", file.getFileName(), e);
            return null;
        }
    }

    private static long regionKey(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
