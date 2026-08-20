package uk.co.pembo.startergrant.storage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class FileStarterGrantStorage implements StarterGrantStorage {

    private final Path filePath;
    private final Logger logger;
    private final Set<UUID> granted = new HashSet<>();
    private final Object lock = new Object();

    public FileStarterGrantStorage(Path filePath, Logger logger) {
        this.filePath = filePath;
        this.logger = logger;
    }

    @Override
    public void init() {
        synchronized (lock) {
            try {
                if (!Files.exists(filePath)) {
                    Files.createDirectories(filePath.getParent());
                    Files.createFile(filePath);
                    return;
                }
                for (String line : Files.readAllLines(filePath, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;
                    try {
                        granted.add(UUID.fromString(trimmed));
                    } catch (IllegalArgumentException e) {
                        logger.warning("Skipping malformed UUID in starter grants file: " + trimmed);
                    }
                }
                logger.info("Loaded " + granted.size() + " starter grant records from file.");
            } catch (IOException e) {
                logger.severe("Failed to load starter grants file: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean hasReceivedStarterGrant(UUID uuid) {
        synchronized (lock) {
            return granted.contains(uuid);
        }
    }

    @Override
    public void markStarterGrantGiven(UUID uuid) {
        synchronized (lock) {
            if (granted.contains(uuid)) return;
            granted.add(uuid);
            try (BufferedWriter writer = Files.newBufferedWriter(
                    filePath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(uuid.toString());
                writer.newLine();
            } catch (IOException e) {
                logger.severe("Failed to persist starter grant for " + uuid + ": " + e.getMessage());
                granted.remove(uuid);
            }
        }
    }

    @Override
    public void shutdown() {
        // no-op
    }
}