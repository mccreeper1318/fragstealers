package me.pinnacle.fragstealers.shop;

import me.pinnacle.fragstealers.BlockKey;
import me.pinnacle.fragstealers.FragStealers;
import me.pinnacle.fragstealers.ProtectionType;
import me.pinnacle.fragstealers.data.ShopData;
import me.pinnacle.fragstealers.data.ShopManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ShopMenuService {
    public static final int OWNER_STOCK_SLOT = 11;
    public static final int OWNER_COLLECT_SLOT = 15;
    public static final int BUY_SLOT = 11;
    public static final int CLOSE_SLOT = 15;
    public static final int SETUP_SLOT = 13;
    public static final int BACK_SLOT = 45;
    public static final int PREVIOUS_PAGE_SLOT = 47;
    public static final int CANCEL_SETUP_SLOT = 49;
    public static final int NEXT_PAGE_SLOT = 51;
    public static final int CONFIRM_DONE_SLOT = 11;
    public static final int CONFIRM_BACK_SLOT = 13;
    public static final int CONFIRM_CANCEL_SLOT = 15;
    public static final int PAGE_SIZE = 45;

    private final FragStealers plugin;
    private final ShopManager manager;

    public ShopMenuService(FragStealers plugin, ShopManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void openMain(Player player, ShopData shop) {
        boolean manage = plugin.canManageShop(player, shop);
        boolean restock = plugin.canRestockShop(player, shop);
        ShopMenuHolder holder = new ShopMenuHolder(shop.signKey(), ShopMenuType.MAIN,
            plugin.isMasterOverride(player, shop.ownerUuid()));
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("FragStealers Shop"));
        holder.inventory(inventory);
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);

        if (!plugin.shopsEnabled() && !restock) {
            inventory.setItem(13, namedItem(Material.BARRIER, "Shops Temporarily Disabled", NamedTextColor.RED,
                List.of(Component.text("Purchases are disabled by the server.", NamedTextColor.GRAY))));
            player.openInventory(inventory);
            return;
        }

        if (!shop.isConfigured()) {
            if (manage) {
                inventory.setItem(SETUP_SLOT, namedItem(Material.LIME_STAINED_GLASS_PANE, "Setup Shop", NamedTextColor.GREEN,
                    List.of(Component.text("Choose the sale item and price.", NamedTextColor.GRAY))));
            } else {
                inventory.setItem(13, namedItem(Material.RED_STAINED_GLASS_PANE, "Shop Not Configured", NamedTextColor.RED,
                    List.of(Component.text("The owner has not finished setup.", NamedTextColor.GRAY))));
            }
            player.openInventory(inventory);
            return;
        }

        inventory.setItem(13, namedItem(shop.sellMaterial(), ItemCatalog.display(shop.sellMaterial()) + " x" + shop.sellAmount(),
            NamedTextColor.GREEN, List.of(Component.text("Price: " + ItemCatalog.display(shop.priceMaterial()) + " x" + shop.priceAmount(), NamedTextColor.RED))));

        if (manage) {
            inventory.setItem(OWNER_STOCK_SLOT, namedItem(Material.CHEST, "Open Stock", NamedTextColor.GREEN,
                List.of(Component.text("Stock only: " + ItemCatalog.display(shop.sellMaterial()), NamedTextColor.GRAY))));
            inventory.setItem(OWNER_COLLECT_SLOT, namedItem(Material.GOLD_INGOT, "Collect Payments", NamedTextColor.RED,
                List.of(Component.text("Stored: " + shop.earnings() + " " + ItemCatalog.display(shop.priceMaterial()), NamedTextColor.GRAY))));
        } else if (restock) {
            inventory.setItem(OWNER_STOCK_SLOT, namedItem(Material.CHEST, "Restock Shop", NamedTextColor.GREEN,
                List.of(Component.text("You may add " + ItemCatalog.display(shop.sellMaterial()) + " but cannot remove stock.", NamedTextColor.GRAY))));
            inventory.setItem(CLOSE_SLOT, namedItem(Material.RED_STAINED_GLASS_PANE, "Close", NamedTextColor.RED, List.of()));
        } else {
            inventory.setItem(BUY_SLOT, namedItem(Material.LIME_STAINED_GLASS_PANE, "Buy Item", NamedTextColor.GREEN,
                List.of(
                    Component.text("Receive: " + shop.sellAmount() + " " + ItemCatalog.display(shop.sellMaterial()), NamedTextColor.GRAY),
                    Component.text("Cost: " + shop.priceAmount() + " " + ItemCatalog.display(shop.priceMaterial()), NamedTextColor.GRAY)
                )));
            inventory.setItem(CLOSE_SLOT, namedItem(Material.RED_STAINED_GLASS_PANE, "Close", NamedTextColor.RED, List.of()));
        }
        player.openInventory(inventory);
    }

    public void openCategoryPicker(Player player, BlockKey signKey, ShopMenuType type) {
        ShopData shop = manager.bySign(signKey);
        ShopMenuHolder holder = new ShopMenuHolder(signKey, type, 0, null, null, isAdminOverride(player, shop));
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text(selectionTitle(type, "Category")));
        holder.inventory(inventory);
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);

        List<ItemCatalog.Category> categories = ItemCatalog.categories();
        for (int slot = 0; slot < categories.size() && slot < PAGE_SIZE; slot++) {
            ItemCatalog.Category category = categories.get(slot);
            inventory.setItem(slot, namedItem(category.icon(), category.displayName(), NamedTextColor.YELLOW,
                List.of(Component.text(ItemCatalog.itemCount(category) + " items", NamedTextColor.GRAY),
                    Component.text("Click to browse subcategories.", NamedTextColor.DARK_GRAY))));
        }
        if (type == ShopMenuType.SELECT_PRICE_CATEGORY) {
            inventory.setItem(BACK_SLOT, namedItem(Material.ARROW, "Back to Sell Quantity", NamedTextColor.YELLOW,
                List.of(Component.text("Return without restarting setup.", NamedTextColor.GRAY))));
        }
        inventory.setItem(CANCEL_SETUP_SLOT, namedItem(Material.RED_STAINED_GLASS_PANE, "Cancel Setup", NamedTextColor.RED, List.of()));
        player.openInventory(inventory);
    }

    public void openGroupPicker(Player player, BlockKey signKey, ShopMenuType type, ItemCatalog.Category category) {
        ShopData shop = manager.bySign(signKey);
        ShopMenuHolder holder = new ShopMenuHolder(signKey, type, 0, category, null, isAdminOverride(player, shop));
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text(category.displayName()));
        holder.inventory(inventory);
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);

        List<ItemCatalog.Group> groups = ItemCatalog.groups(category);
        for (int slot = 0; slot < groups.size() && slot < PAGE_SIZE; slot++) {
            ItemCatalog.Group group = groups.get(slot);
            inventory.setItem(slot, namedItem(group.icon(), group.displayName(), NamedTextColor.YELLOW,
                List.of(Component.text(ItemCatalog.items(group).size() + " items", NamedTextColor.GRAY),
                    Component.text("Click to view items.", NamedTextColor.DARK_GRAY))));
        }
        inventory.setItem(BACK_SLOT, namedItem(Material.ARROW, "Back to Categories", NamedTextColor.YELLOW, List.of()));
        inventory.setItem(CANCEL_SETUP_SLOT, namedItem(Material.RED_STAINED_GLASS_PANE, "Cancel Setup", NamedTextColor.RED, List.of()));
        player.openInventory(inventory);
    }

    public void openItemPicker(Player player, BlockKey signKey, ShopMenuType type, ItemCatalog.Group group, int page) {
        List<Material> items = ItemCatalog.items(group);
        int maxPage = Math.max(0, (items.size() - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, maxPage));
        ShopData shop = manager.bySign(signKey);
        ShopMenuHolder holder = new ShopMenuHolder(signKey, type, safePage, group.category(), group, isAdminOverride(player, shop));
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text(group.displayName()));
        holder.inventory(inventory);
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);

        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, items.size());
        for (int i = start; i < end; i++) {
            Material material = items.get(i);
            inventory.setItem(i - start, namedItem(material, ItemCatalog.display(material), NamedTextColor.YELLOW,
                List.of(Component.text("Click to select.", NamedTextColor.GRAY))));
        }
        inventory.setItem(BACK_SLOT, namedItem(Material.ARROW, "Back to " + group.category().displayName(), NamedTextColor.YELLOW, List.of()));
        if (safePage > 0) {
            inventory.setItem(PREVIOUS_PAGE_SLOT, namedItem(Material.ARROW, "Previous Page", NamedTextColor.YELLOW,
                List.of(Component.text("Page " + (safePage + 1) + " of " + (maxPage + 1), NamedTextColor.GRAY))));
        }
        inventory.setItem(CANCEL_SETUP_SLOT, namedItem(Material.RED_STAINED_GLASS_PANE, "Cancel Setup", NamedTextColor.RED, List.of()));
        if (safePage < maxPage) {
            inventory.setItem(NEXT_PAGE_SLOT, namedItem(Material.ARROW, "Next Page", NamedTextColor.YELLOW,
                List.of(Component.text("Page " + (safePage + 1) + " of " + (maxPage + 1), NamedTextColor.GRAY))));
        }
        player.openInventory(inventory);
    }

    public void openAmountPicker(Player player, ShopData shop, ShopMenuType type, Material material, int page) {
        int[] quantities = ItemCatalog.quantities(material);
        int maxPage = Math.max(0, (quantities.length - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, maxPage));
        String title = type == ShopMenuType.SELECT_SELL_AMOUNT ? "Choose Sell Quantity" : "Choose Price Quantity";
        ShopMenuHolder holder = new ShopMenuHolder(shop.signKey(), type, safePage, null, null,
            plugin.isMasterOverride(player, shop.ownerUuid()));
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text(title));
        holder.inventory(inventory);
        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, quantities.length);
        for (int i = start; i < end; i++) {
            int amount = quantities[i];
            ItemStack button = namedItem(Material.LIME_STAINED_GLASS_PANE, "x" + amount, NamedTextColor.GREEN,
                List.of(Component.text(ItemCatalog.display(material), NamedTextColor.GRAY)));
            button.setAmount(amount);
            inventory.setItem(i - start, button);
        }
        inventory.setItem(BACK_SLOT, namedItem(Material.ARROW, "Back to Item Selection", NamedTextColor.YELLOW,
            List.of(Component.text("Change the selected material without restarting setup.", NamedTextColor.GRAY))));
        if (safePage > 0) inventory.setItem(PREVIOUS_PAGE_SLOT, namedItem(Material.ARROW, "Previous Page", NamedTextColor.YELLOW, List.of()));
        inventory.setItem(CANCEL_SETUP_SLOT, namedItem(Material.RED_STAINED_GLASS_PANE, "Cancel Setup", NamedTextColor.RED, List.of()));
        if (safePage < maxPage) inventory.setItem(NEXT_PAGE_SLOT, namedItem(Material.ARROW, "Next Page", NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    public void openConfirm(Player player, ShopData shop, Material sell, int sellAmount, Material price, int priceAmount) {
        ShopMenuHolder holder = new ShopMenuHolder(shop.signKey(), ShopMenuType.CONFIRM_SETUP,
            plugin.isMasterOverride(player, shop.ownerUuid()));
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Confirm Shop Setup"));
        holder.inventory(inventory);
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);
        inventory.setItem(4, namedItem(sell, "Sell: " + ItemCatalog.display(sell) + " x" + sellAmount, NamedTextColor.GREEN, List.of()));
        inventory.setItem(22, namedItem(price, "Price: " + ItemCatalog.display(price) + " x" + priceAmount, NamedTextColor.RED, List.of()));
        inventory.setItem(CONFIRM_DONE_SLOT, namedItem(Material.LIME_STAINED_GLASS_PANE, "Done", NamedTextColor.GREEN, List.of()));
        inventory.setItem(CONFIRM_BACK_SLOT, namedItem(Material.ARROW, "Back to Price Quantity", NamedTextColor.YELLOW,
            List.of(Component.text("Review or change earlier selections without cancelling.", NamedTextColor.GRAY))));
        inventory.setItem(CONFIRM_CANCEL_SLOT, namedItem(Material.RED_STAINED_GLASS_PANE, "Cancel", NamedTextColor.RED, List.of()));
        player.openInventory(inventory);
    }

    public void openStock(Player player, ShopData shop) {
        Inventory inventory = manager.inventory(shop);
        if (inventory == null) {
            player.closeInventory();
            player.sendMessage(plugin.error("This shop container could not be found."));
            return;
        }
        player.openInventory(inventory);
    }

    public void buy(Player player, ShopData shop) {
        if (!plugin.shopsEnabled()) {
            player.sendMessage(plugin.error("FragStealers shops are currently disabled."));
            return;
        }
        Inventory stock = manager.inventory(shop);
        if (stock == null || !shop.isConfigured()) {
            player.closeInventory();
            player.sendMessage(plugin.error("This shop is unavailable."));
            return;
        }
        java.util.function.Predicate<ItemStack> usable = item -> !plugin.masterKeys().isMasterKey(item);
        if (ItemUtil.count(stock, shop.sellMaterial(), usable) < shop.sellAmount()) {
            player.sendMessage(plugin.error("This shop does not have enough stock."));
            return;
        }
        if (ItemUtil.count(player.getInventory(), shop.priceMaterial(), usable) < shop.priceAmount()) {
            player.sendMessage(plugin.error("You do not have enough " + ItemCatalog.display(shop.priceMaterial()) + "."));
            return;
        }
        List<ItemStack> incoming = ItemUtil.peek(stock, shop.sellMaterial(), shop.sellAmount(), usable);
        if (incoming.isEmpty() || !ItemUtil.canFitAfterPayment(player.getInventory(), shop.priceMaterial(), shop.priceAmount(), incoming, usable)) {
            player.sendMessage(plugin.error("You do not have enough inventory room for this purchase."));
            return;
        }
        List<ItemStack> sold = ItemUtil.take(stock, shop.sellMaterial(), shop.sellAmount(), usable);
        if (sold.isEmpty()) {
            player.sendMessage(plugin.error("This shop does not have enough stock."));
            return;
        }
        ItemUtil.remove(player.getInventory(), shop.priceMaterial(), shop.priceAmount(), usable);
        for (ItemStack item : sold) player.getInventory().addItem(item);
        shop.addEarnings(shop.priceAmount());
        manager.save();
        player.sendMessage(plugin.success("Purchase complete."));
        openMain(player, shop);
    }

    public void collectPayments(Player player, ShopData shop) {
        if (!shop.isConfigured() || shop.earnings() <= 0) {
            player.sendMessage(plugin.error("This shop has no payments to collect."));
            openMain(player, shop);
            return;
        }
        int fit = ItemUtil.fitAmount(player.getInventory(), shop.priceMaterial(), shop.earnings());
        if (fit <= 0) {
            player.sendMessage(plugin.error("You do not have enough inventory room."));
            return;
        }
        long amount = Math.min(shop.earnings(), fit);
        ItemUtil.giveOrDrop(player, shop.priceMaterial(), amount);
        shop.removeEarnings(amount);
        manager.save();
        if (!shop.isOwner(player.getUniqueId())) {
            plugin.audit().log(player, "COLLECTED_SHOP_PAYMENTS", ProtectionType.SHOP, shop.ownerUuid(), shop.ownerName(), shop.signKey(),
                "Collected " + amount + " " + shop.priceMaterial().name());
        }
        player.sendMessage(plugin.success("Collected " + amount + " " + ItemCatalog.display(shop.priceMaterial()) + "."));
        openMain(player, shop);
    }

    public void closeViewers(ShopData shop) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Inventory top = viewer.getOpenInventory().getTopInventory();
            boolean shopMenu = top.getHolder() instanceof ShopMenuHolder holder && holder.signKey().equals(shop.signKey());
            if (shopMenu || manager.inventoryBelongs(top, shop)) viewer.closeInventory();
        }
    }

    public void updateSign(ShopData shop) {
        Block block = shop.signKey().block();
        if (block == null || !(block.getState() instanceof Sign sign)) return;
        SignSide side = sign.getSide(Side.FRONT);
        side.line(0, Component.text("[shop]", NamedTextColor.GOLD));
        side.line(1, Component.text(shop.ownerName(), NamedTextColor.WHITE));
        if (shop.isConfigured()) {
            side.line(2, Component.text(ItemCatalog.display(shop.sellMaterial()) + " x" + shop.sellAmount(), NamedTextColor.GREEN));
            side.line(3, Component.text(ItemCatalog.display(shop.priceMaterial()) + " x" + shop.priceAmount(), NamedTextColor.RED));
        } else {
            side.line(2, Component.text("Not configured", NamedTextColor.YELLOW));
            side.line(3, Component.text("Right-click setup", NamedTextColor.GRAY));
        }
        side.setGlowingText(false);
        sign.setWaxed(true);
        sign.update(true, false);
    }

    private boolean isAdminOverride(Player player, ShopData shop) {
        return shop != null && plugin.isMasterOverride(player, shop.ownerUuid());
    }

    private String selectionTitle(ShopMenuType type, String suffix) {
        boolean sell = type == ShopMenuType.SELECT_SELL_CATEGORY
            || type == ShopMenuType.SELECT_SELL_GROUP
            || type == ShopMenuType.SELECT_SELL_ITEM;
        return (sell ? "Sell Item " : "Price Item ") + suffix;
    }

    private void fill(Inventory inventory, Material material) {
        ItemStack filler = namedItem(material, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
    }

    public ItemStack namedItem(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        List<Component> fixedLore = new ArrayList<>();
        for (Component line : lore) fixedLore.add(line.decoration(TextDecoration.ITALIC, false));
        meta.lore(fixedLore);
        item.setItemMeta(meta);
        return item;
    }
}
