package me.pinnacle.fragstealers.shop;

import me.pinnacle.fragstealers.BlockKey;
import me.pinnacle.fragstealers.FragStealers;
import me.pinnacle.fragstealers.MasterKeyManager;
import me.pinnacle.fragstealers.data.ShopData;
import me.pinnacle.fragstealers.data.ShopManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopPersistenceTransactionBehaviorTest {
    private static final BlockKey SIGN = new BlockKey("world", 50, 64, 50);
    private static final BlockKey CONTAINER = new BlockKey("world", 50, 64, 51);
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private FragStealers plugin;
    private ShopManager manager;
    private MasterKeyManager masterKeys;
    private Player player;
    private PlayerInventory playerInventory;
    private Inventory stock;
    private ShopMenuService menus;

    @BeforeEach
    void setUp() {
        plugin = mock(FragStealers.class);
        manager = mock(ShopManager.class);
        masterKeys = mock(MasterKeyManager.class);
        player = mock(Player.class);
        playerInventory = mock(PlayerInventory.class);
        stock = mock(Inventory.class);

        when(plugin.masterKeys()).thenReturn(masterKeys);
        when(plugin.shopsEnabled()).thenReturn(true);
        when(plugin.error(anyString())).thenReturn(Component.text("error"));
        when(plugin.success(anyString())).thenReturn(Component.text("success"));
        when(player.getInventory()).thenReturn(playerInventory);

        menus = new ShopMenuService(plugin, manager);
    }

    @Test
    void failedPurchasePersistenceRestoresStockPaymentAndEarnings() {
        ShopData shop = shop(7L);
        ItemStack sold = item(Material.DIAMOND);
        ItemStack payment = item(Material.EMERALD);
        ItemStack[] stockContents = new ItemStack[] { sold };
        ItemStack[] playerContents = new ItemStack[] { payment, null };

        when(payment.isSimilar(any(ItemStack.class))).thenReturn(true);
        when(manager.inventory(shop)).thenReturn(stock);
        when(manager.save()).thenReturn(false);
        when(stock.getContents()).thenReturn(stockContents);
        when(stock.getSize()).thenReturn(1);
        when(stock.getItem(0)).thenReturn(sold);
        when(playerInventory.getStorageContents()).thenReturn(playerContents);

        try (MockedStatic<ItemCatalog> catalog = mockStatic(ItemCatalog.class);
             MockedConstruction<ItemStack> construction = mockConstruction(ItemStack.class, (constructed, context) -> {
                 when(constructed.getType()).thenReturn(Material.EMERALD);
                 when(constructed.getAmount()).thenReturn(1);
                 when(constructed.clone()).thenReturn(constructed);
             })) {
            catalog.when(() -> ItemCatalog.isSafePaymentMaterial(Material.EMERALD)).thenReturn(true);
            menus.buy(player, shop);
        }

        ArgumentCaptor<ItemStack[]> stockRestore = ArgumentCaptor.forClass(ItemStack[].class);
        verify(stock).setContents(stockRestore.capture());
        assertSame(sold, stockRestore.getValue()[0]);

        ArgumentCaptor<ItemStack[]> playerWrites = ArgumentCaptor.forClass(ItemStack[].class);
        verify(playerInventory, atLeast(2)).setStorageContents(playerWrites.capture());
        ItemStack[] finalPlayerContents = playerWrites.getAllValues().get(playerWrites.getAllValues().size() - 1);
        assertSame(payment, finalPlayerContents[0]);
        assertEquals(7L, shop.earnings());
    }

    @Test
    void failedPaymentCollectionPersistenceRestoresInventoryAndEarnings() {
        ShopData shop = shop(7L);
        ItemStack[] playerContents = new ItemStack[] { null, null };
        when(playerInventory.getStorageContents()).thenReturn(playerContents);
        when(manager.save()).thenReturn(false);

        try (MockedStatic<ItemCatalog> catalog = mockStatic(ItemCatalog.class);
             MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class, CALLS_REAL_METHODS)) {
            catalog.when(() -> ItemCatalog.isSafePaymentMaterial(Material.EMERALD)).thenReturn(true);
            itemUtil.when(() -> ItemUtil.fitAmount(playerInventory, Material.EMERALD, 7L)).thenReturn(7);
            itemUtil.when(() -> ItemUtil.giveOrDrop(player, Material.EMERALD, 7L)).thenAnswer(invocation -> null);

            menus.collectPayments(player, shop);
        }

        ArgumentCaptor<ItemStack[]> playerRestore = ArgumentCaptor.forClass(ItemStack[].class);
        verify(playerInventory).setStorageContents(playerRestore.capture());
        assertEquals(2, playerRestore.getValue().length);
        assertEquals(7L, shop.earnings());
    }

    private ShopData shop(long earnings) {
        return new ShopData(SIGN, Set.of(CONTAINER), OWNER, "Owner",
            Material.DIAMOND, 1, Material.EMERALD, 1, earnings);
    }

    private ItemStack item(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(1);
        when(item.getMaxStackSize()).thenReturn(64);
        when(item.clone()).thenReturn(item);
        return item;
    }
}
