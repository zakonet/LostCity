package client.cn.kafei.simukraft.client.city.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Simukraft 鑷湁鐨勬柟鍧楅鑹叉槧灏勭郴缁熴€?
 * 鍙傝€?FTB Chunks 鐨?ColorMapLoader / BlockColors锛屼絾瀹屽叏鐙珛涓嶄緷璧?FTB 搴撱€?
 * 涓烘瘡涓柟鍧楃姸鎬佽绠椾竴涓?ARGB 棰滆壊锛岀敤浜庡湴鍥炬覆鏌撱€?
 */
public class SimuBlockColors {
    private static final SimuBlockColors INSTANCE = new SimuBlockColors();
    private final Map<Block, Integer> colorOverrides = new HashMap<>();
    private boolean initialized = false;

    private SimuBlockColors() {
    }

    public static SimuBlockColors getInstance() {
        return INSTANCE;
    }

    /**
     * 鍒濆鍖栭鑹茬紦瀛樺拰瑕嗙洊銆?
     * 搴斿湪瀹㈡埛绔缃樁娈佃皟鐢ㄣ€?
     */
    public void init() {
        if (initialized) return;
        initialized = true;

        colorOverrides.put(Blocks.WATER, 0xFF3F76E4);
        colorOverrides.put(Blocks.LAVA, 0xFFD4610A);
        colorOverrides.put(Blocks.ICE, 0xFF91B4FC);
        colorOverrides.put(Blocks.PACKED_ICE, 0xFF8DB4FA);
        colorOverrides.put(Blocks.BLUE_ICE, 0xFF74AEF9);
        colorOverrides.put(Blocks.SNOW, 0xFFFAFAFA);
        colorOverrides.put(Blocks.SNOW_BLOCK, 0xFFF0F0F0);
        colorOverrides.put(Blocks.SAND, 0xFFDBD3A0);
        colorOverrides.put(Blocks.RED_SAND, 0xFFA85320);
        colorOverrides.put(Blocks.GRAVEL, 0xFF837E7A);
        colorOverrides.put(Blocks.CLAY, 0xFF9EA4B0);
        colorOverrides.put(Blocks.BEDROCK, 0xFF545454);
        colorOverrides.put(Blocks.NETHERRACK, 0xFF6B3535);
        colorOverrides.put(Blocks.END_STONE, 0xFFDBDE8E);
        colorOverrides.put(Blocks.OBSIDIAN, 0xFF14121D);
        colorOverrides.put(Blocks.DIAMOND_BLOCK, 0xFF6EECD2);
        colorOverrides.put(Blocks.GOLD_BLOCK, 0xFFF9EC4E);
        colorOverrides.put(Blocks.IRON_BLOCK, 0xFFD8D8D8);
        colorOverrides.put(Blocks.EMERALD_BLOCK, 0xFF41C950);
        colorOverrides.put(Blocks.COAL_BLOCK, 0xFF161616);
        colorOverrides.put(Blocks.REDSTONE_BLOCK, 0xFFA81303);
        colorOverrides.put(Blocks.LAPIS_BLOCK, 0xFF1D47A5);
        colorOverrides.put(Blocks.MYCELIUM, 0xFF6F6265);
        colorOverrides.put(Blocks.SOUL_SAND, 0xFF544033);
        colorOverrides.put(Blocks.GLOWSTONE, 0xFFAB8654);
        colorOverrides.put(Blocks.MELON, 0xFF669E1F);
        colorOverrides.put(Blocks.PUMPKIN, 0xFFC07615);
        colorOverrides.put(Blocks.TNT, 0xFFDB4A2B);
        colorOverrides.put(Blocks.BOOKSHELF, 0xFF6B5339);
        colorOverrides.put(Blocks.COBBLESTONE, 0xFF7F7F7F);
        colorOverrides.put(Blocks.STONE, 0xFF7D7D7D);
        colorOverrides.put(Blocks.DEEPSLATE, 0xFF505050);
        colorOverrides.put(Blocks.MOSS_BLOCK, 0xFF596D28);
        colorOverrides.put(Blocks.CHERRY_LEAVES, 0xFFEEB3C7);
        colorOverrides.put(Blocks.CHERRY_LOG, 0xFF3A1F23);
    }

