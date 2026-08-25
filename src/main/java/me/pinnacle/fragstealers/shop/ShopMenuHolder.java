package me.pinnacle.fragstealers.shop;

import me.pinnacle.fragstealers.BlockKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ShopMenuHolder implements InventoryHolder {
    private final BlockKey signKey;
    private final ShopMenuType type;
    private final int page;
    private final ItemCatalog.Category category;
    private final ItemCatalog.Group group;
    private final boolean adminOverride;
    private Inventory inventory;

    public ShopMenuHolder(BlockKey signKey, ShopMenuType type) {
        this(signKey, type, 0, null, null, false);
    }

    public ShopMenuHolder(BlockKey signKey, ShopMenuType type, boolean adminOverride) {
        this(signKey, type, 0, null, null, adminOverride);
    }

    public ShopMenuHolder(BlockKey signKey, ShopMenuType type, int page,
                          ItemCatalog.Category category, ItemCatalog.Group group, boolean adminOverride) {
        this.signKey = signKey;
        this.type = type;
        this.page = page;
        this.category = category;
        this.group = group;
        this.adminOverride = adminOverride;
    }

    public BlockKey signKey() { return signKey; }
    public ShopMenuType type() { return type; }
    public int page() { return page; }
    public ItemCatalog.Category category() { return category; }
    public ItemCatalog.Group group() { return group; }
    public boolean adminOverride() { return adminOverride; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
