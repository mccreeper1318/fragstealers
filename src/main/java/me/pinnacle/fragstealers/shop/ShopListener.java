package me.pinnacle.fragstealers.shop;

import me.pinnacle.fragstealers.BlockKey;
import me.pinnacle.fragstealers.FragStealers;
import me.pinnacle.fragstealers.ProtectionType;
import me.pinnacle.fragstealers.data.ShopData;
import me.pinnacle.fragstealers.data.ShopManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ShopListener implements Listener {
    private final FragStealers plugin;
    private final ShopManager manager;
    private final ShopMenuService menus;
    private final Map<UUID, SetupSession> setupSessions = new HashMap<>();
    private final Map<UUID, StockSession> stockSessions = new HashMap<>();

    public ShopListener(FragStealers plugin, ShopManager manager, ShopMenuService menus) {
        this.plugin = plugin;
        this.manager = manager;
        this.menus = menus;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        ShopData shop = manager.bySign(event.getClickedBlock());
        if (shop == null) {
            return;
        }
        event.setCancelled(true);
        menus.openMain(event.getPlayer(), shop);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ShopSearchHolder holder) {
            event.setCancelled(true);
            if (holder.adminOverride() && !plugin.masterKeys().canUse(player)) {
                later(player::closeInventory);
                player.sendMessage(plugin.error("Keep the Master Key in your main hand while managing this shop."));
                return;
            }
            if (event.getRawSlot() == 2) {
                String query = event.getView() instanceof AnvilView anvilView ? anvilView.getRenameText() : "";
                plugin.getServer().getScheduler().runTask(plugin,
                    () -> menus.openItemPicker(player, holder.signKey(), holder.returnType(), 0, query));
            }
            return;
        }
        if (top.getHolder() instanceof ShopMenuHolder holder) {
            event.setCancelled(true);
            if (holder.adminOverride() && !plugin.masterKeys().canUse(player)) {
                later(player::closeInventory);
                player.sendMessage(plugin.error("Keep the Master Key in your main hand while managing this shop."));
                return;
            }
            handleMenuClick(event, player, holder, top);
            return;
        }
        enforceStockClick(event, player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ShopMenuHolder || top.getHolder() instanceof ShopSearchHolder) {
            event.setCancelled(true);
            return;
        }
        StockSession session = stockSessions.get(player.getUniqueId());
        if (session == null) return;
        ShopData shop = manager.bySign(session.signKey());
        if (shop == null || !manager.inventoryBelongs(top, shop)) return;
        if (!plugin.canManage(player, shop.ownerUuid())) {
            event.setCancelled(true);
            stockSessions.remove(player.getUniqueId());
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            player.sendMessage(plugin.error("Keep the Master Key in your main hand while managing this shop."));
            return;
        }
        boolean intoTop = event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize());
        if (intoTop && plugin.masterKeys().isMasterKey(event.getOldCursor())) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("Master Keys cannot be placed in shops."));
        } else if (intoTop && !ItemUtil.isEmpty(event.getOldCursor()) && event.getOldCursor().getType() != shop.sellMaterial()) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("This shop can only contain " + ItemCatalog.display(shop.sellMaterial()) + "."));
        }
        postCheckMasterKey(player, session);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopSearchHolder)) {
            return;
        }
        AnvilView anvilView = event.getView();
        String query = anvilView.getRenameText();
        event.setResult(menus.searchResult(query));
        anvilView.setRepairCost(0);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof ShopSearchHolder) {
            event.getInventory().clear();
            return;
        }
        StockSession session = stockSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        ShopData shop = manager.bySign(session.signKey());
        if (shop == null || manager.inventoryBelongs(event.getInventory(), shop)) {
            stockSessions.remove(player.getUniqueId());
            if (shop != null && session.adminOverride()) {
                Inventory inventory = manager.inventory(shop);
                if (inventory != null) {
                    String details = ItemUtil.summarizeDifference(session.before(), inventory.getContents());
                    if (!details.equals("No items removed")) {
                        plugin.audit().log(player, "WITHDREW_SHOP_STOCK", ProtectionType.SHOP,
                            shop.ownerUuid(), shop.ownerName(), shop.signKey(), details);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        setupSessions.remove(event.getPlayer().getUniqueId());
        stockSessions.remove(event.getPlayer().getUniqueId());
    }

    private void handleMenuClick(InventoryClickEvent event, Player player, ShopMenuHolder holder, Inventory top) {
        ShopData shop = manager.bySign(holder.signKey());
        if (shop == null) {
            later(player::closeInventory);
            return;
        }
        if (event.getClickedInventory() != top) {
            return;
        }
        switch (holder.type()) {
            case MAIN -> handleMain(event, player, shop);
            case SELECT_SELL_ITEM, SELECT_PRICE_ITEM -> handleItemPicker(event, player, shop, holder);
            case SELECT_SELL_AMOUNT, SELECT_PRICE_AMOUNT -> handleAmountPicker(event, player, shop, holder);
            case CONFIRM_SETUP -> handleConfirm(event, player, shop);
        }
    }

    private void handleMain(InventoryClickEvent event, Player player, ShopData shop) {
        boolean manage = plugin.canManage(player, shop.ownerUuid());
        int slot = event.getSlot();
        if (!shop.isConfigured()) {
            if (manage && slot == ShopMenuService.SETUP_SLOT) {
                setupSessions.put(player.getUniqueId(), new SetupSession(shop.signKey()));
                plugin.getServer().getScheduler().runTask(plugin,
                    () -> menus.openItemPicker(player, shop.signKey(), ShopMenuType.SELECT_SELL_ITEM, 0, ""));
            }
            return;
        }
        if (manage) {
            if (slot == ShopMenuService.OWNER_STOCK_SLOT) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Inventory inventory = manager.inventory(shop);
                    if (inventory == null) {
                        player.sendMessage(plugin.error("The shop container could not be found."));
                        return;
                    }
                    stockSessions.put(player.getUniqueId(), new StockSession(shop.signKey(),
                        !shop.isOwner(player.getUniqueId()), cloneContents(inventory.getContents())));
                    menus.openStock(player, shop);
                });
            } else if (slot == ShopMenuService.OWNER_COLLECT_SLOT) {
                plugin.getServer().getScheduler().runTask(plugin, () -> menus.collectPayments(player, shop));
            }
        } else if (slot == ShopMenuService.BUY_SLOT) {
            plugin.getServer().getScheduler().runTask(plugin, () -> menus.buy(player, shop));
        } else if (slot == ShopMenuService.CLOSE_SLOT) {
            later(player::closeInventory);
        }
    }

    private void handleItemPicker(InventoryClickEvent event, Player player, ShopData shop, ShopMenuHolder holder) {
        if (!ensureCanSetup(player, shop)) {
            return;
        }
        int slot = event.getSlot();
        if (slot == ShopMenuService.PREVIOUS_PAGE_SLOT) {
            later(() -> menus.openItemPicker(player, shop.signKey(), holder.type(), holder.page() - 1, holder.query()));
            return;
        }
        if (slot == ShopMenuService.NEXT_PAGE_SLOT) {
            later(() -> menus.openItemPicker(player, shop.signKey(), holder.type(), holder.page() + 1, holder.query()));
            return;
        }
        if (slot == ShopMenuService.SEARCH_SLOT) {
            later(() -> menus.openSearch(player, shop.signKey(), holder.type()));
            return;
        }
        if (slot == ShopMenuService.CANCEL_SETUP_SLOT) {
            setupSessions.remove(player.getUniqueId());
            later(() -> menus.openMain(player, shop));
            return;
        }
        if (slot < 0 || slot >= ShopMenuService.PAGE_SIZE) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (ItemUtil.isEmpty(clicked) || !ItemCatalog.isAllowed(clicked.getType())) {
            return;
        }
        SetupSession session = session(player, shop.signKey());
        if (holder.type() == ShopMenuType.SELECT_SELL_ITEM) {
            session.sellMaterial = clicked.getType();
            later(() -> menus.openAmountPicker(player, shop, ShopMenuType.SELECT_SELL_AMOUNT, session.sellMaterial, 0));
        } else {
            session.priceMaterial = clicked.getType();
            later(() -> menus.openAmountPicker(player, shop, ShopMenuType.SELECT_PRICE_AMOUNT, session.priceMaterial, 0));
        }
    }

    private void handleAmountPicker(InventoryClickEvent event, Player player, ShopData shop, ShopMenuHolder holder) {
        if (!ensureCanSetup(player, shop)) {
            return;
        }
        SetupSession session = session(player, shop.signKey());
        Material material = holder.type() == ShopMenuType.SELECT_SELL_AMOUNT ? session.sellMaterial : session.priceMaterial;
        int slot = event.getSlot();
        if (slot == ShopMenuService.PREVIOUS_PAGE_SLOT) {
            if (material != null) later(() -> menus.openAmountPicker(player, shop, holder.type(), material, holder.page() - 1));
            return;
        }
        if (slot == ShopMenuService.NEXT_PAGE_SLOT) {
            if (material != null) later(() -> menus.openAmountPicker(player, shop, holder.type(), material, holder.page() + 1));
            return;
        }
        if (slot == ShopMenuService.CANCEL_SETUP_SLOT) {
            setupSessions.remove(player.getUniqueId());
            later(() -> menus.openMain(player, shop));
            return;
        }
        if (slot < 0 || slot >= ShopMenuService.PAGE_SIZE) return;
        ItemStack clicked = event.getCurrentItem();
        if (ItemUtil.isEmpty(clicked) || clicked.getType() != Material.LIME_STAINED_GLASS_PANE) return;
        int amount = clicked.getAmount();
        if (holder.type() == ShopMenuType.SELECT_SELL_AMOUNT) {
            session.sellAmount = amount;
            later(() -> menus.openItemPicker(player, shop.signKey(), ShopMenuType.SELECT_PRICE_ITEM, 0, ""));
        } else {
            session.priceAmount = amount;
            if (session.complete()) later(() -> menus.openConfirm(player, shop, session.sellMaterial, session.sellAmount, session.priceMaterial, session.priceAmount));
        }
    }

    private void handleConfirm(InventoryClickEvent event, Player player, ShopData shop) {
        if (!ensureCanSetup(player, shop)) return;
        if (event.getSlot() == ShopMenuService.CONFIRM_CANCEL_SLOT) {
            setupSessions.remove(player.getUniqueId());
            later(() -> menus.openMain(player, shop));
            return;
        }
        if (event.getSlot() != ShopMenuService.CONFIRM_DONE_SLOT) return;
        SetupSession session = setupSessions.get(player.getUniqueId());
        if (session == null || !session.complete()) {
            player.sendMessage(plugin.error("Shop setup was incomplete."));
            return;
        }
        Inventory stock = manager.inventory(shop);
        if (stock == null || !ItemUtil.containsOnlyEmptyOr(stock, session.sellMaterial)) {
            player.sendMessage(plugin.error("The shop container must be empty or contain only the sale item."));
            return;
        }
        if (ItemUtil.contains(stock, plugin.masterKeys()::isMasterKey)) {
            player.sendMessage(plugin.error("Master Keys cannot be used as shop stock."));
            return;
        }
        shop.configure(session.sellMaterial, session.sellAmount, session.priceMaterial, session.priceAmount);
        setupSessions.remove(player.getUniqueId());
        manager.save();
        menus.updateSign(shop);
        player.sendMessage(plugin.success("Shop setup complete."));
        later(() -> menus.openMain(player, shop));
    }

    private void enforceStockClick(InventoryClickEvent event, Player player) {
        StockSession session = stockSessions.get(player.getUniqueId());
        if (session == null) return;
        ShopData shop = manager.bySign(session.signKey());
        if (shop == null || !manager.inventoryBelongs(event.getView().getTopInventory(), shop)) {
            stockSessions.remove(player.getUniqueId());
            return;
        }
        if (!plugin.canManage(player, shop.ownerUuid())) {
            event.setCancelled(true);
            stockSessions.remove(player.getUniqueId());
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            player.sendMessage(plugin.error("Keep the Master Key in your main hand while managing this shop."));
            return;
        }
        Inventory top = event.getView().getTopInventory();
        boolean clickedTop = event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize();
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        if (clickedTop) {
            if (isHotbarAction(event.getAction())) {
                ItemStack moving = null;
                if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) moving = player.getInventory().getItem(event.getHotbarButton());
                else if (event.getClick() == ClickType.SWAP_OFFHAND) moving = player.getInventory().getItemInOffHand();
                if (plugin.masterKeys().isMasterKey(moving)) {
                    cancelMasterKeyStock(event, player);
                } else if (!ItemUtil.isEmpty(moving) && moving.getType() != shop.sellMaterial()) {
                    cancelStock(event, player, shop);
                }
                postCheckMasterKey(player, session);
                return;
            }
            if ((event.getAction() == InventoryAction.PLACE_ALL || event.getAction() == InventoryAction.PLACE_ONE
                || event.getAction() == InventoryAction.PLACE_SOME || event.getAction() == InventoryAction.SWAP_WITH_CURSOR)
                && plugin.masterKeys().isMasterKey(cursor)) {
                cancelMasterKeyStock(event, player);
            } else if ((event.getAction() == InventoryAction.PLACE_ALL || event.getAction() == InventoryAction.PLACE_ONE
                || event.getAction() == InventoryAction.PLACE_SOME || event.getAction() == InventoryAction.SWAP_WITH_CURSOR)
                && !ItemUtil.isEmpty(cursor) && cursor.getType() != shop.sellMaterial()) {
                cancelStock(event, player, shop);
            }
        } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
            && plugin.masterKeys().isMasterKey(current)) {
            cancelMasterKeyStock(event, player);
        } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
            && !ItemUtil.isEmpty(current) && current.getType() != shop.sellMaterial()) {
            cancelStock(event, player, shop);
        }
        postCheckMasterKey(player, session);
    }

    private void cancelMasterKeyStock(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        player.sendMessage(plugin.error("Master Keys cannot be placed in shops."));
    }

    private void postCheckMasterKey(Player player, StockSession session) {
        if (!session.adminOverride()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!plugin.masterKeys().canUse(player)) player.closeInventory();
        });
    }

    private void cancelStock(InventoryClickEvent event, Player player, ShopData shop) {
        event.setCancelled(true);
        player.sendMessage(plugin.error("This shop can only contain " + ItemCatalog.display(shop.sellMaterial()) + "."));
    }

    private boolean ensureCanSetup(Player player, ShopData shop) {
        if (!plugin.canManage(player, shop.ownerUuid())) {
            later(player::closeInventory);
            return false;
        }
        if (shop.isConfigured()) {
            later(player::closeInventory);
            setupSessions.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private SetupSession session(Player player, BlockKey signKey) {
        SetupSession existing = setupSessions.get(player.getUniqueId());
        if (existing != null && existing.signKey.equals(signKey)) return existing;
        SetupSession created = new SetupSession(signKey);
        setupSessions.put(player.getUniqueId(), created);
        return created;
    }

    private boolean isHotbarAction(InventoryAction action) {
        return action == InventoryAction.HOTBAR_SWAP;
    }

    private void later(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) clone[i] = ItemUtil.isEmpty(contents[i]) ? null : contents[i].clone();
        return clone;
    }

    private static final class SetupSession {
        private final BlockKey signKey;
        private Material sellMaterial;
        private int sellAmount;
        private Material priceMaterial;
        private int priceAmount;
        private SetupSession(BlockKey signKey) { this.signKey = signKey; }
        private boolean complete() { return sellMaterial != null && priceMaterial != null && sellAmount > 0 && priceAmount > 0; }
    }

    private record StockSession(BlockKey signKey, boolean adminOverride, ItemStack[] before) {
    }
}
