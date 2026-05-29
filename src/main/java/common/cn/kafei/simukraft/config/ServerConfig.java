package common.cn.kafei.simukraft.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue CITY_CHUNK_PRICE;
    private static final ModConfigSpec.IntValue POPULATION_GROWTH_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue POPULATION_GROWTH_MAX_PER_INTERVAL;
    private static final ModConfigSpec.DoubleValue BUILDER_BLOCKS_PER_SECOND;
    private static final ModConfigSpec.IntValue NPC_MAX_LEVEL;
    private static final ModConfigSpec.BooleanValue BUILDER_XP_GAIN;
    private static final ModConfigSpec.IntValue BUILDER_XP_PER_BLOCK;
    private static final ModConfigSpec.BooleanValue MATERIALS_CREATIVE_MODE;
    private static final ModConfigSpec.BooleanValue MATERIALS_EXPERT_MODE;
    private static final ModConfigSpec.BooleanValue MATERIALS_CATEGORY_MATCHING;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> BASIC_MATERIALS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> MATERIAL_CATEGORY_GROUPS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> EXPERT_MODE_SKIP_LIST;
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
        BUILDER_BLOCKS_PER_SECOND = builder
                .comment(
                        "Base blocks one level 1 builder can place per second.",
                        "The final speed scales with NPC level like the legacy version."
                )
                .translation("config.simukraft.construction.builderBlocksPerSecond")
                .defineInRange("builderBlocksPerSecond", 1.0D, 0.1D, 20.0D);
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
        builder.push("npc_leveling");
        NPC_MAX_LEVEL = builder
                .comment("Maximum NPC profession level.")
                .translation("config.simukraft.npc_leveling.maxLevel")
                .defineInRange("maxLevel", 20, 1, 20);
        BUILDER_XP_GAIN = builder
                .comment("Whether builders gain profession experience when placing blocks.")
                .translation("config.simukraft.npc_leveling.builderEnableXpGain")
                .define("builderEnableXpGain", true);
        BUILDER_XP_PER_BLOCK = builder
                .comment("Builder profession experience gained per placed block.")
                .translation("config.simukraft.npc_leveling.builderXpPerBlock")
                .defineInRange("builderXpPerBlock", 1, 0, 100);
        builder.pop();
        builder.push("materials");
        MATERIALS_CREATIVE_MODE = builder
                .comment(
                        "Whether NPC work consumes no materials.",
                        "When enabled, material checks are bypassed. This takes priority over expert mode."
                )
                .translation("config.simukraft.materials.enableCreativeMode")
                .define("enableCreativeMode", false);
        MATERIALS_EXPERT_MODE = builder
                .comment(
                        "Whether expert material mode is enabled.",
                        "Expert mode requires every build block except entries in expertModeSkipList.",
                        "Normal mode uses basicMaterials and materialCategoryGroups."
                )
                .translation("config.simukraft.materials.enableExpertMode")
                .define("enableExpertMode", false);
        MATERIALS_CATEGORY_MATCHING = builder
                .comment(
                        "Whether normal mode allows category material matching.",
                        "When enabled, a configured category header item can satisfy matching member blocks."
                )
                .translation("config.simukraft.materials.enableMaterialCategoryMatching")
                .define("enableMaterialCategoryMatching", true);
        BASIC_MATERIALS = builder
                .comment(
                        "Normal mode material list.",
                        "Only these single block/item ids require materials directly.",
                        "Format: modid:item_name"
                )
                .translation("config.simukraft.materials.basicMaterials")
                .defineListAllowEmpty("basicMaterials",
                        () -> MaterialConfigDefaults.BASIC_MATERIALS,
                        () -> "minecraft:stone",
                        ServerConfig::isStringEntry);
        MATERIAL_CATEGORY_GROUPS = builder
                .comment(
                        "Normal mode category matching groups.",
                        "Format: group_name|headers|members",
                        "Headers are items players may provide for matching member blocks.",
                        "If headers are empty, all members can replace each other."
                )
                .translation("config.simukraft.materials.materialCategoryGroups")
                .defineListAllowEmpty("materialCategoryGroups",
                        () -> MaterialConfigDefaults.MATERIAL_CATEGORY_GROUPS,
                        () -> "custom_group||minecraft:stone",
                        ServerConfig::isStringEntry);
        EXPERT_MODE_SKIP_LIST = builder
                .comment(
                        "Expert mode skip list.",
                        "Blocks in this list do not consume materials in expert mode.",
                        "Format: modid:block_name"
                )
                .translation("config.simukraft.materials.expertModeSkipList")
                .defineListAllowEmpty("expertModeSkipList",
                        () -> MaterialConfigDefaults.EXPERT_MODE_SKIP_LIST,
                        () -> "minecraft:bedrock",
                        ServerConfig::isStringEntry);
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

    public static double builderBlocksPerSecond() {
        return BUILDER_BLOCKS_PER_SECOND.get();
    }

    public static int npcMaxLevel() {
        return NPC_MAX_LEVEL.get();
    }

    public static boolean builderXpGainEnabled() {
        return BUILDER_XP_GAIN.get();
    }

    public static int builderXpPerBlock() {
        return BUILDER_XP_PER_BLOCK.get();
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

    public static boolean materialsCreativeMode() {
        return MATERIALS_CREATIVE_MODE.get();
    }

    public static boolean materialsExpertMode() {
        return MATERIALS_EXPERT_MODE.get();
    }

    public static boolean materialCategoryMatchingEnabled() {
        return MATERIALS_CATEGORY_MATCHING.get();
    }

    public static List<String> basicMaterials() {
        return copyStringList(BASIC_MATERIALS.get());
    }

    public static List<String> materialCategoryGroups() {
        return copyStringList(MATERIAL_CATEGORY_GROUPS.get());
    }

    public static List<String> expertModeSkipList() {
        return copyStringList(EXPERT_MODE_SKIP_LIST.get());
    }

    private static boolean isStringEntry(Object value) {
        return value instanceof String string && !string.isBlank();
    }

    private static List<String> copyStringList(List<? extends String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }
}
