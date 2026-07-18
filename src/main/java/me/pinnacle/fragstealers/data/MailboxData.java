package me.pinnacle.fragstealers.data;

import me.pinnacle.fragstealers.BlockKey;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class MailboxData {
    private final BlockKey signKey;
    private final Set<BlockKey> containerKeys;
    private final UUID ownerUuid;
    private final String ownerName;
    private final ItemStack[] items = new ItemStack[54];

    public MailboxData(BlockKey signKey, Set<BlockKey> containerKeys, UUID ownerUuid, String ownerName) {
        this.signKey = signKey;
        this.containerKeys = new LinkedHashSet<>(containerKeys);
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
    }

    public BlockKey signKey() { return signKey; }
    public Set<BlockKey> containerKeys() { return Collections.unmodifiableSet(containerKeys); }
    public boolean addContainer(BlockKey key) { return containerKeys.add(key); }
    public UUID ownerUuid() { return ownerUuid; }
    public String ownerName() { return ownerName; }
    public boolean isOwner(UUID uuid) { return ownerUuid.equals(uuid); }
    public int capacity() { return containerKeys.size() > 1 ? 54 : 27; }

    public ItemStack getItem(int slot) {
        ItemStack item = slot >= 0 && slot < items.length ? items[slot] : null;
        return item == null ? null : item.clone();
    }

    public void setItem(int slot, ItemStack item) {
        if (slot < 0 || slot >= items.length) {
            return;
        }
        items[slot] = item == null || item.getType().isAir() || item.getAmount() <= 0 ? null : item.clone();
    }

    public ItemStack[] copyContents() {
        ItemStack[] copy = new ItemStack[capacity()];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = getItem(i);
        }
        return copy;
    }

    public void replaceContents(ItemStack[] contents) {
        int capacity = capacity();
        for (int i = 0; i < capacity; i++) {
            setItem(i, i < contents.length ? contents[i] : null);
        }
    }

    public boolean hasMail() {
        for (int i = 0; i < capacity(); i++) {
            if (items[i] != null && !items[i].getType().isAir() && items[i].getAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    public int firstEmptySlot() {
        for (int i = 0; i < capacity(); i++) {
            if (items[i] == null || items[i].getType().isAir()) {
                return i;
            }
        }
        return -1;
    }

    public void clear() {
        for (int i = 0; i < items.length; i++) {
            items[i] = null;
        }
    }
}
