package uk.co.pembo.startergrant.storage;

import java.util.UUID;

public interface StarterGrantStorage {

    void init();

    boolean hasReceivedStarterGrant(UUID uuid);

    void markStarterGrantGiven(UUID uuid);

    void shutdown();
}