package me.pinnacle.fragstealers.data;

import me.pinnacle.fragstealers.BlockKey;
import org.bukkit.Material;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class ShopData {
    private final BlockKey signKey;
    private final Set<BlockKey> containerKeys;
    private final UUID ownerUuid;
    private final String ownerName;
    private Material sellMaterial;
    private int sellAmount;
    private Material priceMaterial;
    private int priceAmount;
    private long earnings;

    public ShopData(BlockKey signKey, Set<BlockKey> containerKeys, UUID ownerUuid, String ownerName,
                    Material sellMaterial, int sellAmount, Material priceMaterial, int priceAmount, long earnings) {
        this.signKey = signKey;
        this.containerKeys = new LinkedHashSet<>(containerKeys);
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.sellMaterial = sellMaterial;
        this.sellAmount = sellAmount;
        this.priceMaterial = priceMaterial;
        this.priceAmount = priceAmount;
        this.earnings = earnings;
    }

    public BlockKey signKey() { return signKey; }
    public Set<BlockKey> containerKeys() { return Collections.unmodifiableSet(containerKeys); }
    public boolean addContainer(BlockKey key) { return containerKeys.add(key); }
    public UUID ownerUuid() { return ownerUuid; }
    public String ownerName() { return ownerName; }
    public Material sellMaterial() { return sellMaterial; }
    public int sellAmount() { return sellAmount; }
    public Material priceMaterial() { return priceMaterial; }
    public int priceAmount() { return priceAmount; }
    public long earnings() { return earnings; }
    public boolean isOwner(UUID uuid) { return ownerUuid.equals(uuid); }
    public boolean isConfigured() { return sellMaterial != null && priceMaterial != null && sellAmount > 0 && priceAmount > 0; }

    public void configure(Material sellMaterial, int sellAmount, Material priceMaterial, int priceAmount) {
        this.sellMaterial = sellMaterial;
        this.sellAmount = sellAmount;
        this.priceMaterial = priceMaterial;
        this.priceAmount = priceAmount;
    }

    public void addEarnings(long amount) {
        if (amount <= 0) return;
        earnings = Long.MAX_VALUE - earnings < amount ? Long.MAX_VALUE : earnings + amount;
    }

    public void removeEarnings(long amount) {
        earnings = Math.max(0L, earnings - amount);
    }
}
