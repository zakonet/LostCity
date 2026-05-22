package common.cn.kafei.simukraft.building;

import client.cn.kafei.simukraft.client.buildbox.BuildingCacheService;
import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("null")
public final class BuildingStructureService {
    private BuildingStructureService() {
    }

    public static Optional<BuildingStructure> loadStructure(BuildingCacheService.BuildingMeta meta) {
        if (meta == null) {
            return Optional.empty();
        }
        return loadStructure(new BuildingCatalog.BuildingDefinition(
                meta.category(),
                meta.name(),
                meta.size(),
                meta.amount(),
                meta.author(),
                meta.metaFileName(),
                meta.structureFileName(),
                BuildingCacheService.categoryDirectory(meta.category()).resolve(meta.metaFileName()),
                BuildingCacheService.categoryDirectory(meta.category()).resolve(meta.structureFileName())
        ));
    }

    public static Optional<BuildingStructure> loadStructure(String category, String buildingFileName) {
        return BuildingCatalog.findBuilding(category, buildingFileName).flatMap(BuildingStructureService::loadStructure);
    }

    public static Optional<BuildingStructure> loadStructure(BuildingCatalog.BuildingDefinition definition) {
        if (definition == null) {
            return Optional.empty();
        }
        Optional<BuildingStructureFileLoader.LoadedStructure> loaded = BuildingStructureFileLoader.load(definition);
        if (loaded.isEmpty()) {
            return Optional.empty();
        }
        List<BuildingBlockData> blocks = parseBlocks(loaded.get().rootTag());
        if (blocks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BuildingStructure(
                definition.category(),
                definition.displayName(),
                stripExtension(definition.metaFileName()),
                definition.structureFileName(),
                definition.author(),
                definition.size(),
                BuildingMetadataReader.parseSize(definition.size()),
                List.copyOf(blocks),
                BuildingMetadataReader.readPoiDefinitions(definition),
                BlockPos.ZERO,
                blocks.size()
        ));
    }

    public static List<BuildingBlockData> resolvePlacedBlocks(BuildingStructure structure, BlockPos origin, int rotationDegrees) {
        List<BuildingBlockData> placed = new ArrayList<>();
        for (BuildingBlockData block : structure.blocks()) {
            BlockPos rotated = BuildingTransform.rotatePosition(block.relativePos(), rotationDegrees);
            BlockState rotatedState = BuildingTransform.rotateState(block.state(), rotationDegrees);
            placed.add(new BuildingBlockData(origin.offset(rotated), rotatedState, block.originalStructurePos()));
        }
        return List.copyOf(placed);
    }

    private static List<BuildingBlockData> parseBlocks(CompoundTag rootTag) {
        List<BuildingBlockData> blocks = new ArrayList<>();
        if (rootTag.contains("Schematic", Tag.TAG_COMPOUND)) {
            return parseBlocks(rootTag.getCompound("Schematic"));
        }
        if (rootTag.contains("blocks", Tag.TAG_LIST) && rootTag.contains("palette", Tag.TAG_LIST)) {
            ListTag palette = rootTag.getList("palette", Tag.TAG_COMPOUND);
            ListTag blockTags = rootTag.getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < blockTags.size(); i++) {
                CompoundTag blockTag = blockTags.getCompound(i);
                if (!blockTag.contains("pos", Tag.TAG_LIST)) {
                    continue;
                }
                ListTag posList = blockTag.getList("pos", Tag.TAG_INT);
                if (posList.size() < 3) {
                    continue;
                }
                int x = posList.getInt(0);
                int y = posList.getInt(1);
                int z = posList.getInt(2);
                int stateIndex = blockTag.getInt("state");
                if (stateIndex < 0 || stateIndex >= palette.size()) {
                    continue;
                }
                BlockState state = parseState(palette.getCompound(stateIndex));
                if (state == null) {
                    continue;
                }
                BlockPos relative = new BlockPos(x, y, z);
                blocks.add(new BuildingBlockData(relative, state, relative));
            }
        }
        return blocks;
    }

    private static BlockState parseState(CompoundTag stateTag) {
        String name = stateTag.getString("Name");
        if (name == null || name.isBlank()) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(name)).orElse(null);
        if (block == null) {
            SimuKraft.LOGGER.warn("Simukraft: Missing block {} while loading structure", name);
            return null;
        }
        BlockState state = block.defaultBlockState();
        if (stateTag.contains("Properties", Tag.TAG_COMPOUND)) {
            CompoundTag properties = stateTag.getCompound("Properties");
            for (String key : properties.getAllKeys()) {
                Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
                if (property == null) {
                    continue;
                }
                state = applyProperty(state, property, properties.getString(key));
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    private static String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }
}
