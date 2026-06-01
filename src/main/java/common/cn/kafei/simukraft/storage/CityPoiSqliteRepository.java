package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@SuppressWarnings("null")
public final class CityPoiSqliteRepository {
    private final SimuSqliteDatabase database;

    public CityPoiSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    public synchronized void saveAll(CompoundTag tag) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            SqliteNbtHelper.clearTables(connection, "city_pois");
            try {
                ListTag pois = tag.getList("Pois", CompoundTag.TAG_COMPOUND);
                for (int i = 0; i < pois.size(); i++) {
                    savePoi(connection, pois.getCompound(i));
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to save city POIs to SQLite", exception);
        }
    }

    public synchronized void upsert(CompoundTag poiTag) {
        try (Connection connection = database.openConnection()) {
            savePoi(connection, poiTag);
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to save city POI to SQLite", exception);
        }
    }

    public synchronized void deleteCity(java.util.UUID cityId) {
        if (cityId == null) {
            return;
        }
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM city_pois WHERE city_id = ?")) {
            statement.setString(1, cityId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to delete city POIs from SQLite", exception);
        }
    }

    public synchronized CompoundTag loadAll() {
        CompoundTag tag = new CompoundTag();
        ListTag pois = new ListTag();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM city_pois ORDER BY poi_id");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                CompoundTag poi = new CompoundTag();
                poi.putUUID("PoiId", java.util.UUID.fromString(resultSet.getString("poi_id")));
                poi.putUUID("CityId", java.util.UUID.fromString(resultSet.getString("city_id")));
                poi.putLong("Pos", resultSet.getLong("pos_long"));
                poi.putString("Type", resultSet.getString("type"));
                poi.putInt("Capacity", resultSet.getInt("capacity"));
                poi.putBoolean("Active", resultSet.getInt("active") != 0);
                pois.add(poi);
            }
            tag.put("Pois", pois);
            return pois.isEmpty() ? null : tag;
        } catch (SQLException | IllegalArgumentException exception) {
            SimuKraft.LOGGER.error("Failed to load city POIs from SQLite", exception);
            return null;
        }
    }

    private void savePoi(Connection connection, CompoundTag poi) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO city_pois(poi_id, city_id, pos_long, type, capacity, active) VALUES(?, ?, ?, ?, ?, ?) ON CONFLICT(poi_id) DO UPDATE SET city_id = excluded.city_id, pos_long = excluded.pos_long, type = excluded.type, capacity = excluded.capacity, active = excluded.active")) {
            statement.setString(1, poi.getUUID("PoiId").toString());
            statement.setString(2, poi.getUUID("CityId").toString());
            statement.setLong(3, poi.getLong("Pos"));
            statement.setString(4, poi.getString("Type"));
            statement.setInt(5, poi.getInt("Capacity"));
            statement.setInt(6, poi.getBoolean("Active") ? 1 : 0);
            statement.executeUpdate();
        }
    }
}
