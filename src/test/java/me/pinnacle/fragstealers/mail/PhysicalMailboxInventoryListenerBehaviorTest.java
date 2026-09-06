package me.pinnacle.fragstealers.mail;

import me.pinnacle.fragstealers.FragStealers;
import me.pinnacle.fragstealers.data.MailboxManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhysicalMailboxInventoryListenerBehaviorTest {
    private FragStealers plugin;
    private MailboxManager manager;
    private PhysicalMailboxInventoryListener listener;

    @BeforeEach
    void setUp() {
        plugin = mock(FragStealers.class);
        manager = mock(MailboxManager.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.error(anyString())).thenReturn(Component.text("error"));
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return mock(BukkitTask.class);
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class));

        listener = new PhysicalMailboxInventoryListener(plugin, manager);
    }

    @Test
    void directPhysicalMailboxOpenIsCancelled() {
        Player player = mock(Player.class);
        Inventory inventory = mock(Inventory.class);
        InventoryOpenEvent event = mock(InventoryOpenEvent.class);

        when(event.getPlayer()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(manager.inventoryBelongsToAny(inventory)).thenReturn(true);

        listener.onInventoryOpen(event);

        verify(event).setCancelled(true);
        verify(player).sendMessage(any(Component.class));
    }

    @Test
    void unexpectedPhysicalMailboxClickFailsClosedAndClosesView() {
        Player player = mock(Player.class);
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        InventoryClickEvent event = mock(InventoryClickEvent.class);

        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(manager.inventoryBelongsToAny(inventory)).thenReturn(true);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(player).closeInventory();
    }

    @Test
    void unexpectedPhysicalMailboxDragFailsClosedAndClosesView() {
        Player player = mock(Player.class);
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        InventoryDragEvent event = mock(InventoryDragEvent.class);

        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(manager.inventoryBelongsToAny(inventory)).thenReturn(true);

        listener.onInventoryDrag(event);

        verify(event).setCancelled(true);
        verify(player).closeInventory();
    }

    @Test
    void legitimateMailboxMenuInventoryIsNotBlocked() {
        Player player = mock(Player.class);
        Inventory inventory = mock(Inventory.class);
        MailboxMenuHolder holder = mock(MailboxMenuHolder.class);
        InventoryOpenEvent event = mock(InventoryOpenEvent.class);

        when(event.getPlayer()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(holder);

        listener.onInventoryOpen(event);

        verify(event, never()).setCancelled(true);
        verify(manager, never()).inventoryBelongsToAny(inventory);
    }

    @Test
    void unrelatedPhysicalInventoryIsNotBlocked() {
        Player player = mock(Player.class);
        Inventory inventory = mock(Inventory.class);
        InventoryOpenEvent event = mock(InventoryOpenEvent.class);

        when(event.getPlayer()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(manager.inventoryBelongsToAny(inventory)).thenReturn(false);

        listener.onInventoryOpen(event);

        verify(event, never()).setCancelled(true);
    }
}
