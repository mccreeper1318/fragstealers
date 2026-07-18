package me.pinnacle.fragstealers.shop;

import me.pinnacle.fragstealers.BlockKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ShopMenuHolder implements InventoryHolder {
    private final BlockKey signKey;
    private final ShopMenuType type;
    private final int page;
    private final String query;
    private final boolean adminOverride;
    private Inventory inventory;

    public ShopMenuHolder(BlockKey signKey, ShopMenuType type) {
        this(signKey, type, 0, "", false);
    }

    public ShopMenuHolder(BlockKey signKey, ShopMenuType type, boolean adminOverride) {
        this(signKey, type, 0, "", adminOverride);
    }

    public ShopMenuHolder(BlockKey signKey, ShopMenuType type, int page, String query) {
        this(signKey, type, page, query, false);
    }

    public ShopMenuHolder(BlockKey signKey, ShopMenuType type, int page, String query, boolean adminOverride) {
        this.signKey = signKey;
        this.type = type;
        this.page = page;
        this.query = query == null ? "" : query;
        this.adminOverride = adminOverride;
    }

    public BlockKey signKey() { return signKey; }
    public ShopMenuType type() { return type; }
    public int page() { return page; }
    public String query() { return query; }
    public boolean adminOverride() { return adminOverride; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
