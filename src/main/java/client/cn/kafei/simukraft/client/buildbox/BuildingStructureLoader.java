package client.cn.kafei.simukraft.client.buildbox;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public final class BuildingStructureLoader {
    private BuildingStructureLoader() {
    }

    public static Optional<LoadedStructure> load(BuildingCacheService.BuildingMeta meta) {
        Path file = BuildingCacheService.categoryDirectory(meta.category()).resolve(meta.structureFileName());
        if (!Files.isRegularFile(file)) {
            SimuKraft.LOGGER.warn("Simukraft: Structure file not found {}", file);
            return Optional.empty();
        }
        StructureFormat format = StructureFormat.fromFileName(file.getFileName().toString());
        try {
            CompoundTag rootTag = readCompressedOrPlain(file);
            ParsedStructureInfo parsedInfo = parseStructureInfo(rootTag, format, file.getFileName().toString());
            return Optional.of(new LoadedStructure(file, parsedInfo.format(), rootTag, parsedInfo.blockCount(), parsedInfo.size()));
        } catch (Exception exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to load structure file {} with format {}", file, format, exception);
            return Optional.empty();
        }
    }

    private static CompoundTag readCompressedOrPlain(Path file) throws IOException {
        try {
            return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException compressedException) {
            try (InputStream inputStream = Files.newInputStream(file);
                 DataInputStream dataInputStream = new DataInputStream(inputStream)) {
                return NbtIo.read(dataInputStream, NbtAccounter.unlimitedHeap());
            }
        }
    }

    private static ParsedStructureInfo parseStructureInfo(CompoundTag rootTag, StructureFormat fallbackFormat, String fileName) {
        if (rootTag.contains("Schematic", Tag.TAG_COMPOUND)) {
            CompoundTag schematicTag = rootTag.getCompound("Schematic");
            return parseStructureInfo(schematicTag, StructureFormat.SCHEMATIC, fileName);
        }

        if (isLitematic(rootTag)) {
            CompoundTag metadataTag = rootTag.getCompound("Metadata");
            BlockPos size = extractLitematicSize(metadataTag);
            int blockCount = Math.max(0, metadataTag.getInt("TotalBlocks"));
            return new ParsedStructureInfo(StructureFormat.LITEMATIC, blockCount, size);
        }

        if (isSchematica(rootTag)) {
            BlockPos size = extractClassicSchematicSize(rootTag);
            int blockCount = rootTag.getByteArray("Blocks").length;
            return new ParsedStructureInfo(StructureFormat.SCHEMATIC, blockCount, size);
        }

        if (isSpongeSchem(rootTag)) {
            BlockPos size = extractClassicSchematicSize(rootTag);
            int blockCount = estimateVolume(size);
            return new ParsedStructureInfo(StructureFormat.SCHEM, blockCount, size);
        }

        if (isVanillaStructure(rootTag)) {
            ListTag blocks = rootTag.getList("blocks", Tag.TAG_COMPOUND);
            BlockPos size = extractStructureBlockBounds(blocks);
            return new ParsedStructureInfo(StructureFormat.NBT, blocks.size(), size);
        }

        return new ParsedStructureInfo(fallbackFormat, 0, BlockPos.ZERO);
    }

    private static boolean isLitematic(CompoundTag rootTag) {
        return rootTag.contains("Metadata", Tag.TAG_COMPOUND) && rootTag.contains("Regions", Tag.TAG_COMPOUND);
    }

    private static boolean isSchematica(CompoundTag rootTag) {
        return rootTag.contains("Blocks", Tag.TAG_BYTE_ARRAY)
                && hasClassicDimensions(rootTag)
                && rootTag.contains("Materials", Tag.TAG_STRING);
    }

    private static boolean isSpongeSchem(CompoundTag rootTag) {
        return rootTag.contains("Palette", Tag.TAG_COMPOUND)
                && rootTag.contains("BlockData", Tag.TAG_BYTE_ARRAY)
                && hasClassicDimensions(rootTag);
    }

    private static boolean isVanillaStructure(CompoundTag rootTag) {
        return rootTag.contains("blocks", Tag.TAG_LIST) && rootTag.contains("palette", Tag.TAG_LIST);
    }

    private static boolean hasClassicDimensions(CompoundTag rootTag) {
        return (rootTag.contains("Width", Tag.TAG_SHORT) || rootTag.contains("Width", Tag.TAG_INT))
                && (rootTag.contains("Height", Tag.TAG_SHORT) || rootTag.contains("Height", Tag.TAG_INT))
                && (rootTag.contains("Length", Tag.TAG_SHORT) || rootTag.contains("Length", Tag.TAG_INT));
    }

    private static BlockPos extractLitematicSize(CompoundTag metadataTag) {
        if (metadataTag.contains("EnclosingSize", Tag.TAG_COMPOUND)) {
            CompoundTag sizeTag = metadataTag.getCompound("EnclosingSize");
            return new BlockPos(sizeTag.getInt("x"), sizeTag.getInt("y"), sizeTag.getInt("z"));
        }
        return BlockPos.ZERO;
    }

    private static BlockPos extractClassicSchematicSize(CompoundTag rootTag) {
        int width = rootTag.contains("Width", Tag.TAG_SHORT) ? rootTag.getShort("Width") : rootTag.getInt("Width");
        int height = rootTag.contains("Height", Tag.TAG_SHORT) ? rootTag.getShort("Height") : rootTag.getInt("Height");
        int length = rootTag.contains("Length", Tag.TAG_SHORT) ? rootTag.getShort("Length") : rootTag.getInt("Length");
        return new BlockPos(width, height, length);
    }

    private static BlockPos extractStructureBlockBounds(ListTag blocks) {
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag blockTag = blocks.getCompound(i);
            if (blockTag.contains("pos", Tag.TAG_LIST)) {
                ListTag posList = blockTag.getList("pos", Tag.TAG_INT);
                if (posList.size() >= 3) {
                    maxX = Math.max(maxX, posList.getInt(0));
                    maxY = Math.max(maxY, posList.getInt(1));
                    maxZ = Math.max(maxZ, posList.getInt(2));
                }
            }
        }
        return new BlockPos(maxX + 1, maxY + 1, maxZ + 1);
    }

    private static int estimateVolume(BlockPos size) {
        return Math.max(0, size.getX()) * Math.max(0, size.getY()) * Math.max(0, size.getZ());
    }

    public enum StructureFormat {
        NBT,
        SCHEM,
        SCHEMATIC,
        LITEMATIC,
        UNKNOWN;

        public static StructureFormat fromFileName(String fileName) {
            String lowerName = fileName.toLowerCase(Locale.ROOT);
            if (lowerName.endsWith(".litematic")) {
                return LITEMATIC;
            }
            if (lowerName.endsWith(".schematic")) {
                return SCHEMATIC;
            }
            if (lowerName.endsWith(".schem")) {
                return SCHEM;
            }
            if (lowerName.endsWith(".nbt")) {
                return NBT;
            }
            return UNKNOWN;
        }
    }

    private record ParsedStructureInfo(StructureFormat format, int blockCount, BlockPos size) {
    }

    public record LoadedStructure(Path file, StructureFormat format, CompoundTag rootTag, int blockCount, BlockPos size) {
    }
}
