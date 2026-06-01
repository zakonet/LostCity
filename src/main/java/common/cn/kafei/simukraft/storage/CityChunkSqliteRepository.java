package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@SuppressWarnings("null")
public final class CityChunkSqliteRepository {
    private final SimuSqliteDatabase database;

    public CityChunkSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    public synchronized void saveAll(CompoundTag tag) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            SqliteNbtHelper.clearTables(connection, "city_chunks");
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO city_chunks(city_id, chunk_long) VALUES(?, ?)")) {
                ListTag cityTags = tag.getList("CityChunks", CompoundTag.TAG_COMPOUND);
                for (int i = 0; i < cityTags.size(); i++) {
                    CompoundTag cityTag = cityTags.getCompound(i);
                    String cityId = cityTag.getUUID("CityId").toString();
                    ListTag chunks = cityTag.getList("Chunks", LongTag.TAG_LONG);
                    for (int j = 0; j < chunks.size(); j++) {
                        statement.setString(1, cityId);
                        statement.setLong(2, ((LongTag) chunks.get(j)).getAsLong());
                        statement.addBatch();
                    }
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to save city chunks to SQLite", exception);
        }
    }

    public synchronized void upsert(java.util.UUID cityId, long chunkLong) {
        if (cityId == null) {
            return;
        }
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO city_chunks(city_id, chunk_long) VALUES(?, ?)")) {
            statement.setString(1, cityId.toString());
            statement.setLong(2, chunkLong);
            statement.executeUpdate();
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to save city chunk to SQLite", exception);
        }
    }

    public synchronized void deleteCity(java.util.UUID cityId) {
        if (cityId == null) {
            return;
        }
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM city_chunks WHERE city_id = ?")) {
            statement.setString(1, cityId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to delete city chunks from SQLite", exception);
        }
    }

    public synchronized CompoundTag loadAll() {
        CompoundTag tag = new CompoundTag();
        ListTag cityTags = new ListTag();
        try (Connection connection = database.openConnection();
             PreparedStatement cityStatement = connection.prepareStatement("SELECT DISTINCT city_id FROM city_chunks ORDER BY city_id");
             ResultSet cityResult = cityStatement.executeQuery()) {
            while (cityResult.next()) {
                String cityId = cityResult.getString("city_id");
                CompoundTag cityTag = new CompoundTag();
                cityTag.putUUID("CityId", java.util.UUID.fromString(cityId));
                ListTag chunks = new ListTag();
                try (PreparedStatement chunkStatement = connection.prepareStatement("SELECT chunk_long FROM city_chunks WHERE city_id = ? ORDER BY chunk_long")) {
                    chunkStatement.setString(1, cityId);
                    try (ResultSet chunkResult = chunkStatement.executeQuery()) {
                        while (chunkResult.next()) {
                            chunks.add(LongTag.valueOf(chunkResult.getLong("chunk_long")));
                        }
                    }
                }
                cityTag.put("Chunks", chunks);
                cityTags.add(cityTag);
            }
            tag.put("CityChunks", cityTags);
            return cityTags.isEmpty() ? null : tag;
        } catch (SQLException | IllegalArgumentException exception) {
            SimuKraft.LOGGER.error("Failed to load city chunks from SQLite", exception);
            return null;
        }
    }
}
