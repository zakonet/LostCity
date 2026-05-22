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
 * 鍦板浘鏁版嵁鎸佷箙鍖栧瓨鍌ㄧ鐞嗗櫒銆?
 *
 * <p>瀛樺偍鐩綍缁撴瀯锛堜綅浜?.minecraft/simukraft_mapdata/锛夛細
 * <pre>
 * simukraft_mapdata/
 *   &lt;瀛樻。鏍囪瘑&gt;/
 *     &lt;缁村害鍛藉悕绌洪棿&gt;_&lt;缁村害璺緞&gt;/
 *       &lt;regionX&gt;_&lt;regionZ&gt;.smr
 * </pre>
 *
 * <p>鏂囦欢鏍煎紡锛?smr = Simukraft Map Region锛夛細
 * <ul>
 *   <li>4 瀛楄妭 magic锛歿@code 0x534D5200}锛?SMR\0"锛?/li>
 *   <li>2 瀛楄妭 version锛歿@code 1}</li>
 *   <li>512*512 涓?short锛歨eight 鏁扮粍</li>
 *   <li>512*512 涓?int锛歝olor 鏁扮粍</li>
 *   <li>512*512 涓?short锛歠lags 鏁扮粍</li>
 * </ul>
 *
 * <p>瀛樻。鏍囪瘑瑙勫垯锛?
 * <ul>
 *   <li>鍗曚汉娓告垙锛氫娇鐢ㄥ瓨妗ｆ枃浠跺す鍚嶇О</li>
 *   <li>澶氫汉娓告垙锛氫娇鐢?{@code mp_<IP>_<port>}锛岀壒娈婂瓧绗︽浛鎹负涓嬪垝绾?/li>
 * </ul>
 */
public class SimuMapStorage {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int MAGIC = 0x534D5200;
    private static final short VERSION = 1;

    /** 鏍瑰瓨鍌ㄧ洰褰曪紝浣嶄簬 MC 娓告垙鐩綍涓?*/
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
     * 鑾峰彇褰撳墠瀛樻。鐨勬爣璇嗗瓧绗︿覆銆?
     *
     * @return 鍗曚汉娓告垙杩斿洖瀛樻。鏂囦欢澶瑰悕锛屽浜烘父鎴忚繑鍥?{@code mp_<host>_<port>}锛屾棤娉曡瘑鍒椂杩斿洖 {@code unknown}
     */
    public static String getCurrentWorldId() {
        Minecraft mc = Minecraft.getInstance();
        var singleplayerServer = mc.getSingleplayerServer();
        if (singleplayerServer != null) {
            // 单人世界使用存档名，多人服务器使用地址，避免地图缓存互相覆盖。
            String levelId = singleplayerServer.getWorldData().getLevelName();
            return sanitize(levelId);
        }
        var currentServer = mc.getCurrentServer();
        if (currentServer != null) {
            String host = currentServer.ip;
            return "mp_" + sanitize(host);
        }
        return "unknown";
    }

    /**
     * 灏嗙淮搴?key 杞崲涓哄悎娉曠殑鐩綍鍚嶇О銆?
     *
     * @param dimension 缁村害璧勬簮閿?
     * @return 褰㈠ {@code minecraft_overworld} 鐨勫瓧绗︿覆
     */
    public static String dimensionToDir(ResourceKey<Level> dimension) {
        String ns = dimension.location().getNamespace();
        String path = dimension.location().getPath();
        return sanitize(ns + "_" + path);
    }

    /**
     * 鑾峰彇鎸囧畾瀛樻。鍜岀淮搴︾殑鍖哄煙鏂囦欢鎵€鍦ㄧ洰褰曡矾寰勩€?
     *
     * @param worldId   瀛樻。鏍囪瘑
     * @param dimension 缁村害璧勬簮閿?
     * @return 鐩綍璺緞
     */
    public static Path getRegionDir(String worldId, ResourceKey<Level> dimension) {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        return gameDir.resolve(ROOT_DIR).resolve(worldId).resolve(dimensionToDir(dimension));
    }

