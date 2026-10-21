package uk.co.pembo.startergrant.storage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Comparator;
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
    public Set<UUID> exportGranted() {
        synchronized (lock) {
            return Set.copyOf(granted);
        }
    }

    @Override
    public int importGranted(Collection<UUID> uuids) {
        synchronized (lock) {
            Path temp = filePath.resolveSibling(filePath.getFileName().toString() + ".migrate-tmp");
            try {
                Path parent = filePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Set<UUID> replacement = new HashSet<>(uuids);
                try (BufferedWriter writer = Files.newBufferedWriter(
                        temp, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    for (UUID uuid : replacement.stream().sorted(Comparator.comparing(UUID::toString)).toList()) {
                        writer.write(uuid.toString());
                        writer.newLine();
                    }
                }
                moveIntoPlace(temp);
                granted.clear();
                granted.addAll(replacement);
                return replacement.size();
            } catch (IOException e) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // The original file is untouched; a leftover temp is reported via the cause below.
                }
                throw new StorageException("Failed to write starter grants file: " + e.getMessage(), e);
            }
        }
    }

    private void moveIntoPlace(Path temp) throws IOException {
        try {
            Files.move(temp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void shutdown() {
        // no-op
    }
}