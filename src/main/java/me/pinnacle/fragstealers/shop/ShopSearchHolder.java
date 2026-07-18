package me.pinnacle.fragstealers.shop;

import me.pinnacle.fragstealers.BlockKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ShopSearchHolder implements InventoryHolder {
    private final BlockKey signKey;
    private final ShopMenuType returnType;
    private final boolean adminOverride;
    private Inventory inventory;

    public ShopSearchHolder(BlockKey signKey, ShopMenuType returnType, boolean adminOverride) {
        this.signKey = signKey;
        this.returnType = returnType;
        this.adminOverride = adminOverride;
    }

    public BlockKey signKey() { return signKey; }
    public ShopMenuType returnType() { return returnType; }
    public boolean adminOverride() { return adminOverride; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
