package uk.co.pembo.startergrant.storage;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class MariaDbStarterGrantStorage implements StarterGrantStorage {

    private final DataSource dataSource;
    private final String table;
    private final Logger logger;
    private final boolean ownsDataSource;

    public MariaDbStarterGrantStorage(DataSource dataSource, String table, Logger logger, boolean ownsDataSource) {
        this.dataSource = dataSource;
        this.table = table;
        this.logger = logger;
        this.ownsDataSource = ownsDataSource;
    }

    @Override
    public void init() {
        String sql = "CREATE TABLE IF NOT EXISTS " + table + " (" +
                "player_uuid CHAR(36) NOT NULL, " +
                "granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (player_uuid))";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        } catch (SQLException e) {
            logger.severe("Failed to create " + table + " table: " + e.getMessage());
        }
    }

    @Override
    public boolean hasReceivedStarterGrant(UUID uuid) {
        String sql = "SELECT 1 FROM " + table + " WHERE player_uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.severe("Failed to check starter grant for " + uuid + ": " + e.getMessage());
            return true; // fail closed — never double-grant on DB error
        }
    }

    @Override
    public void markStarterGrantGiven(UUID uuid) {
        String sql = "INSERT IGNORE INTO " + table + " (player_uuid) VALUES (?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to record starter grant for " + uuid + ": " + e.getMessage());
        }
    }

    @Override
    public Set<UUID> exportGranted() {
        String sql = "SELECT player_uuid FROM " + table;
        Set<UUID> result = new HashSet<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String value = rs.getString(1);
                try {
                    result.add(UUID.fromString(value));
                } catch (IllegalArgumentException e) {
                    logger.warning("Skipping malformed UUID in " + table + ": " + value);
                }
            }
            return result;
        } catch (SQLException e) {
            throw new StorageException("Failed to read starter grants from " + table + ": " + e.getMessage(), e);
        }
    }

    @Override
    public int importGranted(Collection<UUID> uuids) {
        if (uuids.isEmpty()) {
            return 0;
        }
        String sql = "INSERT IGNORE INTO " + table + " (player_uuid) VALUES (?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int batched = 0;
            int inserted = 0;
            for (UUID uuid : uuids) {
                ps.setString(1, uuid.toString());
                ps.addBatch();
                batched++;
                if (batched % 500 == 0) {
                    inserted += countInserted(ps.executeBatch());
                }
            }
            if (batched % 500 != 0) {
                inserted += countInserted(ps.executeBatch());
            }
            return inserted;
        } catch (SQLException e) {
            throw new StorageException("Failed to write starter grants to " + table + ": " + e.getMessage(), e);
        }
    }

    private static int countInserted(int[] counts) {
        int inserted = 0;
        for (int count : counts) {
            if (count > 0 || count == Statement.SUCCESS_NO_INFO) {
                inserted += count > 0 ? count : 1;
            }
        }
        return inserted;
    }

    @Override
    public void shutdown() {
        if (ownsDataSource && dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception e) {
                logger.warning("Failed to close starter grant DataSource: " + e.getMessage());
            }
        }
    }
}