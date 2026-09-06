package me.pinnacle.fragstealers.shop;

import me.pinnacle.fragstealers.BlockKey;
import me.pinnacle.fragstealers.FragStealers;
import me.pinnacle.fragstealers.MasterKeyManager;
import me.pinnacle.fragstealers.data.ShopData;
import me.pinnacle.fragstealers.data.ShopManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopListenerBehaviorTest {
    private static final BlockKey SIGN = new BlockKey("world", 20, 64, 20);
    private static final BlockKey CONTAINER = new BlockKey("world", 20, 64, 21);
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TRUSTED = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private FragStealers plugin;
    private ShopManager manager;
    private ShopMenuService menus;
    private MasterKeyManager masterKeys;
    private BukkitScheduler scheduler;
    private Player player;
    private Inventory stock;
    private ShopData shop;
    private ShopListener listener;

    @BeforeEach
    void setUp() {
        plugin = mock(FragStealers.class);
        manager = mock(ShopManager.class);
        menus = mock(ShopMenuService.class);
        masterKeys = mock(MasterKeyManager.class);
        scheduler = mock(BukkitScheduler.class);
        Server server = mock(Server.class);
        player = mock(Player.class);
        stock = mock(Inventory.class);
        shop = new ShopData(SIGN, Set.of(CONTAINER), OWNER, "Owner", Material.DIAMOND, 1, Material.EMERALD, 1, 0L);

        when(plugin.masterKeys()).thenReturn(masterKeys);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.error(anyString())).thenReturn(Component.text("error"));
        when(player.getUniqueId()).thenReturn(TRUSTED);
        when(manager.bySign(SIGN)).thenReturn(shop);
        when(manager.byInventory(stock)).thenReturn(shop);
        when(manager.inventory(shop)).thenReturn(stock);
        when(manager.inventoryBelongs(stock, shop)).thenReturn(true);
        when(stock.getContents()).thenReturn(new ItemStack[27]);
        when(stock.getSize()).thenReturn(27);
        when(plugin.isMasterOverride(player, OWNER)).thenReturn(false);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return mock(BukkitTask.class);
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class));

        listener = new ShopListener(plugin, manager, menus);
    }

    @Test
    void accessLevelStockSessionAllowsValidRestockButBlocksRemovingStock() {
        when(plugin.canManageShop(player, shop)).thenReturn(false);
        when(plugin.canRestockShop(player, shop)).thenReturn(true);
        openStockSession();

        InventoryClickEvent remove = stockClick(0, InventoryAction.PICKUP_ALL, item(Material.DIAMOND));
        listener.onInventoryClick(remove);
        verify(remove).setCancelled(true);

        InventoryClickEvent add = stockClick(30, InventoryAction.MOVE_TO_OTHER_INVENTORY, item(Material.DIAMOND));
        listener.onInventoryClick(add);
        verify(add, never()).setCancelled(true);
    }

    @Test
    void stockSessionRejectsInvalidMaterialsAndMasterKeys() {
        when(plugin.canManageShop(player, shop)).thenReturn(false);
        when(plugin.canRestockShop(player, shop)).thenReturn(true);
        openStockSession();

        InventoryClickEvent invalid = stockClick(30, InventoryAction.MOVE_TO_OTHER_INVENTORY, item(Material.DIRT));
        listener.onInventoryClick(invalid);
        verify(invalid).setCancelled(true);

        ItemStack masterKey = item(Material.WOODEN_AXE);
        when(masterKeys.isMasterKey(masterKey)).thenReturn(true);
        InventoryClickEvent master = stockClick(30, InventoryAction.MOVE_TO_OTHER_INVENTORY, masterKey);
        listener.onInventoryClick(master);
        verify(master).setCancelled(true);
    }

    @Test
    void manageLevelStockSessionCanRemoveStock() {
        when(plugin.canManageShop(player, shop)).thenReturn(true);
        when(plugin.canRestockShop(player, shop)).thenReturn(true);
        openStockSession();

        InventoryClickEvent remove = stockClick(0, InventoryAction.PICKUP_ALL, item(Material.DIAMOND));
        listener.onInventoryClick(remove);

        verify(remove, never()).setCancelled(true);
    }

    @Test
    void trustDowngradeDuringActiveFullStockSessionCancelsTheNextAction() {
        when(plugin.canManageShop(player, shop)).thenReturn(true, false);
        when(plugin.canRestockShop(player, shop)).thenReturn(true);
        openStockSession();

        InventoryClickEvent remove = stockClick(0, InventoryAction.PICKUP_ALL, item(Material.DIAMOND));
        listener.onInventoryClick(remove);

        verify(remove).setCancelled(true);
        verify(player).closeInventory();
    }

    private void openStockSession() {
        Inventory menu = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        ShopMenuHolder holder = new ShopMenuHolder(SIGN, ShopMenuType.MAIN);

        when(click.getWhoClicked()).thenReturn(player);
        when(click.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(menu);
        when(menu.getHolder()).thenReturn(holder);
        when(click.getClickedInventory()).thenReturn(menu);
        when(click.getSlot()).thenReturn(ShopMenuService.OWNER_STOCK_SLOT);

        listener.onInventoryClick(click);
        verify(menus).openStock(player, shop);
    }

    private InventoryClickEvent stockClick(int rawSlot, InventoryAction action, ItemStack current) {
        InventoryView view = mock(InventoryView.class);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(stock);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getAction()).thenReturn(action);
        when(event.getCurrentItem()).thenReturn(current);
        return event;
    }

    private ItemStack item(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(1);
        return item;
    }
}
