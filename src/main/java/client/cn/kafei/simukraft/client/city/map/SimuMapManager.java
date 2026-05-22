package client.cn.kafei.simukraft.client.city.map;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simukraft 鍦板浘绠＄悊鍣ㄣ€?
 * 绠＄悊鎵€鏈夊尯鍩?({@link SimuMapRegion}) 鐨勫垱寤恒€佹壂鎻忋€佹覆鏌撳拰鐢熷懡鍛ㄦ湡銆?
 * 鍙傝€?FTB Chunks 鐨?MapManager锛屼絾瀹屽叏鐙珛銆侀浂澶栭儴妯＄粍渚濊禆銆?
 *
 * <p>鐢熷懡鍛ㄦ湡:</p>
 * <ol>
 *   <li>{@link #init()} - 瀹㈡埛绔姞鍏ヤ笘鐣屾椂璋冪敤锛岃嚜鍔ㄤ粠纾佺洏鎭㈠褰撳墠瀛樻。+缁村害鐨勫巻鍙叉暟鎹?/li>
 *   <li>{@link #tick()} - 姣忓鎴风 tick 璋冪敤锛屾墽琛屽閲忔壂鎻忓拰娓叉煋锛涙娴嬬淮搴﹀垏鎹㈡椂鑷姩淇濆瓨/鍔犺浇鏁版嵁</li>
 *   <li>{@link #shutdown()} - 瀹㈡埛绔寮€涓栫晫鏃惰皟鐢紝灏嗗綋鍓嶆暟鎹啓鍏ョ鐩?/li>
 * </ol>
 *
 * <p>鎸佷箙鍖栫瓥鐣ワ細鎸?{@code <瀛樻。鏍囪瘑>/<缁村害>} 鍒嗙洰褰曞瓨鍌紝姣忎釜 region 瀵瑰簲涓€涓?.smr 鏂囦欢锛?
 * 鐢?{@link SimuMapStorage} 缁熶竴璐熻矗璇诲啓銆?/p>
 */
public class SimuMapManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static SimuMapManager instance;

    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SimuMap-Render");
        t.setDaemon(true);
        return t;
    });

    // region 数据按 512x512 方块分片管理，只保留近期访问区域以控制显存/内存。
    private final Map<Long, SimuMapRegion> regions = new ConcurrentHashMap<>();
    // 防止同一个 region 在上一次异步渲染完成前重复提交。
    private final Set<Long> renderingKeys = ConcurrentHashMap.newKeySet();

    private int scanRadius = 12;
    private int chunksPerTick = 4;
    private long tickCount = 0;

    private int scanCursorDX = 0;
    private int scanCursorDZ = 0;
    private int scanSpiralLeg = 1;
    private int scanSpiralStep = 0;
    private int scanSpiralDirection = 0;

    private boolean initialized = false;
    private int loadGeneration = 0;

    /**
     * 褰撳墠宸插垵濮嬪寲鏃舵墍鍦ㄧ殑缁村害 key锛岀敤浜庢娴嬬淮搴?涓栫晫鍒囨崲銆?
     */
    @Nullable
    private ResourceKey<Level> currentDimension = null;

    /**
     * 褰撳墠瀛樻。鐨勫敮涓€鏍囪瘑瀛楃涓诧紝鐢?{@link SimuMapStorage#getCurrentWorldId()} 鎻愪緵銆?
     * 鍦?{@link #init()} 鏃惰缃紝{@link #shutdown()} 鏃舵竻绌恒€?
     */
    @Nullable
    private String currentWorldId = null;

    /**
     * 娲昏穬娑堣垂鑰呰鏁板櫒銆?
     * 澶т簬 0 鏃惰〃绀烘湁鍦板浘鐣岄潰澶勪簬鎵撳紑鐘舵€侊紝{@link #tick()} 鎵嶆墽琛屾壂鎻忓拰娓叉煋銆?
     */
    private int activeConsumers = 0;

    private SimuMapManager() {
    }

    /**
     * 娉ㄥ唽涓€涓椿璺冩秷璐硅€咃紙鍦板浘鐣岄潰鎵撳紑鏃惰皟鐢級銆?
     */
    public void acquireConsumer() {
        activeConsumers++;
    }

    /**
     * 娉ㄩ攢涓€涓椿璺冩秷璐硅€咃紙鍦板浘鐣岄潰鍏抽棴鏃惰皟鐢級銆?
     */
    public void releaseConsumer() {
        activeConsumers = Math.max(0, activeConsumers - 1);
    }

    /**
     * 鏄惁瀛樺湪娲昏穬娑堣垂鑰呫€?
     */
    public boolean hasActiveConsumer() {
        return activeConsumers > 0;
    }

    /**
     * 鑾峰彇鍗曚緥銆?
     */
    public static SimuMapManager getInstance() {
        if (instance == null) {
            instance = new SimuMapManager();
        }
        return instance;
    }

    /**
     * 妫€鏌ュ湴鍥剧郴缁熸槸鍚﹀凡鍒濆鍖栥€?
     */
    public static boolean isAvailable() {
        return instance != null && instance.initialized;
    }

    // 仅关闭已存在的地图管理器，退出存档时保存并释放当前地图缓存。
    public static void shutdownIfPresent() {
        if (instance != null) {
            instance.shutdown();
        }
    }

    /**
     * 鍒濆鍖栧湴鍥剧郴缁燂紝骞朵粠纾佺洏鎭㈠褰撳墠瀛樻。+缁村害鐨勫巻鍙叉壂鎻忔暟鎹€?
     */
    public void init() {
        if (initialized) return;
        initialized = true;

        SimuBlockColors.getInstance().init();
        resetScanCursor();

        Minecraft mc = Minecraft.getInstance();
        currentWorldId = SimuMapStorage.getCurrentWorldId();
        Level level = mc.level;
        if (level != null) {
            currentDimension = level.dimension();
        } else {
            currentDimension = null;
        }

        if (currentDimension != null) {
            queueRegionLoad(currentWorldId, currentDimension);
            LOGGER.info("Simukraft: Map system initialization queued for world={} dim={}.",
                    currentWorldId, SimuMapStorage.dimensionToDir(currentDimension));
        } else {
            LOGGER.info("Simukraft: Map system initialized (dimension not yet known).");
        }
    }

    /**
     * 鍏抽棴鍦板浘绯荤粺锛氬皢褰撳墠鏁版嵁鍐欏叆纾佺洏锛岀劧鍚庨噴鏀炬墍鏈夊唴瀛樿祫婧愩€?
     */
    public void shutdown() {
        if (!initialized) return;
        initialized = false;

        persistRegionsAsync(currentWorldId, currentDimension, List.copyOf(regions.values()), "shutdown");

        currentDimension = null;
        currentWorldId = null;
        loadGeneration++;

        regions.clear();
        renderingKeys.clear();
        renderExecutor.shutdownNow();

        instance = null;
        LOGGER.info("Simukraft: Map rendering system shut down.");
    }

    /**
     * 鑾峰彇鎴栧垱寤烘寚瀹氬潗鏍囩殑鍖哄煙銆?
     */
    public SimuMapRegion getOrCreateRegion(int regionX, int regionZ) {
        long key = regionKey(regionX, regionZ);
        return regions.computeIfAbsent(key, k -> new SimuMapRegion(regionX, regionZ));
    }

    /**
     * 鑾峰彇鍖哄煙锛堝彲鑳戒负 null锛夈€?
     */
    @Nullable
    public SimuMapRegion getRegion(int regionX, int regionZ) {
        return regions.get(regionKey(regionX, regionZ));
    }

    /**
     * 鑾峰彇鎸囧畾鑼冨洿鍐呯殑鎵€鏈夊尯鍩熴€?
     */
    public Collection<SimuMapRegion> getAllRegions() {
        return regions.values();
    }

    /**
     * 姣?tick 璋冪敤锛氭墽琛屽閲忓尯鍧楁壂鎻忓拰鑴忓尯鍩熸覆鏌撱€?
     */
    public void tick() {
        if (!initialized) return;
        if (activeConsumers <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        ResourceKey<Level> dim = level.dimension();
        String worldId = SimuMapStorage.getCurrentWorldId();
        ResourceKey<Level> previousDimension = currentDimension;
        String previousWorldId = currentWorldId;
        if (!worldId.equals(previousWorldId) || !dim.equals(previousDimension)) {
            // 进入新存档或新维度时先保存旧 region，再清空内存缓存。
            if (previousWorldId != null && previousDimension != null) {
                persistRegionsAsync(previousWorldId, previousDimension, List.copyOf(regions.values()), "world_or_dimension_change");
                LOGGER.info("Simukraft: Map scope changed from world={} dim={} to world={} dim={}, queued async save for {} regions.",
                        previousWorldId, SimuMapStorage.dimensionToDir(previousDimension), worldId, SimuMapStorage.dimensionToDir(dim), regions.size());
                regions.clear();
                renderingKeys.clear();
                resetScanCursor();
            } else if (previousDimension == null) {
                LOGGER.info("Simukraft: First dimension acquired: {}.", SimuMapStorage.dimensionToDir(dim));
            }
            currentWorldId = worldId;
            currentDimension = dim;
            queueRegionLoad(currentWorldId, currentDimension);
        }

        tickCount++;

        if (tickCount % 2 == 0) {
            incrementalScan();
        }

        if (tickCount % 10 == 0) {
            renderDirtyRegions();
        }

        if (tickCount % 600 == 0) {
            releaseStaleRegions(60_000L);
        }
    }

    /**
     * 寮哄埗鎵弿鎸囧畾鑼冨洿鍐呯殑鎵€鏈夊尯鍧椼€?
     */
    public void forceScanArea(int centerChunkX, int centerChunkZ, int radius) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int cx = centerChunkX + dx;
                int cz = centerChunkZ + dz;

                if (!level.hasChunk(cx, cz)) continue;

                int regionX = cx >> 5;
                int regionZ = cz >> 5;
                SimuMapRegion region = getOrCreateRegion(regionX, regionZ);

                try {
                    SimuChunkScanner.scanChunk(cx, cz, region);
                } catch (Exception e) {
                    LOGGER.debug("Simukraft: Force scan failed for ({}, {}): {}", cx, cz, e.getMessage());
                }
            }
        }
    }

    /**
     * 寮哄埗閲嶆柊娓叉煋鎵€鏈夊凡鍔犺浇鍖哄煙銆?
     */
    public void forceRenderAll() {
        for (SimuMapRegion region : regions.values()) {
            SimuMapRegionData data = region.getData();
            if (data != null) {
                data.markDirty();
            }
        }
        renderDirtyRegions();
    }

    private void incrementalScan() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        int playerCX = player.chunkPosition().x;
        int playerCZ = player.chunkPosition().z;

        int scanned = 0;
        int maxAttempts = chunksPerTick * 4;
        int attempts = 0;

        while (scanned < chunksPerTick && attempts < maxAttempts) {
            int cx = playerCX + scanCursorDX;
            int cz = playerCZ + scanCursorDZ;

            advanceSpiralCursor();
            attempts++;

            if (Math.abs(scanCursorDX) > scanRadius || Math.abs(scanCursorDZ) > scanRadius) {
                resetScanCursor();
                break;
            }

            if (!level.hasChunk(cx, cz)) continue;

            int regionX = cx >> 5;
            int regionZ = cz >> 5;
            SimuMapRegion region = getOrCreateRegion(regionX, regionZ);

            try {
                if (SimuChunkScanner.scanChunk(cx, cz, region)) {
                    scanned++;
                }
            } catch (Exception e) {
                LOGGER.debug("Simukraft: Incremental scan failed for ({}, {})", cx, cz);
            }
        }
    }

    private void advanceSpiralCursor() {
        switch (scanSpiralDirection) {
            case 0 -> scanCursorDX++;
            case 1 -> scanCursorDZ++;
            case 2 -> scanCursorDX--;
            case 3 -> scanCursorDZ--;
        }

        scanSpiralStep++;
        if (scanSpiralStep >= scanSpiralLeg) {
            scanSpiralStep = 0;
            scanSpiralDirection = (scanSpiralDirection + 1) % 4;
            if (scanSpiralDirection == 0 || scanSpiralDirection == 2) {
                scanSpiralLeg++;
            }
        }
    }

    private void resetScanCursor() {
        scanCursorDX = 0;
        scanCursorDZ = 0;
        scanSpiralLeg = 1;
        scanSpiralStep = 0;
        scanSpiralDirection = 0;
    }

    private void renderDirtyRegions() {
        for (Map.Entry<Long, SimuMapRegion> entry : regions.entrySet()) {
            long key = entry.getKey();
            SimuMapRegion region = entry.getValue();
            SimuMapRegionData data = region.getData();
            if (data != null && data.isDirty() && renderingKeys.add(key)) {
                // 地图纹理生成放到单线程队列，避免多线程同时操作同一 region 数据。
                renderExecutor.execute(() -> {
                    try {
                        SimuMapRenderer.renderRegion(region);
                    } catch (Exception e) {
                        LOGGER.error("Simukraft: Failed to render region {}", region, e);
                    } finally {
                        renderingKeys.remove(key);
                    }
                });
            }
        }
    }

    private void releaseStaleRegions(long maxAge) {
        long now = System.currentTimeMillis();
        regions.entrySet().removeIf(entry -> {
            SimuMapRegion region = entry.getValue();
            if (now - region.getLastAccessTime() > maxAge) {
                // 离玩家太近的 region 即使过期也保留，避免地图拖动时反复释放/重载。
                if (region.distToPlayer() < 512 * 512 * 4) return false;
                region.release();
                return true;
            }
            return false;
        });
    }

    private void persistRegionsAsync(@Nullable String worldId, @Nullable ResourceKey<Level> dimension,
                                     List<SimuMapRegion> regionSnapshot, String reason) {
        if (regionSnapshot.isEmpty()) {
            return;
        }

        for (SimuMapRegion region : regionSnapshot) {
            region.releaseTexture();
        }

        if (worldId != null && dimension != null) {
            SimuMapStorage.saveAllAsync(worldId, dimension, regionSnapshot, reason);
            return;
        }

        for (SimuMapRegion region : regionSnapshot) {
            region.discardData();
        }
    }

    private void queueRegionLoad(String worldId, ResourceKey<Level> dimension) {
        int currentLoadGeneration = ++loadGeneration;
        SimuMapStorage.loadAllAsync(worldId, dimension, loadedRegions -> {
            // 异步加载返回时可能已经切换维度，用 generation 丢弃过期回调。
            if (!initialized || currentLoadGeneration != loadGeneration) {
                return;
            }
            if (currentDimension == null || !currentDimension.equals(dimension)) {
                return;
            }
            regions.putAll(loadedRegions);
            LOGGER.info("Simukraft: Async-loaded {} regions for world={} dim={}.",
                    loadedRegions.size(), worldId, SimuMapStorage.dimensionToDir(dimension));
        });
    }

    /**
     * 璁剧疆鎵弿鍗婂緞锛堝尯鍧楁暟锛夈€?
     */
    public void setScanRadius(int radius) {
        this.scanRadius = Math.max(1, Math.min(radius, 32));
    }

    /**
     * 鑾峰彇鎵弿鍗婂緞銆?
     */
    public int getScanRadius() {
        return scanRadius;
    }

    private static long regionKey(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
    }
}
