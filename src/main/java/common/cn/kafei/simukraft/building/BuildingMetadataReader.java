package common.cn.kafei.simukraft.building;

import net.minecraft.core.BlockPos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BuildingMetadataReader {
    private BuildingMetadataReader() {
    }

    public static List<BuildingPoiDefinition> readPoiDefinitions(BuildingCatalog.BuildingDefinition definition) {
        if (definition == null) {
            return List.of();
        }
        return definition.readFileText(definition.metaFileName())
                .map(BuildingMetadataReader::readPoiDefinitions)
                .orElse(List.of());
    }

    public static List<BuildingPoiDefinition> readPoiDefinitions(Path file) {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            return readPoiDefinitions(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static List<BuildingPoiDefinition> readPoiDefinitions(String text) {
        List<BuildingPoiDefinition> definitions = new ArrayList<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!line.regionMatches(true, 0, "poi:", 0, 4)) {
                continue;
            }
            String payload = line.substring(4).trim();
            String[] parts = payload.split(",");
            if (parts.length < 2) {
                continue;
            }
            String poiTypeName = parts[0].trim().toUpperCase(Locale.ROOT);
            int capacity;
            try {
                capacity = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException exception) {
                continue;
            }
            String id = parts.length >= 3 ? parts[2].trim() : poiTypeName.toLowerCase(Locale.ROOT);
            definitions.add(new BuildingPoiDefinition(id, common.cn.kafei.simukraft.city.poi.CityPoiType.fromName(poiTypeName), Math.max(0, capacity)));
        }
        return List.copyOf(definitions);
    }

    public static BlockPos parseSize(String value) {
        if (value == null || value.isBlank()) {
            return BlockPos.ZERO;
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace('×', 'x').replace(' ', 'x');
        String[] parts = normalized.split("x");
        if (parts.length != 3) {
            return BlockPos.ZERO;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException exception) {
            return BlockPos.ZERO;
        }
    }
}
