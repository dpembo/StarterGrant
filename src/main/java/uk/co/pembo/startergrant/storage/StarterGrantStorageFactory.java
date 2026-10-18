package uk.co.pembo.startergrant.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.logging.Logger;

public final class StarterGrantStorageFactory {

    private StarterGrantStorageFactory() {
    }

    public static StarterGrantStorage create(ConfigurationSection config, Path dataFolder,
                                              Logger logger, DataSource sharedDataSource) {
        String type = config.getString("storage.type", "file").toLowerCase();

        if (type.equals("mariadb")) {
            boolean useShared = config.getBoolean("storage.mariadb.use-shared-pool", false);
            String table = config.getString("storage.mariadb.table", "starter_grants");

            if (useShared) {
                if (sharedDataSource == null) {
                    throw new IllegalStateException(
                            "storage.mariadb.use-shared-pool is true but no shared DataSource was provided");
                }
                return new MariaDbStarterGrantStorage(sharedDataSource, table, logger, false);
            }

            HikariConfig hikariConfig = new HikariConfig();
            String host = config.getString("storage.mariadb.host", "localhost");
            int port = config.getInt("storage.mariadb.port", 3306);
            String database = config.getString("storage.mariadb.database");
            hikariConfig.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database);
            hikariConfig.setUsername(config.getString("storage.mariadb.username"));
            hikariConfig.setPassword(config.getString("storage.mariadb.password"));
            hikariConfig.setMaximumPoolSize(2);
            hikariConfig.setMaxLifetime(config.getLong("storage.mariadb.max-lifetime", 540000L));
            hikariConfig.setPoolName("StarterGrant");

            return new MariaDbStarterGrantStorage(new HikariDataSource(hikariConfig), table, logger, true);
        }

        String relativePath = config.getString("storage.file.path", "starter_grants.txt");
        return new FileStarterGrantStorage(dataFolder.resolve(relativePath), logger);
    }
}