    /**
     * 鑾峰彇鍗曚釜鍖哄煙鏂囦欢鐨勮矾寰勩€?
     *
     * @param worldId   瀛樻。鏍囪瘑
     * @param dimension 缁村害璧勬簮閿?
     * @param regionX   鍖哄煙X鍧愭爣
     * @param regionZ   鍖哄煙Z鍧愭爣
     * @return 鏂囦欢璺緞
     */
    public static Path getRegionFile(String worldId, ResourceKey<Level> dimension, int regionX, int regionZ) {
        return getRegionDir(worldId, dimension).resolve(regionX + "_" + regionZ + ".smr");
    }

    /**
     * 灏嗗崟涓尯鍩熺殑鏁版嵁鍐欏叆纾佺洏銆?
     * 浠呭湪鏈夊疄闄呮暟鎹椂鍐欏叆锛涜嫢鍖哄煙鏁版嵁涓虹┖鍒欒烦杩囥€?
     *
     * @param worldId   瀛樻。鏍囪瘑
     * @param dimension 缁村害璧勬簮閿?
     * @param region    寰呬繚瀛樼殑鍖哄煙
     */
    public static void saveRegion(String worldId, ResourceKey<Level> dimension, SimuMapRegion region) {
        SimuMapRegionData data = region.getData();
        if (data == null || data.isEmpty()) return;

        Path file = getRegionFile(worldId, dimension, region.regionX, region.regionZ);
        try {
            // .smr 是固定长度二进制文件，按 height/color/flags 三个数组顺序写入。
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
                out.writeInt(MAGIC);
                out.writeShort(VERSION);
                for (short h : data.height) {
                    out.writeShort(h);
                }
                for (int c : data.color) {
                    out.writeInt(c);
                }
                for (short f : data.flags) {
                    out.writeShort(f);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Simukraft: Failed to save map region ({}, {}) for world={} dim={}",
                    region.regionX, region.regionZ, worldId, dimensionToDir(dimension), e);
        }
    }

    /**
     * 灏嗕竴鎵瑰尯鍩熺殑鏁版嵁鎵归噺鍐欏叆纾佺洏銆?
     *
     * @param worldId   瀛樻。鏍囪瘑
     * @param dimension 缁村害璧勬簮閿?
     * @param regions   寰呬繚瀛樼殑鍖哄煙闆嗗悎
     */
    public static void saveAll(String worldId, ResourceKey<Level> dimension,
                               Collection<SimuMapRegion> regions) {
        for (SimuMapRegion region : regions) {
            saveRegion(worldId, dimension, region);
        }
        LOGGER.debug("Simukraft: Saved {} regions for world={} dim={}",
                regions.size(), worldId, dimensionToDir(dimension));
    }

    /**
     * 寮傛淇濆瓨涓€鎵瑰尯鍩燂紝閬垮厤鍦ㄥ鎴风鐧诲綍/閫€鍑洪樁娈甸樆濉炰富绾跨▼銆?
     * 杩欓噷鐩存帴淇濈暀鍖哄煙鏁版嵁寮曠敤锛屼富绾跨▼鍙噴鏀剧汗鐞嗚祫婧愶紝纾佺洏鍐欏叆瀹屾垚鍚庡啀娓呯悊鏁版嵁瀵硅薄銆?
     */
    public static void saveAllAsync(String worldId, ResourceKey<Level> dimension,
                                    Collection<SimuMapRegion> regions, String reason) {
        List<SimuMapRegion> regionSnapshot = new ArrayList<>(regions);
        if (regionSnapshot.isEmpty()) {
            return;
        }

        SAVE_EXECUTOR.execute(() -> {
            // 保存完成后丢弃 CPU 侧地形数据，纹理和文件仍可用于地图显示/恢复。
            saveAll(worldId, dimension, regionSnapshot);
            for (SimuMapRegion region : regionSnapshot) {
                region.discardData();
            }
            LOGGER.info("Simukraft: Async-saved {} regions for world={} dim={} reason={}",
                    regionSnapshot.size(), worldId, dimensionToDir(dimension), reason);
        });
    }