    /**
     * 鑾峰彇鏂瑰潡鍦ㄧ粰瀹氫綅缃殑鍦板浘棰滆壊銆?
     *
     * @param state  鏂瑰潡鐘舵€?
     * @param level  涓栫晫瀹炰緥
     * @param pos    鏂瑰潡浣嶇疆
     * @return ARGB 棰滆壊鍊?
     */
    public int getBlockColor(BlockState state, Level level, BlockPos pos) {
        Block block = state.getBlock();

        Integer override = colorOverrides.get(block);
        if (override != null) {
            return override;
        }

        if (state.isAir()) {
            return 0x00000000;
        }

        if (block instanceof GrassBlock) {
            return getBiomeGrassColor(level, pos);
        }
        if (state.is(Objects.requireNonNull(BlockTags.LEAVES))) {
            return getBiomeFoliageColor(level, pos);
        }

        if (block instanceof LiquidBlock) {
            return getBiomeWaterColor(level, pos);
        }

        try {
            BlockColors blockColors = Minecraft.getInstance().getBlockColors();
            int tintColor = blockColors.getColor(state, level, pos, 0);
            if (tintColor != -1 && tintColor != 0) {
                return 0xFF000000 | tintColor;
            }
        } catch (Exception ignored) {
        }

        try {
            MapColor mapColor = state.getMapColor(Objects.requireNonNull(level), Objects.requireNonNull(pos));
            if (mapColor != MapColor.NONE) {
                return 0xFF000000 | mapColor.col;
            }
        } catch (Exception ignored) {
        }

        return 0xFF7F7F7F;
    }

    /**
     * 鑾峰彇鐢熺墿缇ょ郴鑽夊湴棰滆壊銆?
     */
    private int getBiomeGrassColor(Level level, BlockPos pos) {
        try {
            Biome biome = level.getBiome(Objects.requireNonNull(pos)).value();
            int color = biome.getGrassColor(pos.getX(), pos.getZ());
            return 0xFF000000 | color;
        } catch (Exception e) {
            return 0xFF7CBB4A; // 榛樿鑽夊湴棰滆壊
        }
    }

    /**
     * 鑾峰彇鐢熺墿缇ょ郴鏍戝彾棰滆壊銆?
     */
    private int getBiomeFoliageColor(Level level, BlockPos pos) {
        try {
            Biome biome = level.getBiome(Objects.requireNonNull(pos)).value();
            int color = biome.getFoliageColor();
            return 0xFF000000 | color;
        } catch (Exception e) {
            return 0xFF59AE30; // 榛樿鏍戝彾棰滆壊
        }
    }

    /**
     * 鑾峰彇鐢熺墿缇ょ郴姘翠綋棰滆壊銆?
     */
    private int getBiomeWaterColor(Level level, BlockPos pos) {
        try {
            Biome biome = level.getBiome(Objects.requireNonNull(pos)).value();
            int color = biome.getWaterColor();
            return 0xFF000000 | color;
        } catch (Exception e) {
            return 0xFF3F76E4; // 榛樿姘撮鑹?
        }
    }

    /**
     * 娣峰悎涓や釜 ARGB 棰滆壊銆?
     *
     * @param base    鍩鸿壊
     * @param overlay 鍙犲姞鑹诧紙alpha 鎺у埗娣峰悎绋嬪害锛?
     * @return 娣峰悎鍚庣殑棰滆壊
     */
    public static int blendColors(int base, int overlay) {
        int oa = (overlay >> 24) & 0xFF;
        if (oa == 0) return base;
        if (oa == 255) return overlay;

        int ba = (base >> 24) & 0xFF;
        int br = (base >> 16) & 0xFF;
        int bg = (base >> 8) & 0xFF;
        int bb = base & 0xFF;

        int or = (overlay >> 16) & 0xFF;
        int og = (overlay >> 8) & 0xFF;
        int ob = overlay & 0xFF;

        float alpha = oa / 255f;
        int r = (int) (br * (1 - alpha) + or * alpha);
        int g = (int) (bg * (1 - alpha) + og * alpha);
        int b = (int) (bb * (1 - alpha) + ob * alpha);
        int a = Math.max(ba, oa);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 璋冭妭棰滆壊浜害銆?
     *
     * @param color      ARGB 棰滆壊
     * @param brightness 浜害璋冭妭閲?[-1.0, 1.0]
     * @return 璋冭妭鍚庣殑棰滆壊
     */
    public static int adjustBrightness(int color, float brightness) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        if (brightness > 0) {
            r = (int) (r + (255 - r) * brightness);
            g = (int) (g + (255 - g) * brightness);
            b = (int) (b + (255 - b) * brightness);
        } else {
            float factor = 1.0f + brightness;
            r = (int) (r * factor);
            g = (int) (g * factor);
            b = (int) (b * factor);
        }

        r = Math.min(Math.max(r, 0), 255);
        g = Math.min(Math.max(g, 0), 255);
        b = Math.min(Math.max(b, 0), 255);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 灏?ARGB 杞崲涓?NativeImage 浣跨敤鐨?ABGR 鏍煎紡銆?
     */
    public static int toNativeColor(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
