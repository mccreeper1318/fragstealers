package me.pinnacle.fragstealers.mail;

import me.pinnacle.fragstealers.BlockKey;
import me.pinnacle.fragstealers.ContainerResolver;
import me.pinnacle.fragstealers.FragStealers;
import me.pinnacle.fragstealers.data.MailboxData;
import me.pinnacle.fragstealers.data.MailboxManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailboxPersistenceTransactionBehaviorTest {
    private static final BlockKey SIGN = new BlockKey("world", 60, 64, 60);
    private static final BlockKey CONTAINER = new BlockKey("world", 60, 64, 61);
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @TempDir
    Path tempDir;

    private FragStealers plugin;
    private MailboxManager manager;
    private MailboxMenuService menus;
    private Player player;
    private PlayerInventory playerInventory;
    private MailboxData mailbox;
    private MailboxListener listener;
    private Path dataFolder;
    private Path blocker;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(FragStealers.class);
        ContainerResolver resolver = mock(ContainerResolver.class);
        menus = mock(MailboxMenuService.class);
        player = mock(Player.class);
        playerInventory = mock(PlayerInventory.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        blocker = tempDir.resolve("mail-parent");
        dataFolder = blocker.resolve("mail-data");
        Files.createDirectories(dataFolder);

        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("MailboxPersistenceTransactionBehaviorTest"));
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.mailEnabled()).thenReturn(true);
        when(plugin.error(anyString())).thenReturn(Component.text("error"));
        when(plugin.success(anyString())).thenReturn(Component.text("success"));
        when(player.getUniqueId()).thenReturn(PLAYER);
        when(player.getInventory()).thenReturn(playerInventory);
        when(playerInventory.addItem(any(ItemStack.class))).thenReturn(new java.util.HashMap<>());

        manager = new MailboxManager(plugin, resolver);
        mailbox = new MailboxData(SIGN, Set.of(CONTAINER), OWNER, "Owner");
        assertTrue(manager.add(mailbox));
        listener = new MailboxListener(plugin, manager, menus);
    }

    @Test
    void failedDepositYamlWriteRestoresMailboxAndReturnsSubmittedItems() throws Exception {
        ItemStack submitted = item(Material.DIAMOND);
        forceYamlWritesToFail();

        MailboxMenuHolder holder = new MailboxMenuHolder(SIGN, MailboxMenuType.DEPOSIT, new boolean[27], null, false);
        Inventory inventory = mock(Inventory.class);
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(inventory.getHolder()).thenReturn(holder);
        when(inventory.getSize()).thenReturn(27);
        when(inventory.getItem(0)).thenReturn(submitted);
        when(event.getPlayer()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);

        listener.onInventoryClose(event);

        assertNull(mailbox.getItem(0));
        verify(playerInventory).addItem(submitted);
        verify(menus, never()).notifyOwner(mailbox);
    }

    @Test
    void failedPickupYamlWriteRestoresMailboxWithoutDuplicatingRecoveredSessionItems() throws Exception {
        ItemStack mail = item(Material.DIAMOND);
        when(mail.isSimilar(mail)).thenReturn(true);
        mailbox.setItem(0, mail);
        ItemStack[] before = mailbox.copyContents();
        forceYamlWritesToFail();

        String pickupToken = "pickup-token";
        MailboxMenuHolder holder = new MailboxMenuHolder(SIGN, MailboxMenuType.PICKUP, null, before, false, false, pickupToken);
        Inventory inventory = mock(Inventory.class);
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(inventory.getHolder()).thenReturn(holder);
        when(inventory.getContents()).thenReturn(new ItemStack[27]);
        when(event.getPlayer()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(menus.removePickupItems(player, pickupToken)).thenReturn(List.of(mail));

        listener.onInventoryClose(event);

        assertSame(mail, mailbox.getItem(0));
        verify(menus).removePickupItems(player, pickupToken);
        verify(playerInventory, never()).addItem(mail);
    }

    private void forceYamlWritesToFail() throws Exception {
        Files.deleteIfExists(dataFolder.resolve("mailboxes.yml"));
        Files.delete(dataFolder);
        Files.delete(blocker);
        Files.writeString(blocker, "prevent the data directory from being recreated");
    }

    private ItemStack item(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(1);
        when(item.clone()).thenReturn(item);
        return item;
    }
}