    /**
     * 浠庣鐩樺姞杞芥寚瀹氬瓨妗ｅ拰缁村害涓嬬殑鎵€鏈夊凡瀛樺尯鍩熸暟鎹紝濉厖鍒颁紶鍏ョ殑 regions Map 涓€?
     * 浠呭姞杞芥枃浠跺瓨鍦ㄤ笖鏍煎紡鍚堟硶鐨勫尯鍩燂紱鏍煎紡閿欒鐨勬枃浠朵細琚烦杩囧苟璁板綍璀﹀憡銆?
     *
     * @param worldId   瀛樻。鏍囪瘑
     * @param dimension 缁村害璧勬簮閿?
     * @param regions   鐩爣 Map锛宬ey 涓哄尯鍩熷潗鏍囩紪鐮侊紝value 涓?{@link SimuMapRegion}
     */
    public static void loadAll(String worldId, ResourceKey<Level> dimension,
                               Map<Long, SimuMapRegion> regions) {
        Path dir = getRegionDir(worldId, dimension);
        if (!Files.isDirectory(dir)) return;

        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".smr")).forEach(file -> {
                String name = file.getFileName().toString();
                name = name.substring(0, name.length() - 4);
                String[] parts = name.split("_", 2);
                if (parts.length != 2) return;
                try {
                    int rx = Integer.parseInt(parts[0]);
                    int rz = Integer.parseInt(parts[1]);
                    SimuMapRegionData data = readRegionFile(file);
                    if (data != null) {
                        SimuMapRegion region = new SimuMapRegion(rx, rz);
                        region.setData(data);
                        data.markDirty();
                        long key = ((long) rx << 32) | (rz & 0xFFFFFFFFL);
                        regions.put(key, region);
                    }
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

    /**
     * 寮傛鍔犺浇涓栫晫鍖哄煙缂撳瓨锛岄檷浣庤繘鍏ヤ笘鐣屾椂鐨勪富绾跨▼鍗￠】銆?
     */
    public static void loadAllAsync(String worldId, ResourceKey<Level> dimension,
                                    Map<Long, SimuMapRegion> regions) {
        LOAD_EXECUTOR.execute(() -> loadAll(worldId, dimension, regions));
    }

    /**
     * 寮傛鍔犺浇鍒颁复鏃?Map锛屽啀鐢辫皟鐢ㄦ柟鍐冲畾鏄惁鍚堝苟锛岄伩鍏嶈法涓栫晫寮傛鍥炵亴銆?
     */
    public static void loadAllAsync(String worldId, ResourceKey<Level> dimension,
                                    Consumer<Map<Long, SimuMapRegion>> callback) {
        LOAD_EXECUTOR.execute(() -> {
            Map<Long, SimuMapRegion> loadedRegions = new ConcurrentHashMap<>();
            loadAll(worldId, dimension, loadedRegions);
            // 回调仍在加载线程执行，调用方必须自己处理过期结果和线程安全。
            callback.accept(loadedRegions);
        });
    }

    /**
     * 浠庡崟涓?.smr 鏂囦欢璇诲彇鍖哄煙鏁版嵁銆?
     *
     * @param file 鏂囦欢璺緞
     * @return 鎴愬姛鏃惰繑鍥炲～鍏呭ソ鐨?{@link SimuMapRegionData}锛屾牸寮忛潪娉曟椂杩斿洖 {@code null}
     */
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
            int rx = Integer.parseInt(parts[0]);
            int rz = Integer.parseInt(parts[1]);

            SimuMapRegionData data = new SimuMapRegionData(rx, rz);
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

    /**
     * 灏嗗瓧绗︿覆涓笉鍚堟硶鐨勬枃浠剁郴缁熷瓧绗︽浛鎹负涓嬪垝绾裤€?
     *
     * @param s 鍘熷瀛楃涓?
     * @return 娓呯悊鍚庣殑瀛楃涓?
     */
    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
