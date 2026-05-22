package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@SuppressWarnings("null")
public final class BuilderMaterialPolicy {
    private static volatile String cachedConfig = null;
    private static volatile List<MaterialRule> cachedRules = List.of();

    private BuilderMaterialPolicy() {
    }

    public static boolean requiresMaterial(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        for (MaterialRule rule : rules()) {
            if (rule.matches(state)) {
                return true;
            }
        }
        return false;
    }

    // 清理材料规则解析缓存，切换存档后重新读取当前世界的服务端配置。
    public static synchronized void clearCache() {
        cachedConfig = null;
        cachedRules = List.of();
    }

    private static List<MaterialRule> rules() {
        String config = ServerConfig.builderRequiredMaterialBlocks();
        String normalizedConfig = config != null ? config : "";
        List<MaterialRule> rules = cachedRules;
        if (normalizedConfig.equals(cachedConfig)) {
            return rules;
        }
        synchronized (BuilderMaterialPolicy.class) {
            if (!normalizedConfig.equals(cachedConfig)) {
                cachedRules = parseRules(normalizedConfig);
                cachedConfig = normalizedConfig;
            }
            return cachedRules;
        }
    }

    private static List<MaterialRule> parseRules(String config) {
        if (config == null || config.isBlank()) {
            return List.of();
        }
        String[] entries = config.split("[,;\\r\\n]+");
        List<MaterialRule> rules = new ArrayList<>();
        for (String rawEntry : entries) {
            String entry = rawEntry.trim().toLowerCase(Locale.ROOT);
            if (entry.isBlank()) {
                continue;
            }
            if (entry.startsWith("tag:")) {
                addTagRule(rules, entry.substring("tag:".length()));
            } else if (entry.startsWith("#")) {
                addTagRule(rules, entry.substring(1));
            } else {
                if (entry.startsWith("block:")) {
                    entry = entry.substring("block:".length());
                }
                if (entry.indexOf('*') >= 0) {
                    rules.add(new PatternRule(entry));
                } else {
                    ResourceLocation id = ResourceLocation.tryParse(entry);
                    if (id != null) {
                        rules.add(new BlockIdRule(id));
                    }
                }
            }
        }
        return List.copyOf(rules);
    }

    private static void addTagRule(List<MaterialRule> rules, String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id != null) {
            rules.add(new TagRule(BlockTags.create(id)));
        }
    }

    private interface MaterialRule {
        boolean matches(BlockState state);
    }

    private record BlockIdRule(ResourceLocation blockId) implements MaterialRule {
        @Override
        public boolean matches(BlockState state) {
            return blockId.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        }
    }

    private record TagRule(TagKey<Block> tag) implements MaterialRule {
        @Override
        public boolean matches(BlockState state) {
            return state.is(tag);
        }
    }

    private static final class PatternRule implements MaterialRule {
        private final Pattern pattern;

        private PatternRule(String wildcard) {
            this.pattern = Pattern.compile("\\Q" + wildcard.replace("*", "\\E.*\\Q") + "\\E");
        }

        @Override
        public boolean matches(BlockState state) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            return pattern.matcher(id.toString().toLowerCase(Locale.ROOT)).matches();
        }
    }
}
