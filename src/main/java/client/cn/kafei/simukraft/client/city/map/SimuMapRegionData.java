package client.cn.kafei.simukraft.client.city.map;

import java.util.Arrays;

/**
 * 鍗曚釜鍖哄煙锛?12x512 鏂瑰潡锛屽嵆 32x32 鍖哄潡锛夌殑鍦板浘鏁版嵁銆?
 * 鍙傝€?FTB Chunks 鐨?MapRegionData锛屼絾绠€鍖栦负鐙珛瀹炵幇銆?
 *
 * 鏁版嵁甯冨眬:
 *  - height[512*512]:    姣忎釜鏂瑰潡浣嶇疆鏈€楂橀潪绌烘皵鏂瑰潡楂樺害 (short)
 *  - color[512*512]:     姣忎釜鏂瑰潡浣嶇疆瀵瑰簲鐨?ARGB 棰滆壊 (int)
 *  - flags[512*512]:     鏍囧織浣嶏細bit0=姘撮潰, bit1-4=鍏夌収绛夌骇
 */
public class SimuMapRegionData {

    public static final int SIZE = 512;
    public static final int AREA = SIZE * SIZE;
    public static final short HEIGHT_UNKNOWN = Short.MIN_VALUE;

    /** 鏈€楂橀潪绌烘皵鏂瑰潡楂樺害 */
    public final short[] height = new short[AREA];

    /** 姣忎釜鏂瑰潡浣嶇疆鐨?ARGB 娓叉煋棰滆壊 */
    public final int[] color = new int[AREA];

    /** 鏍囧織浣? bit0=姘撮潰, bit1-4=鍏夌収绛夌骇(0-15) */
    public final short[] flags = new short[AREA];

    /** 姝ゆ暟鎹槸鍚﹀凡琚慨鏀癸紙闇€瑕侀噸鏂版覆鏌擄級 */
    private boolean dirty = true;

    /** 姝ゆ暟鎹搴旂殑鍖哄煙鍧愭爣 */
    public final int regionX;
    public final int regionZ;

    public SimuMapRegionData(int regionX, int regionZ) {
        this.regionX = regionX;
        this.regionZ = regionZ;
        Arrays.fill(height, HEIGHT_UNKNOWN);
    }

    /** 鑾峰彇鎸囧畾鏂瑰潡浣嶇疆鐨勬暟缁勭储寮?*/
    public static int index(int localX, int localZ) {
        return (localX & 0x1FF) + (localZ & 0x1FF) * SIZE;
    }

    /** 璁剧疆鎸囧畾浣嶇疆鐨勬暟鎹?*/
    public void setData(int localX, int localZ, short h, int argbColor, boolean water, int light) {
        int idx = index(localX, localZ);
        short f = (short) ((water ? 1 : 0) | ((light & 0xF) << 1));
        if (height[idx] == h && color[idx] == argbColor && flags[idx] == f) {
            return;
        }
        height[idx] = h;
        color[idx] = argbColor;
        flags[idx] = f;
        dirty = true;
    }

    /** 鑾峰彇鎸囧畾浣嶇疆鐨勯珮搴?*/
    public short getHeight(int localX, int localZ) {
        return height[index(localX, localZ)];
    }

    /** 鑾峰彇鎸囧畾浣嶇疆鐨勯鑹?*/
    public int getColor(int localX, int localZ) {
        return color[index(localX, localZ)];
    }

    /** 妫€鏌ユ寚瀹氫綅缃槸鍚︿负姘撮潰 */
    public boolean isWater(int localX, int localZ) {
        return (flags[index(localX, localZ)] & 1) != 0;
    }

    /** 鑾峰彇鎸囧畾浣嶇疆鐨勫厜鐓х瓑绾?*/
    public int getLight(int localX, int localZ) {
        return (flags[index(localX, localZ)] >> 1) & 0xF;
    }

    /** 鏄惁鏈夋湭娓叉煋鐨勪慨鏀?*/
    public boolean isDirty() {
        return dirty;
    }

    /** 鏍囪涓哄凡娓叉煋 */
    public void clearDirty() {
        dirty = false;
    }

    /** 鏍囪涓洪渶瑕侀噸鏂版覆鏌?*/
    public void markDirty() {
        dirty = true;
    }

    /** 妫€鏌ヨ鍖哄煙鏄惁瀹屽叏绌虹櫧 */
    public boolean isEmpty() {
        for (short h : height) {
            if (h != HEIGHT_UNKNOWN) return false;
        }
        return true;
    }

    /** 鑾峰彇姝ゅ尯鍩熶腑绗竴涓笘鐣屾柟鍧楃殑 X 鍧愭爣 */
    public int worldBlockX() {
        return regionX * SIZE;
    }

    /** 鑾峰彇姝ゅ尯鍩熶腑绗竴涓笘鐣屾柟鍧楃殑 Z 鍧愭爣 */
    public int worldBlockZ() {
        return regionZ * SIZE;
    }
}
