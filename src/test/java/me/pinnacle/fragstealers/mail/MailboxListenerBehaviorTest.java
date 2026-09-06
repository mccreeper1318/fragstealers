package me.pinnacle.fragstealers.mail;

import me.pinnacle.fragstealers.BlockKey;
import me.pinnacle.fragstealers.FragStealers;
import me.pinnacle.fragstealers.MasterKeyManager;
import me.pinnacle.fragstealers.data.MailboxData;
import me.pinnacle.fragstealers.data.MailboxManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailboxListenerBehaviorTest {
    private static final BlockKey SIGN = new BlockKey("world", 30, 64, 30);
    private static final BlockKey CONTAINER = new BlockKey("world", 30, 64, 31);
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TRUSTED = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private FragStealers plugin;
    private MailboxManager manager;
    private MailboxMenuService menus;
    private MasterKeyManager masterKeys;
    private Player player;
    private MailboxData mailbox;
    private MailboxListener listener;

    @BeforeEach
    void setUp() {
        plugin = mock(FragStealers.class);
        manager = mock(MailboxManager.class);
        menus = mock(MailboxMenuService.class);
        masterKeys = mock(MasterKeyManager.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        Server server = mock(Server.class);
        player = mock(Player.class);
        mailbox = new MailboxData(SIGN, Set.of(CONTAINER), OWNER, "Owner");

        when(plugin.masterKeys()).thenReturn(masterKeys);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.error(anyString())).thenReturn(Component.text("error"));
        when(plugin.success(anyString())).thenReturn(Component.text("success"));
        when(player.getUniqueId()).thenReturn(TRUSTED);
        when(manager.bySign(SIGN)).thenReturn(mailbox);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return mock(BukkitTask.class);
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class));

        listener = new MailboxListener(plugin, manager, menus);
    }

    @Test
    void accessLevelPickupCanRemoveMailButCannotInsertOrRearrangeIt() {
        when(plugin.canCollectMailbox(player, mailbox)).thenReturn(true);
        when(plugin.canManageMailbox(player, mailbox)).thenReturn(false);
        MailboxMenuHolder holder = pickupHolder(true);

        InventoryClickEvent collect = click(holder, 0, InventoryAction.PICKUP_ALL);
        listener.onInventoryClick(collect);
        verify(collect, never()).setCancelled(true);

        InventoryClickEvent insert = click(holder, 30, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        listener.onInventoryClick(insert);
        verify(insert).setCancelled(true);
    }

    @Test
    void creativeCloneStackIsBlockedInReadOnlyPickupView() {
        when(plugin.canCollectMailbox(player, mailbox)).thenReturn(true);
        when(plugin.canManageMailbox(player, mailbox)).thenReturn(false);

        InventoryClickEvent clone = click(pickupHolder(true), 0, InventoryAction.CLONE_STACK);
        listener.onInventoryClick(clone);

        verify(clone).setCancelled(true);
    }

    @Test
    void manageToAccessDowngradeImmediatelyMakesExistingPickupViewReadOnly() {
        when(plugin.canCollectMailbox(player, mailbox)).thenReturn(true);
        when(plugin.canManageMailbox(player, mailbox)).thenReturn(false);

        InventoryClickEvent insert = click(pickupHolder(false), 30, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        listener.onInventoryClick(insert);

        verify(insert).setCancelled(true);
    }

    @Test
    void masterKeysCannotBeInsertedIntoMailboxDepositViews() {
        MailboxMenuHolder holder = new MailboxMenuHolder(SIGN, MailboxMenuType.DEPOSIT, new boolean[27], null, false);
        ItemStack masterKey = mock(ItemStack.class);
        when(masterKeys.isMasterKey(masterKey)).thenReturn(true);

        InventoryClickEvent event = click(holder, 0, InventoryAction.PLACE_ALL);
        when(event.getCursor()).thenReturn(masterKey);
        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
    }

    @Test
    void failedDepositPersistenceRestoresMailboxContents() {
        when(plugin.mailEnabled()).thenReturn(true);
        when(manager.save()).thenReturn(false);
        ItemStack submitted = item(Material.DIAMOND);
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
        verify(manager).save();
        verify(menus, never()).notifyOwner(mailbox);
    }

    @Test
    void failedPickupPersistenceRestoresMailboxAfterRecoveringSessionItems() {
        ItemStack mail = item(Material.DIAMOND);
        when(mail.isSimilar(mail)).thenReturn(true);
        mailbox.setItem(0, mail);
        ItemStack[] before = mailbox.copyContents();
        MailboxMenuHolder holder = new MailboxMenuHolder(SIGN, MailboxMenuType.PICKUP, null, before, false, false, "pickup-token");
        Inventory inventory = mock(Inventory.class);
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(inventory.getHolder()).thenReturn(holder);
        when(inventory.getContents()).thenReturn(new ItemStack[27]);
        when(event.getPlayer()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(manager.save()).thenReturn(false);
        when(menus.removePickupItems(player, "pickup-token")).thenReturn(List.of(mail));

        listener.onInventoryClose(event);

        assertSame(mail, mailbox.getItem(0));
        verify(manager).save();
        verify(menus).removePickupItems(player, "pickup-token");
    }

    private MailboxMenuHolder pickupHolder(boolean readOnly) {
        return new MailboxMenuHolder(SIGN, MailboxMenuType.PICKUP, null, new ItemStack[27], false, readOnly);
    }

    private InventoryClickEvent click(MailboxMenuHolder holder, int rawSlot, InventoryAction action) {
        Inventory top = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(top.getHolder()).thenReturn(holder);
        when(top.getSize()).thenReturn(27);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(top);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getAction()).thenReturn(action);
        return event;
    }

    private ItemStack item(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(1);
        when(item.clone()).thenReturn(item);
        return item;
    }
}
