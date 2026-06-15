package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.logistics.LogisticsChannelData;
import common.cn.kafei.simukraft.logistics.LogisticsClientData;
import common.cn.kafei.simukraft.logistics.LogisticsDirection;
import common.cn.kafei.simukraft.logistics.LogisticsItemFilter;
import common.cn.kafei.simukraft.logistics.LogisticsPortData;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LogisticsSqliteRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsManualLogisticsDataAfterLegacySchemaMigration() throws Exception {
        Path databasePath = tempDir.resolve("simukraft.sqlite");
        createLegacyLogisticsTables(databasePath);
        try (SimuSqliteDatabase db = openDatabase(databasePath)) {
        LogisticsSqliteRepository repository = new LogisticsSqliteRepository(db);

        UUID cityId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        BlockPos warehouseBox = new BlockPos(10, 64, 10);
        BlockPos containerPos = new BlockPos(11, 64, 10);
        BlockPos clientBox = new BlockPos(20, 64, 20);
        BlockPos inputPos = new BlockPos(20, 64, 21);
        LogisticsWarehouseData warehouse = new LogisticsWarehouseData(
                warehouseId, warehouseBox, cityId, "minecraft:overworld", List.of(containerPos), 30L);
        LogisticsClientData client = new LogisticsClientData(
                clientId, clientBox, cityId, "minecraft:overworld", "Input Box", false,
                "logistics_client_box", clientBox.toShortString(),
                List.of(new LogisticsPortData("input", "Input", "manual", inputPos)), 31L);
        LogisticsChannelData channel = new LogisticsChannelData(
                channelId, warehouseId, clientId, LogisticsDirection.CLIENT_TO_WAREHOUSE, "Iron",
                true, List.of(LogisticsItemFilter.item("minecraft:iron_ingot")), 32L, 5, 10);

        repository.saveDimension(rootTag(warehouse, client, channel), "minecraft:overworld");
        CompoundTag loaded = repository.loadDimension("minecraft:overworld");

        assertNotNull(loaded);
        LogisticsWarehouseData loadedWarehouse = LogisticsWarehouseData.fromTag(
                loaded.getList("Warehouses", CompoundTag.TAG_COMPOUND).getCompound(0));
        LogisticsClientData loadedClient = LogisticsClientData.fromTag(
                loaded.getList("Clients", CompoundTag.TAG_COMPOUND).getCompound(0));
        LogisticsChannelData loadedChannel = LogisticsChannelData.fromTag(
                loaded.getList("Channels", CompoundTag.TAG_COMPOUND).getCompound(0));
        assertEquals(warehouseId, loadedWarehouse.warehouseId());
        assertEquals(List.of(containerPos), loadedWarehouse.containers());
        assertEquals(clientId, loadedClient.clientId());
        assertEquals(List.of(new LogisticsPortData("input", "Input", "manual", inputPos)), loadedClient.ports());
        assertEquals(channelId, loadedChannel.channelId());
        assertEquals(LogisticsDirection.CLIENT_TO_WAREHOUSE, loadedChannel.direction());
        assertEquals(List.of(LogisticsItemFilter.item("minecraft:iron_ingot")), loadedChannel.filters());
        assertEquals(5, loadedChannel.keepSourceQuantity());
        assertEquals(10, loadedChannel.keepTargetQuantity());
        }
    }

    @Test
    void savingEmptyDifferentDimensionDoesNotClearExistingLogistics() throws Exception {
        try (SimuSqliteDatabase db = openDatabase(tempDir.resolve("dimension.sqlite"))) {
        LogisticsSqliteRepository repository = new LogisticsSqliteRepository(db);
        UUID cityId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        LogisticsWarehouseData warehouse = new LogisticsWarehouseData(
                warehouseId, new BlockPos(0, 64, 0), cityId, "minecraft:overworld", List.of(new BlockPos(1, 64, 0)), 10L);
        LogisticsClientData client = new LogisticsClientData(
                clientId, new BlockPos(4, 64, 0), cityId, "minecraft:overworld", "Client", false,
                "logistics_client_box", "4, 64, 0", List.of(new LogisticsPortData("output", "Output", "manual", new BlockPos(4, 64, 1))), 11L);
        LogisticsChannelData channel = new LogisticsChannelData(
                UUID.randomUUID(), warehouseId, clientId, LogisticsDirection.WAREHOUSE_TO_CLIENT,
                "Route", true, List.of(), 12L, 0, 0);

        repository.saveDimension(rootTag(warehouse, client, channel), "minecraft:overworld");
        repository.saveDimension(emptyRootTag(), "minecraft:the_nether");

        CompoundTag loaded = repository.loadDimension("minecraft:overworld");
        assertNotNull(loaded);
        assertEquals(1, loaded.getList("Warehouses", CompoundTag.TAG_COMPOUND).size());
        assertEquals(1, loaded.getList("Clients", CompoundTag.TAG_COMPOUND).size());
        assertEquals(1, loaded.getList("Channels", CompoundTag.TAG_COMPOUND).size());
        }
    }

    private static CompoundTag rootTag(LogisticsWarehouseData warehouse, LogisticsClientData client, LogisticsChannelData channel) {
        CompoundTag tag = new CompoundTag();
        ListTag warehouses = new ListTag();
        warehouses.add(warehouse.toTag());
        tag.put("Warehouses", warehouses);
        ListTag clients = new ListTag();
        clients.add(client.toTag());
        tag.put("Clients", clients);
        ListTag channels = new ListTag();
        channels.add(channel.toTag());
        tag.put("Channels", channels);
        return tag;
    }

    private static CompoundTag emptyRootTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("Warehouses", new ListTag());
        tag.put("Clients", new ListTag());
        tag.put("Channels", new ListTag());
        return tag;
    }

    private static SimuSqliteDatabase openDatabase(Path databasePath) throws Exception {
        Constructor<SimuSqliteDatabase> constructor = SimuSqliteDatabase.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(databasePath);
    }

    private static void createLegacyLogisticsTables(Path databasePath) throws Exception {
        Files.createDirectories(databasePath.getParent());
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath().normalize());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE logistics_warehouses(warehouse_id TEXT PRIMARY KEY, box_pos_long INTEGER NOT NULL UNIQUE, city_id TEXT)");
            statement.executeUpdate("CREATE TABLE logistics_clients(client_id TEXT PRIMARY KEY, box_pos_long INTEGER NOT NULL, city_id TEXT)");
            statement.executeUpdate("CREATE TABLE logistics_ports(owner_id TEXT NOT NULL, owner_type TEXT NOT NULL, port_id TEXT NOT NULL, pos_long INTEGER NOT NULL, PRIMARY KEY(owner_id, owner_type, port_id))");
            statement.executeUpdate("CREATE TABLE logistics_channels(channel_id TEXT PRIMARY KEY, warehouse_id TEXT NOT NULL, client_id TEXT NOT NULL, direction TEXT NOT NULL)");
        }
    }
}
