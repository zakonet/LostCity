package client.cn.kafei.simukraft.client.city.map;

/**
 * 鍦板浘娓叉煋鏍峰紡鏋氫妇銆?
 * 鍐冲畾鍩庡競鍦板浘浣跨敤鍝搴曞眰鍦板浘绾圭悊鏁版嵁婧愩€?
 */
public enum MapRenderStyle {

    /**
     * 鑷湁娓叉煋绯荤粺锛堥粯璁わ級銆?
     * 浣跨敤 {@link SimuMapManager} 鐙珛鎵弿鍜屾覆鏌擄紝涓嶄緷璧栦换浣曞閮ㄦā缁勩€?
     */
    SIMUKRAFT,

    /**
     * Xaero's World Map 娓叉煋椋庢牸銆?
     * 鍒╃敤 Xaero 宸叉湁鐨勯珮璐ㄩ噺鍦板浘绾圭悊锛涘綋 Xaero 涓嶅彲鐢ㄦ椂鑷姩闄嶇骇涓?{@link #SIMUKRAFT}銆?
     */
    XAERO,

    /**
     * FTB Chunks 娓叉煋椋庢牸銆?
     * 鍒╃敤 FTB Chunks 宸叉湁鐨勫湴鍥剧汗鐞嗭紱褰?FTB Chunks 涓嶅彲鐢ㄦ椂鑷姩闄嶇骇涓?{@link #SIMUKRAFT}銆?
     */
    FTB;

    /**
     * 鏍规嵁鍚嶇О瀹夊叏瑙ｆ瀽鏍峰紡锛屾棤鏁堝€艰繑鍥?{@link #SIMUKRAFT}銆?
     */
    public static MapRenderStyle fromString(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SIMUKRAFT;
        }
    }
}
