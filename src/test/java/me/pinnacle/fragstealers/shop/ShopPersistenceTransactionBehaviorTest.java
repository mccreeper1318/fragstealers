package me.pinnacle.fragstealers.shop;

import me.pinnacle.fragstealers.BlockKey;
import me.pinnacle.fragstealers.ContainerResolver;
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
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
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

    @TempDir
    Path tempDir;

    private FragStealers plugin;
    private ShopManager manager;
    private MasterKeyManager masterKeys;
    private Player player;
    private PlayerInventory playerInventory;
    private Inventory stock;
    private ShopMenuService menus;
    private Path dataFolder;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(FragStealers.class);
        ContainerResolver resolver = mock(ContainerResolver.class);
        masterKeys = mock(MasterKeyManager.class);
        player = mock(Player.class);
        playerInventory = mock(PlayerInventory.class);
        stock = mock(Inventory.class);
        dataFolder = tempDir.resolve("shop-data");
        Files.createDirectory(dataFolder);

        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ShopPersistenceTransactionBehaviorTest"));
        when(plugin.masterKeys()).thenReturn(masterKeys);
        when(plugin.shopsEnabled()).thenReturn(true);
        when(plugin.error(anyString())).thenReturn(Component.text("error"));
        when(plugin.success(anyString())).thenReturn(Component.text("success"));
        when(player.getInventory()).thenReturn(playerInventory);
        when(resolver.inventory(anySet())).thenReturn(stock);

        manager = new ShopManager(plugin, resolver);
        menus = new ShopMenuService(plugin, manager);
    }

    @Test
    void failedPurchaseYamlWriteRestoresStockPaymentAndEarnings() throws Exception {
        ShopData shop = shop(7L);
        ItemStack sold = item(Material.DIAMOND);
        ItemStack payment = item(Material.EMERALD);
        ItemStack[] stockContents = new ItemStack[] { sold };
        ItemStack[] playerContents = new ItemStack[] { payment, null };

        when(payment.isSimilar(any(ItemStack.class))).thenReturn(true);
        when(stock.getContents()).thenReturn(stockContents);
        when(stock.getSize()).thenReturn(1);
        when(stock.getItem(0)).thenReturn(sold);
        when(playerInventory.getStorageContents()).thenReturn(playerContents);

        assertTrue(manager.add(shop));
        forceYamlWritesToFail("shops.yml");

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
    void failedPaymentCollectionYamlWriteRestoresInventoryAndEarnings() throws Exception {
        ShopData shop = shop(7L);
        ItemStack[] playerContents = new ItemStack[] { null, null };
        when(playerInventory.getStorageContents()).thenReturn(playerContents);

        assertTrue(manager.add(shop));
        forceYamlWritesToFail("shops.yml");

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

    private void forceYamlWritesToFail(String yamlName) throws Exception {
        Files.deleteIfExists(dataFolder.resolve(yamlName));
        Files.delete(dataFolder);
        Files.writeString(dataFolder, "force YAML writes to fail");
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
