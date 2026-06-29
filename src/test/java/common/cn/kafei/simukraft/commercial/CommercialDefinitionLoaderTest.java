package common.cn.kafei.simukraft.commercial;

import common.cn.kafei.simukraft.building.BuildingPackageCatalog;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommercialDefinitionLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsBundledCommercialJsonIgnoringSavedFileCase() throws Exception {
        refreshOfficialPackageInGameDir();
        CommercialDefinitionLoader.clearCache();
        PlacedBuildingRecord building = new PlacedBuildingRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "minecraft:overworld",
                "commerce",
                "jcsd",
                "建材商店",
                "",
                "JCSD.nbt",
                "north",
                BlockPos.ZERO,
                BlockPos.ZERO,
                BlockPos.ZERO,
                BlockPos.ZERO,
                0L,
                List.of(),
                List.of(),
                List.of()
        );

        CommercialDefinitionLoader.LoadResult result = CommercialDefinitionLoader.loadForBuilding(building);

        assertTrue(result.valid(), () -> "Loader errors: " + result.errors());
        assertEquals("JCSD", result.definition().id());
        assertNotNull(result.definition().offerById("buy_oak_planks"));
    }

    @Test
    void loadsNewInfiniteFoodOffer() throws Exception {
        CommercialDefinition definition = load("""
                {
                  "id": "snack_shop",
                  "name": "Snack Shop",
                  "job": { "id": "snack_worker", "name": "Snack Worker", "heldItem": "minecraft:bread" },
                  "offers": [
                    {
                      "id": "buy_bread",
                      "visibleTo": "mixed",
                      "cost": [{ "money": 1.0 }],
                      "result": [{ "item": "minecraft:bread", "count": 1 }]
                    }
                  ]
                }
                """);

        CommercialOffer offer = definition.offerById("buy_bread");
        assertNotNull(offer);
        assertTrue(offer.visibleToNpc());
        assertTrue(offer.visibleToPlayer());
        assertNull(offer.stock());
    }

    @Test
    void loadsNewMaterialBackedStock() throws Exception {
        CommercialDefinition definition = load("""
                {
                  "id": "bread_shop",
                  "name": "Bread Shop",
                  "offers": [
                    {
                      "id": "buy_bread",
                      "visibleTo": "npc",
                      "cost": [{ "money": 0.25 }],
                      "result": [{ "item": "minecraft:bread", "count": 1 }],
                      "stock": {
                        "item": "minecraft:bread",
                        "max": 64,
                        "materials": [{ "item": "minecraft:wheat", "count": 3 }]
                      }
                    }
                  ]
                }
                """);

        CommercialOffer offer = definition.offerById("buy_bread");
        assertNotNull(offer);
        assertNotNull(offer.stock());
        assertTrue(offer.stock().materialBacked());
        assertFalse(offer.stock().sqliteBacked());
        assertEquals("minecraft:wheat", offer.stock().materials().getFirst().itemId());
        assertEquals(3, offer.stock().materials().getFirst().count());
    }

    @Test
    void convertsLegacyRequiredMaterials() throws Exception {
        CommercialDefinition definition = load("""
                {
                  "buildingId": "breadShop",
                  "buildingName": "面包店",
                  "jobType": "breadShopOwner",
                  "jobName": "面包店老板",
                  "shopMode": "NPC_SELL",
                  "heldItem": "minecraft:bread",
                  "workTime": { "start": 100, "end": 12000 },
                      "trades": [
                        {
                          "item": "minecraft:bread",
                          "sellPrice": 0.25,
                          "retail": true,
                          "maxStock": 64,
                          "restockAmount": 32,
                          "requiredMaterials": [{ "item": "minecraft:wheat", "count": 3 }]
                    }
                  ],
                  "buyTrades": []
                }
                """);

        assertEquals("breadShop", definition.id());
        assertEquals("breadShopOwner", definition.job().id());
        assertTrue(definition.workTime().openAt(100));
        assertFalse(definition.workTime().openAt(13000));
        CommercialOffer offer = definition.offers().getFirst();
        assertTrue(offer.visibleToNpc());
        assertEquals(0.25D, offer.cost().getFirst().money());
        assertEquals("minecraft:bread", offer.result().getFirst().itemId());
        assertEquals(1, offer.result().getFirst().count());
        assertTrue(offer.stock().materialBacked());
        assertEquals("minecraft:wheat", offer.stock().materials().getFirst().itemId());
    }

    @Test
    void convertsLegacyWholesalePricesAsStackGroups() throws Exception {
        CommercialDefinition definition = load("""
                {
                  "buildingId": "building_materials",
                  "buildingName": "Building Materials",
                  "shopMode": "MIXED",
                  "trades": [
                    {
                      "item": "minecraft:oak_planks",
                      "sellPrice": 2.24,
                      "maxStock": 512,
                      "restockAmount": 256
                    }
                  ],
                  "buyTrades": [
                    {
                      "item": "minecraft:oak_log",
                      "buyPrice": 7.16,
                      "maxBuyAmount": 256
                    }
                  ]
                }
                """);

        CommercialOffer sellOffer = definition.offerById("sell_oak_planks_0");
        assertNotNull(sellOffer);
        assertEquals(2.24D, sellOffer.cost().getFirst().money());
        assertEquals(64, sellOffer.result().getFirst().count());

        CommercialOffer buyOffer = definition.offerById("buy_oak_log_0");
        assertNotNull(buyOffer);
        assertEquals(64, buyOffer.cost().getFirst().count());
        assertEquals(7.16D, buyOffer.result().getFirst().money());
        assertEquals(16384, buyOffer.stock().max());
    }

    @Test
    void infersLegacyCookedFoodMaterialsFromGlobalList() throws Exception {
        CommercialDefinition definition = load("""
                {
                  "buildingId": "seaboatShoping",
                  "buildingName": "海船之商",
                  "shopMode": "MIXED",
                  "requireMaterialsForSale": true,
                  "materials": [
                    { "item": "minecraft:cod", "count": 1 },
                    { "item": "minecraft:salmon", "count": 1 }
                  ],
                  "trades": [
                    { "item": "minecraft:cooked_cod", "sellPrice": 0.5, "maxStock": 64 },
                    { "item": "minecraft:cooked_salmon", "sellPrice": 0.5, "maxStock": 64 }
                  ],
                  "buyTrades": []
                }
                """);

        CommercialOffer cod = definition.offers().get(0);
        CommercialOffer salmon = definition.offers().get(1);
        assertEquals("minecraft:cod", cod.stock().materials().getFirst().itemId());
        assertEquals("minecraft:salmon", salmon.stock().materials().getFirst().itemId());
        assertTrue(cod.visibleToNpc());
        assertTrue(cod.visibleToPlayer());
    }

    @Test
    void configuredFoodSalesUseMaterialBackedStock() throws Exception {
        assertMaterial("breadShop.json", "shop_sells_bread", "minecraft:wheat", 3);
        assertMaterial("fruitShop.json", "shop_sells_melon_slice", "minecraft:melon_slice", 1);
        assertMaterial("fruitShop.json", "shop_sells_pumpkin", "minecraft:pumpkin", 1);
        assertMaterial("meatShop.json", "shop_sells_cooked_beef", "minecraft:beef", 1);
        assertMaterial("meatShop.json", "shop_sells_cooked_porkchop", "minecraft:porkchop", 1);
        assertMaterial("meatStand.json", "shop_sells_cooked_chicken", "minecraft:chicken", 1);
        assertMaterial("seaboatShoping.json", "shop_sells_cooked_cod", "minecraft:cod", 1);
        assertMaterial("seaboatShoping.json", "shop_sells_cooked_salmon", "minecraft:salmon", 1);
        assertMoney("seaboatShoping.json", "shop_sells_cooked_salmon", 50.0D);
        assertMoney("JCSD.json", "buy_oak_planks", 2.24D);
        assertResultMoney("JCSD.json", "sell_oak_planks", 1.79D);
        assertResultItemCount("lumberjacksHome.json", "shop_sells_oak_log", 64);
        assertCostItemCount("lumberjacksHome.json", "shop_buys_oak_sapling", 64);
        assertResultMoney("lumberjacksHome.json", "shop_buys_oak_sapling", 0.53D);
        assertMaterial("SimuFriedChicken.json", "shop_sells_cooked_chicken", "minecraft:chicken", 1);
        assertMaterial("SimuFriedChicken.json", "shop_sells_bread", "minecraft:wheat", 3);
        assertMaterial("SimuFriedChicken.json", "shop_sells_baked_potato", "minecraft:potato", 1);

        CommercialOffer cake = load(commercialResource("breadShop.json")).offerById("shop_sells_cake");
        assertNotNull(cake);
        assertNotNull(cake.stock());
        assertFalse(cake.stock().materialBacked());
    }

    private void assertMaterial(String fileName, String offerId, String itemId, int count) throws Exception {
        CommercialOffer offer = load(commercialResource(fileName)).offerById(offerId);
        assertNotNull(offer);
        assertNotNull(offer.stock());
        assertTrue(offer.stock().materialBacked(), offerId);
        assertEquals(itemId, offer.stock().materials().getFirst().itemId());
        assertEquals(count, offer.stock().materials().getFirst().count());
    }

    /** assertMoney: 校验指定商业报价的资金成本。 */
    private void assertMoney(String fileName, String offerId, double amount) throws Exception {
        CommercialOffer offer = load(commercialResource(fileName)).offerById(offerId);
        assertNotNull(offer);
        assertEquals(amount, offer.cost().getFirst().money());
    }

    /** assertResultMoney: 校验指定商业报价的资金产出。 */
    private void assertResultMoney(String fileName, String offerId, double amount) throws Exception {
        CommercialOffer offer = load(commercialResource(fileName)).offerById(offerId);
        assertNotNull(offer);
        assertEquals(amount, offer.result().getFirst().money());
    }

    /** assertResultItemCount: 校验指定商业报价的物品产出数量。 */
    private void assertResultItemCount(String fileName, String offerId, int count) throws Exception {
        CommercialOffer offer = load(commercialResource(fileName)).offerById(offerId);
        assertNotNull(offer);
        assertEquals(count, offer.result().getFirst().count());
    }

    /** assertCostItemCount: 校验指定商业报价的物品成本数量。 */
    private void assertCostItemCount(String fileName, String offerId, int count) throws Exception {
        CommercialOffer offer = load(commercialResource(fileName)).offerById(offerId);
        assertNotNull(offer);
        assertEquals(count, offer.cost().getFirst().count());
    }

    private Path commercialResource(String fileName) throws Exception {
        Path packageFile = copyOfficialPackage();
        Path file = tempDir.resolve("commercial_resource_" + System.nanoTime() + "_" + fileName);
        try (ZipFile zipFile = new ZipFile(packageFile.toFile())) {
            var entry = zipFile.getEntry("buildings/commercial/" + fileName);
            assertNotNull(entry);
            try (var input = zipFile.getInputStream(entry)) {
                Files.copy(input, file);
            }
        }
        return file;
    }

    private Path copyOfficialPackage() throws Exception {
        Path packageFile = tempDir.resolve("official_building_" + System.nanoTime() + ".zip");
        try (var input = CommercialDefinitionLoaderTest.class.getResourceAsStream("/assets/simukraft/building/official_building.zip")) {
            assertNotNull(input);
            Files.copy(input, packageFile);
        }
        return packageFile;
    }

    private void refreshOfficialPackageInGameDir() throws Exception {
        Path root = BuildingPackageCatalog.rootDirectory();
        Files.createDirectories(root);
        Path packageFile = root.resolve(BuildingPackageCatalog.OFFICIAL_PACKAGE_NAME);
        Files.deleteIfExists(packageFile);
        try (var input = CommercialDefinitionLoaderTest.class.getResourceAsStream("/assets/simukraft/building/official_building.zip")) {
            assertNotNull(input);
            Files.copy(input, packageFile);
        }
        BuildingPackageCatalog.clearCache();
    }

    private CommercialDefinition load(String json) throws Exception {
        Path file = tempDir.resolve("commercial_" + System.nanoTime() + ".json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return load(file);
    }

    private CommercialDefinition load(Path file) throws Exception {
        CommercialDefinitionLoader.LoadResult result = CommercialDefinitionLoader.load(file);
        assertTrue(result.valid(), () -> "Loader errors: " + result.errors());
        return result.definition();
    }
}
