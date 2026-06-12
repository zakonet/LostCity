package common.cn.kafei.simukraft.commercial;

import common.cn.kafei.simukraft.SimuKraft;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class CommercialDefinitionSourceResolver {
    private static final String BUNDLED_RESOURCE_ROOT = "/assets/" + SimuKraft.MOD_ID + "/building/commercial/";
    private static final Map<String, List<String>> BUNDLED_MANIFEST_CACHE = new ConcurrentHashMap<>();

    private CommercialDefinitionSourceResolver() {
    }

    /** clearCache: 清理内置商业资源清单缓存。 */
    static void clearCache() {
        BUNDLED_MANIFEST_CACHE.clear();
    }

    /** explicitCommercialPath: 解析 .sk 中 commercial 字段声明的商业 JSON 路径。 */
    @Nullable
    static Path explicitCommercialPath(Path metaPath) {
        if (metaPath == null || metaPath.getParent() == null || !Files.isRegularFile(metaPath)) {
            return null;
        }
        Path directory = metaPath.getParent();
        try {
            for (String rawLine : Files.readAllLines(metaPath, StandardCharsets.UTF_8)) {
                String line = rawLine != null ? rawLine.trim() : "";
                if (!line.regionMatches(true, 0, "commercial:", 0, "commercial:".length())) {
                    continue;
                }
                String fileName = line.substring("commercial:".length()).trim();
                if (!fileName.isBlank()) {
                    Path direct = directory.resolve(fileName);
                    return Files.isRegularFile(direct) ? direct : findCaseInsensitiveSibling(directory, fileName);
                }
            }
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read commercial entry from {}", metaPath, exception);
        }
        return null;
    }

    /** siblingCommercialPath: 按建筑 .sk 同名规则解析相邻商业 JSON 路径。 */
    @Nullable
    static Path siblingCommercialPath(Path metaPath, String metaFileName) {
        if (metaPath == null || metaPath.getParent() == null) {
            return null;
        }
        String fileName = stripExtension(metaFileName) + ".json";
        Path candidate = metaPath.getParent().resolve(fileName);
        if (Files.isRegularFile(candidate)) {
            return candidate;
        }
        return findCaseInsensitiveSibling(metaPath.getParent(), fileName);
    }

    /** resolveBundledResourcePath: 按原始大小写或清单大小写解析内置商业 JSON。 */
    @Nullable
    static String resolveBundledResourcePath(String baseName) {
        String fileName = stripExtension(baseName) + ".json";
        String directPath = bundledResourcePath(fileName);
        if (bundledResourceExists(directPath)) {
            return directPath;
        }
        return bundledCommercialJsonFiles().stream()
                .filter(name -> name.equalsIgnoreCase(fileName))
                .findFirst()
                .map(CommercialDefinitionSourceResolver::bundledResourcePath)
                .orElse(null);
    }

    /** openBundledResource: 打开已解析的内置商业资源输入流。 */
    @Nullable
    static InputStream openBundledResource(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return null;
        }
        return CommercialDefinitionSourceResolver.class.getResourceAsStream(resourcePath);
    }

    /** findCaseInsensitiveSibling: 在大小写敏感文件系统上兼容旧存档或手写配置名。 */
    @Nullable
    private static Path findCaseInsensitiveSibling(Path directory, String fileName) {
        if (directory == null || fileName == null || fileName.isBlank() || !Files.isDirectory(directory)) {
            return null;
        }
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(fileName))
                    .findFirst()
                    .orElse(null);
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to scan commercial definition directory {}", directory, exception);
            return null;
        }
    }

    /** bundledCommercialJsonFiles: 读取内置商业资源清单里的 JSON 文件名。 */
    private static List<String> bundledCommercialJsonFiles() {
        return BUNDLED_MANIFEST_CACHE.computeIfAbsent(BUNDLED_RESOURCE_ROOT, ignored -> readBundledCommercialJsonFiles());
    }

    /** readBundledCommercialJsonFiles: 从 _files.txt 加载内置商业 JSON 文件名。 */
    private static List<String> readBundledCommercialJsonFiles() {
        String manifestPath = bundledResourcePath("_files.txt");
        try (var input = openBundledResource(manifestPath)) {
            if (input == null) {
                return List.of();
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(CommercialDefinitionSourceResolver::stripUtf8Bom)
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .filter(line -> line.toLowerCase(Locale.ROOT).endsWith(".json"))
                    .toList();
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read bundled commercial manifest {}", manifestPath, exception);
            return List.of();
        }
    }

    /** bundledResourceExists: 探测指定内置资源是否存在。 */
    private static boolean bundledResourceExists(String resourcePath) {
        try (var input = openBundledResource(resourcePath)) {
            return input != null;
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to probe bundled commercial resource {}", resourcePath, exception);
            return false;
        }
    }

    /** bundledResourcePath: 拼接内置商业资源路径。 */
    private static String bundledResourcePath(String fileName) {
        return BUNDLED_RESOURCE_ROOT + fileName;
    }

    /** stripExtension: 去掉文件扩展名并保留原始大小写。 */
    private static String stripExtension(String fileName) {
        String safeName = fileName != null ? fileName : "";
        int index = safeName.lastIndexOf('.');
        return index > 0 ? safeName.substring(0, index) : safeName;
    }

    /** stripUtf8Bom: 清理清单首行可能存在的 UTF-8 BOM。 */
    private static String stripUtf8Bom(String value) {
        return value != null && value.startsWith("\uFEFF") ? value.substring(1) : value;
    }
}
