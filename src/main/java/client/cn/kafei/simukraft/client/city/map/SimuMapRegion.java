package client.cn.kafei.simukraft.client.city.map;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 浠ｈ〃涓€涓?512x512 鏂瑰潡鍖哄煙鐨勫湴鍥剧摝鐗囥€?
 * 绠＄悊鏁版嵁灞?({@link SimuMapRegionData}) 鍜?GPU 绾圭悊銆?
 * 鍙傝€?FTB Chunks 鐨?MapRegion 浣嗗畬鍏ㄧ嫭绔嬨€?
 */
public class SimuMapRegion {
    private static final Logger LOGGER = LogUtils.getLogger();

    public final int regionX;
    public final int regionZ;

    private SimuMapRegionData data;
    private NativeImage renderedImage;
    private int textureId = -1;
    private volatile boolean textureNeedsUpload = false;
    private volatile boolean imageLoaded = false;
    private long lastAccessTime;

    public SimuMapRegion(int regionX, int regionZ) {
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.lastAccessTime = System.currentTimeMillis();
    }

    /**
     * 鑾峰彇鎴栧垱寤哄尯鍩熸暟鎹€?
     */
    public SimuMapRegionData getOrCreateData() {
        if (data == null) {
            data = new SimuMapRegionData(regionX, regionZ);
        }
        lastAccessTime = System.currentTimeMillis();
        return data;
    }

    /**
     * 鐩存帴璁剧疆鍖哄煙鏁版嵁锛堢敤浜庝粠纾佺洏鍔犺浇鏃舵敞鍏ュ凡鍙嶅簭鍒楀寲鐨勬暟鎹級銆?
     *
     * @param data 宸插～鍏呭ソ鐨勫尯鍩熸暟鎹紝涓嶅緱涓?null
     */
    public void setData(SimuMapRegionData data) {
        this.data = data;
        this.lastAccessTime = System.currentTimeMillis();
    }

    /**
     * 鑾峰彇鍖哄煙鏁版嵁锛堝彲鑳戒负 null锛夈€?
     */
    @Nullable
    public SimuMapRegionData getData() {
        if (data != null) {
            lastAccessTime = System.currentTimeMillis();
        }
        return data;
    }

    /**
     * 鏁版嵁鏄惁宸插姞杞姐€?
     */
    public boolean hasData() {
        return data != null;
    }

    /**
     * 鑾峰彇娓叉煋鍥惧儚锛堝垱寤哄鏋滀笉瀛樺湪锛夈€?
     */
    public NativeImage getOrCreateImage() {
        if (renderedImage == null) {
            renderedImage = new NativeImage(NativeImage.Format.RGBA, 512, 512, true);
            renderedImage.fillRect(0, 0, 512, 512, 0);
        }
        return renderedImage;
    }

    /**
     * 鏍囪绾圭悊闇€瑕佷笂浼犲埌 GPU銆?
     */
    public void markTextureNeedsUpload() {
        textureNeedsUpload = true;
        imageLoaded = false;
    }

    /**
     * 鑾峰彇 OpenGL 绾圭悊 ID锛屽苟鍦ㄩ渶瑕佹椂涓婁紶鍥惧儚鏁版嵁銆?
     * 蹇呴』鍦ㄦ覆鏌撶嚎绋嬭皟鐢ㄣ€?
     */
    public int getTextureId() {
        if (textureId == -1) {
            textureId = com.mojang.blaze3d.platform.TextureUtil.generateTextureId();
            com.mojang.blaze3d.platform.TextureUtil.prepareImage(textureId, 512, 512);
        }

        if (textureNeedsUpload && renderedImage != null) {
            textureNeedsUpload = false;
            if (RenderSystem.isOnRenderThreadOrInit()) {
                uploadNow();
            } else {
                Minecraft.getInstance().submit(this::uploadNow);
            }
        }

        return textureId;
    }

    private void uploadNow() {
        try {
            RenderSystem.bindTexture(textureId);
            synchronized (this) {
                if (renderedImage != null) {
                    renderedImage.upload(0, 0, 0, false);
                    imageLoaded = true;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Simukraft: Failed to upload map region texture ({}, {})", regionX, regionZ, e);
        }
    }

    /**
     * 绾圭悊鏄惁宸叉垚鍔熶笂浼犲埌 GPU銆?
     */
    public boolean isImageLoaded() {
        return imageLoaded;
    }

    /**
     * 涓婃璁块棶鏃堕棿銆?
     */
    public long getLastAccessTime() {
        return lastAccessTime;
    }

    /**
     * 閲婃斁姝ゅ尯鍩熷崰鐢ㄧ殑鎵€鏈夎祫婧愩€?
     */
    public void release() {
        synchronized (this) {
            if (renderedImage != null) {
                renderedImage.close();
                renderedImage = null;
            }
        }
        if (textureId != -1) {
            GlStateManager._deleteTexture(textureId);
            textureId = -1;
        }
        imageLoaded = false;
        data = null;
    }

    /**
     * 閲婃斁绾圭悊浣嗕繚鐣欐暟鎹紙鐢ㄤ簬鑺傜渷鏄惧瓨锛夈€?
     */
    public void releaseTexture() {
        synchronized (this) {
            if (renderedImage != null) {
                renderedImage.close();
                renderedImage = null;
            }
        }
        if (textureId != -1) {
            GlStateManager._deleteTexture(textureId);
            textureId = -1;
        }
        imageLoaded = false;
    }

    /**
     * 浠呴噴鏀惧唴瀛樻暟鎹€?
     * 鐢ㄤ簬寮傛鎸佷箙鍖栧畬鎴愬悗娓呯悊鏃т笘鐣屾畫鐣欐暟鎹紝閬垮厤鍐嶆瑙︾娓叉煋绾跨▼璧勬簮銆?
     */
    public void discardData() {
        data = null;
    }

    /**
     * 鍒扮帺瀹剁殑璺濈骞虫柟锛堢敤浜庢帓搴忥級銆?
     */
    public double distToPlayer() {
        var player = Minecraft.getInstance().player;
        if (player == null) return Double.MAX_VALUE;
        double cx = regionX * 512.0 + 256.0;
        double cz = regionZ * 512.0 + 256.0;
        double dx = cx - player.getX();
        double dz = cz - player.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * 鍖哄煙瀛楃涓叉爣璇嗐€?
     */
    public String regionKey() {
        return regionX + "," + regionZ;
    }

    @Override
    public String toString() {
        return "SimuMapRegion[" + regionX + "," + regionZ + "]";
    }
}
