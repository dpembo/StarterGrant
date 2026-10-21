package uk.co.pembo.startergrant.storage;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface StarterGrantStorage {

    void init();

    boolean hasReceivedStarterGrant(UUID uuid);

    void markStarterGrantGiven(UUID uuid);

    /**
     * Every player UUID recorded by this backend.
     */
    Set<UUID> exportGranted();

    /**
     * Writes player UUIDs into this backend.
     * File storage replaces the file so it contains exactly these UUIDs.
     * MariaDB inserts any UUID that is not already present and leaves existing rows in place.
     *
     * @return how many UUIDs were written (file: the full set size; MariaDB: newly inserted rows)
     */
    int importGranted(Collection<UUID> uuids);

    void shutdown();
}