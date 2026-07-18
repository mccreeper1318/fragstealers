package me.pinnacle.fragstealers.mail;

import me.pinnacle.fragstealers.BlockKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class MailboxMenuHolder implements InventoryHolder {
    private final BlockKey signKey;
    private final MailboxMenuType type;
    private final boolean[] blockedSlots;
    private final ItemStack[] before;
    private final boolean adminOverride;
    private Inventory inventory;

    public MailboxMenuHolder(BlockKey signKey, MailboxMenuType type, boolean[] blockedSlots, ItemStack[] before, boolean adminOverride) {
        this.signKey = signKey;
        this.type = type;
        this.blockedSlots = blockedSlots == null ? new boolean[0] : blockedSlots.clone();
        this.before = cloneItems(before);
        this.adminOverride = adminOverride;
    }

    public BlockKey signKey() { return signKey; }
    public MailboxMenuType type() { return type; }
    public boolean blocked(int slot) { return slot >= 0 && slot < blockedSlots.length && blockedSlots[slot]; }
    public ItemStack[] before() { return cloneItems(before); }
    public boolean adminOverride() { return adminOverride; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        if (items == null) return new ItemStack[0];
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) copy[i] = items[i] == null ? null : items[i].clone();
        return copy;
    }
}
