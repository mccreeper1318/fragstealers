package me.pinnacle.fragstealers;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class ChestLock {
    private final Set<BlockKey> chestKeys;
    private final BlockKey signKey;
    private final UUID ownerUuid;
    private final String ownerName;

    public ChestLock(Set<BlockKey> chestKeys, BlockKey signKey, UUID ownerUuid, String ownerName) {
        this.chestKeys = Collections.unmodifiableSet(new LinkedHashSet<>(chestKeys));
        this.signKey = signKey;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
    }

    public Set<BlockKey> chestKeys() {
        return chestKeys;
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
}
