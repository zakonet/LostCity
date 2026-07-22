package common.cn.kafei.simukraft.building;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingPackageCatalogTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsBuildingsByScanningPackageCategories() throws Exception {
        writePackage("official_building.zip", List.of(
                entry("buildings/commercial/shop.sk", "name:Shop\namount:12\nstructure:shop.nbt\ndescription:Busy corner shop\ncommercial:shop.json\n"),
                entry("buildings/commercial/shop.nbt", structureNbtBytes(1)),
                entry("buildings/commercial/shop.json", "{}"),
                entry("buildings/industry/factory.sk", "name:Factory\nstructure:factory.nbt\nindustrial:factory.json\n"),
                entry("buildings/industry/factory.nbt", structureNbtBytes(2)),
                entry("buildings/industry/factory.json", "{}")
        ));

        BuildingPackageCatalog.CatalogSnapshot snapshot = BuildingPackageCatalog.scanPackages(tempDir);

        assertEquals(1, snapshot.listBuildings("commercial").size());
        assertEquals(1, snapshot.listBuildings("industry").size());
        assertEquals("Shop", snapshot.listBuildings("commercial").getFirst().displayName());
        assertEquals("Busy corner shop", snapshot.listBuildings("commercial").getFirst().description());
        assertEquals("Factory", snapshot.listBuildings("industry").getFirst().displayName());
    }

    @Test
    void loadsMedicalBuildingsFromPublicCategory() throws Exception {
        writePackage("public.zip", List.of(
                entry("buildings/public/yiyuan.sk", "name:医院\nstructure:yiyuan.nbt\nmedical:yiyuan.json\n"),
                entry("buildings/public/yiyuan.nbt", structureNbtBytes(3)),
                entry("buildings/public/yiyuan.json", "{\"serviceRangeRings\":3}"),
                entry("buildings/public/zhensuo.sk", "name:诊所\nstructure:zhensuo.nbt\nmedical:zhensuo.json\n"),
                entry("buildings/public/zhensuo.nbt", structureNbtBytes(5)),
                entry("buildings/public/zhensuo.json", "{\"serviceRangeRings\":3}")
        ));

        BuildingPackageCatalog.CatalogSnapshot snapshot = BuildingPackageCatalog.scanPackages(tempDir);
        List<BuildingCatalog.BuildingDefinition> definitions = snapshot.listBuildings("public");
        BuildingCatalog.BuildingDefinition hospital = definitions.stream()
                .filter(definition -> "yiyuan.sk".equals(definition.metaFileName()))
                .findFirst()
                .orElseThrow();
        BuildingCatalog.BuildingDefinition clinic = definitions.stream()
                .filter(definition -> "zhensuo.sk".equals(definition.metaFileName()))
                .findFirst()
                .orElseThrow();

        assertEquals(2, definitions.size());
        assertEquals("医院", hospital.displayName());
        assertEquals("public", hospital.category());
        assertEquals("yiyuan.nbt", hospital.structureFileName());
        assertTrue(hospital.hasFile("yiyuan.json"));
        assertEquals("{\"serviceRangeRings\":3}", hospital.readFileText("yiyuan.json").orElseThrow());
        assertEquals("诊所", clinic.displayName());
        assertEquals("zhensuo.nbt", clinic.structureFileName());
        assertTrue(clinic.hasFile("zhensuo.json"));
    }

    @Test
    void laterPackageOverridesSameBuildingName() throws Exception {
        writePackage("a_base.zip", List.of(
                entry("buildings/residential/home.sk", "name:Base Home\nstructure:home.nbt\n"),
                entry("buildings/residential/home.nbt", structureNbtBytes(1))
        ));
        writePackage("z_patch.zip", List.of(
                entry("buildings/residential/home.sk", "name:Patch Home\nstructure:home.nbt\n"),
                entry("buildings/residential/home.nbt", structureNbtBytes(3))
        ));

        BuildingPackageCatalog.CatalogSnapshot snapshot = BuildingPackageCatalog.scanPackages(tempDir);

        assertEquals(1, snapshot.listBuildings("residential").size());
        assertEquals("Patch Home", snapshot.listBuildings("residential").getFirst().displayName());
    }

    @Test
    void rejectsUnsafeEntriesAndIgnoresFilesOutsideKnownCategories() throws Exception {
        writePackage("unsafe.zip", List.of(
                entry("buildings/other/safe.sk", "name:Safe\nstructure:safe.nbt\n"),
                entry("buildings/other/safe.nbt", structureNbtBytes(1)),
                entry("buildings/other/deep/ignored.sk", "name:Ignored\nstructure:ignored.nbt\n"),
                entry("buildings/unknown/ignored.sk", "name:Ignored\nstructure:ignored.nbt\n")
        ));

        BuildingPackageCatalog.CatalogSnapshot snapshot = BuildingPackageCatalog.scanPackages(tempDir);

        assertEquals(1, snapshot.listBuildings("other").size());
        assertTrue(snapshot.listBuildings("public").isEmpty());
    }

    @Test
    void findsBuildingByStructureFileAndLoadsNbtEntry() throws Exception {
        writePackage("official_building.zip", List.of(
                entry("buildings/other/tower.sk", "name:Tower\nstructure:tower.nbt\n"),
                entry("buildings/other/tower.nbt", structureNbtBytes(2))
        ));
        BuildingPackageCatalog.CatalogSnapshot snapshot = BuildingPackageCatalog.scanPackages(tempDir);
        BuildingCatalog.BuildingDefinition definition = snapshot.listBuildings("other").getFirst();

        assertEquals("Tower", definition.displayName());
        assertEquals("tower.nbt", definition.structureFileName());
        Optional<BuildingStructureFileLoader.LoadedStructure> loaded = BuildingStructureFileLoader.load(definition);

        assertTrue(loaded.isPresent());
        assertEquals(BuildingStructureFileLoader.StructureFormat.NBT, loaded.get().format());
        assertEquals(2, loaded.get().blockCount());
        assertFalse(loaded.get().source().isBlank());
    }

    @Test
    void manifestOnlyCalibratesNonSnakeCaseNames() throws Exception {
        writePackage("calibration.zip", List.of(
                entry("buildings/industry/_files.txt", "lumberjacks_house.sk\nlumberjacks_house.nbt\n"),
                entry("buildings/industry/lumberjacksHouse.sk", "name:Lumber\nstructure:lumberjacks_house.nbt\n"),
                entry("buildings/industry/lumberjacksHouse.nbt", structureNbtBytes(1))
        ));

        BuildingPackageCatalog.CatalogSnapshot snapshot = BuildingPackageCatalog.scanPackages(tempDir);

        assertEquals(1, snapshot.listBuildings("industry").size());
        assertEquals("lumberjacksHouse.nbt", snapshot.listBuildings("industry").getFirst().structureFileName());
    }

    private void writePackage(String fileName, List<TestEntry> entries) throws Exception {
        try (ZipOutputStream outputStream = new ZipOutputStream(java.nio.file.Files.newOutputStream(tempDir.resolve(fileName)))) {
            for (TestEntry entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry.name());
                outputStream.putNextEntry(zipEntry);
                outputStream.write(entry.bytes());
                outputStream.closeEntry();
            }
        }
    }

    private static TestEntry entry(String name, String text) {
        return new TestEntry(name, text.getBytes(StandardCharsets.UTF_8));
    }

    private static TestEntry entry(String name, byte[] bytes) {
        return new TestEntry(name, bytes);
    }

    private static byte[] structureNbtBytes(int blockCount) throws Exception {
        CompoundTag root = new CompoundTag();
        ListTag palette = new ListTag();
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:stone");
        palette.add(state);
        ListTag blocks = new ListTag();
        for (int index = 0; index < blockCount; index++) {
            CompoundTag block = new CompoundTag();
            ListTag pos = new ListTag();
            pos.add(IntTag.valueOf(index));
            pos.add(IntTag.valueOf(0));
            pos.add(IntTag.valueOf(0));
            block.put("pos", pos);
            block.putInt("state", 0);
            blocks.add(block);
        }
        root.put("palette", palette);
        root.put("blocks", blocks);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, outputStream);
        return outputStream.toByteArray();
    }

    private record TestEntry(String name, byte[] bytes) {
    }
}

