package common.cn.kafei.simukraft.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue CITY_CHUNK_PRICE;
    private static final ModConfigSpec.IntValue POPULATION_GROWTH_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue POPULATION_GROWTH_MAX_PER_INTERVAL;
    private static final ModConfigSpec.IntValue BUILDER_BLOCKS_PER_TICK;
    private static final ModConfigSpec.IntValue BUILDER_MATERIAL_SEARCH_RADIUS;
    private static final ModConfigSpec.ConfigValue<String> BUILDER_REQUIRED_MATERIAL_BLOCKS;
    private static final ModConfigSpec.BooleanValue BUILDER_PAUSE_AT_NIGHT;
    private static final ModConfigSpec.IntValue BUILDER_REST_START_TIME;
    private static final ModConfigSpec.IntValue BUILDER_REST_END_TIME;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("economy");
        CITY_CHUNK_PRICE = builder.defineInRange("cityChunkPrice", 10.0D, 0.0D, 1_000_000.0D);
        builder.pop();
        builder.push("population");
        POPULATION_GROWTH_INTERVAL_TICKS = builder.defineInRange("growthIntervalTicks", 24_000, 20, 2_400_000);
        POPULATION_GROWTH_MAX_PER_INTERVAL = builder.defineInRange("growthMaxPerInterval", 1, 0, 100);
        builder.pop();
        builder.push("construction");
        BUILDER_BLOCKS_PER_TICK = builder
                .comment("How many blocks one builder can place per server tick.")
                .defineInRange("builderBlocksPerTick", 3, 1, 128);
        BUILDER_MATERIAL_SEARCH_RADIUS = builder
                .comment("Container search radius around the build box when a material is required.")
                .defineInRange("builderMaterialSearchRadius", 6, 1, 32);
        BUILDER_REQUIRED_MATERIAL_BLOCKS = builder
                .comment(
                        "Comma/semicolon/newline separated material whitelist.",
                        "Only blocks matched by this list consume materials; blocks not listed are free.",
                        "Supported entries: minecraft:stone, block:minecraft:stone, #minecraft:logs, tag:minecraft:logs, minecraft:*_planks."
                )
                .define("builderRequiredMaterialBlocks", "");
        BUILDER_PAUSE_AT_NIGHT = builder
                .comment("Whether builder tasks pause during the configured night/rest time.")
                .define("builderPauseAtNight", true);
        BUILDER_REST_START_TIME = builder
                .comment("Day time when builders stop working. Minecraft night starts around 13000.")
                .defineInRange("builderRestStartTime", 13000, 0, 23999);
        BUILDER_REST_END_TIME = builder
                .comment("Day time when builders resume working. Minecraft morning starts around 0.")
                .defineInRange("builderRestEndTime", 0, 0, 23999);
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {
    }

    public static double cityChunkPrice() {
        return CITY_CHUNK_PRICE.get();
    }

    public static int populationGrowthIntervalTicks() {
        return POPULATION_GROWTH_INTERVAL_TICKS.get();
    }

    public static int populationGrowthMaxPerInterval() {
        return POPULATION_GROWTH_MAX_PER_INTERVAL.get();
    }

    public static int builderBlocksPerTick() {
        return BUILDER_BLOCKS_PER_TICK.get();
    }

    public static int builderMaterialSearchRadius() {
        return BUILDER_MATERIAL_SEARCH_RADIUS.get();
    }

    public static String builderRequiredMaterialBlocks() {
        return BUILDER_REQUIRED_MATERIAL_BLOCKS.get();
    }

    public static boolean builderPauseAtNight() {
        return BUILDER_PAUSE_AT_NIGHT.get();
    }

    public static int builderRestStartTime() {
        return BUILDER_REST_START_TIME.get();
    }

    public static int builderRestEndTime() {
        return BUILDER_REST_END_TIME.get();
    }
}
