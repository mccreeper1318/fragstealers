package me.pinnacle.fragstealers.data;

import me.pinnacle.fragstealers.BlockKey;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class ChestLock {
    private final Set<BlockKey> containerKeys;
    private final BlockKey signKey;
    private final UUID ownerUuid;
    private final String ownerName;

    public ChestLock(Set<BlockKey> containerKeys, BlockKey signKey, UUID ownerUuid, String ownerName) {
        this.containerKeys = new LinkedHashSet<>(containerKeys);
        this.signKey = signKey;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
    }

    public Set<BlockKey> containerKeys() {
        return Collections.unmodifiableSet(containerKeys);
    }

    public boolean addContainer(BlockKey key) {
        return containerKeys.add(key);
    }

    public BlockKey signKey() {
        return signKey;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public String ownerName() {
        return ownerName;
    }

    public boolean isOwner(UUID uuid) {
        return ownerUuid.equals(uuid);
    }
}
