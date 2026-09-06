package me.pinnacle.fragstealers;

import me.pinnacle.fragstealers.data.ChestLock;
import me.pinnacle.fragstealers.data.LockManager;
import me.pinnacle.fragstealers.data.MailboxData;
import me.pinnacle.fragstealers.data.MailboxManager;
import me.pinnacle.fragstealers.data.ShopManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProtectionListenerBehaviorTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final BlockKey SIGN_KEY = new BlockKey("world", 5, 64, 5);
    private static final BlockKey CONTAINER_KEY = new BlockKey("world", 5, 64, 6);

    private FragStealers plugin;
    private LockManager locks;
    private ShopManager shops;
    private MailboxManager mailboxes;
    private ContainerResolver resolver;
    private TrustManager trust;
    private MasterKeyManager masterKeys;
    private BukkitScheduler scheduler;
    private ProtectionListener listener;

    @BeforeEach
    void setUp() {
        plugin = mock(FragStealers.class);
        locks = mock(LockManager.class);
        shops = mock(ShopManager.class);
        mailboxes = mock(MailboxManager.class);
        resolver = mock(ContainerResolver.class);
        trust = mock(TrustManager.class);
        masterKeys = mock(MasterKeyManager.class);
        scheduler = mock(BukkitScheduler.class);
        Server server = mock(Server.class);

        when(plugin.locks()).thenReturn(locks);
        when(plugin.shops()).thenReturn(shops);
        when(plugin.mailboxes()).thenReturn(mailboxes);
        when(plugin.resolver()).thenReturn(resolver);
        when(plugin.trust()).thenReturn(trust);
        when(plugin.masterKeys()).thenReturn(masterKeys);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.error(anyString())).thenReturn(Component.text("error"));
        when(plugin.success(anyString())).thenReturn(Component.text("ok"));
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return mock(BukkitTask.class);
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class));

        listener = new ProtectionListener(plugin);
    }

    @Test
    void creatingLockClosesPlayersWhoAlreadyHadTheInventoryOpen() {
        Player owner = mock(Player.class);
        HumanEntity staleViewer = mock(HumanEntity.class);
        SignChangeEvent event = mock(SignChangeEvent.class);
        Block sign = mock(Block.class);
        Block container = mock(Block.class);
        Inventory inventory = mock(Inventory.class);
        World world = mock(World.class);

        when(event.getPlayer()).thenReturn(owner);
        when(event.getBlock()).thenReturn(sign);
        when(event.line(0)).thenReturn(Component.text("[fs]"));
        when(event.line(1)).thenReturn(Component.empty());
        when(owner.getUniqueId()).thenReturn(OWNER);
        when(owner.getName()).thenReturn("Owner");
        when(owner.hasPermission("fragstealers.lock.create")).thenReturn(true);
        when(locks.bySign(sign)).thenReturn(Optional.empty());
        when(shops.bySign(sign)).thenReturn(null);
        when(mailboxes.bySign(sign)).thenReturn(null);
        when(resolver.findAttachedContainer(sign)).thenReturn(Optional.of(container));
        when(resolver.connectedBlocks(container)).thenReturn(Set.of(container));
        when(plugin.anyContainerProtected(container)).thenReturn(false);
        when(resolver.inventory(container)).thenReturn(inventory);
        when(inventory.getViewers()).thenReturn(List.of(staleViewer));
        when(world.getName()).thenReturn("world");
        when(sign.getWorld()).thenReturn(world);
        when(sign.getX()).thenReturn(5);
        when(sign.getY()).thenReturn(64);
        when(sign.getZ()).thenReturn(5);
        when(trust.clear(ProtectionType.LOCK, SIGN_KEY)).thenReturn(true);
        when(locks.create(any(), eq(sign), eq(OWNER), eq("Owner"))).thenReturn(true);

        listener.onSignChange(event);

        verify(staleViewer).closeInventory();
    }

    @Test
    void creatingMailboxClosesPlayersWhoAlreadyHadSingleContainerOpen() {
        assertCreatingMailboxClosesExistingViewers(false);
    }

    @Test
    void creatingMailboxClosesPlayersWhoAlreadyHadDoubleChestOpen() {
        assertCreatingMailboxClosesExistingViewers(true);
    }

    @Test
    void revokingTrustWhileLockIsOpenCancelsTheNextInventoryActionAndClosesTheView() {
        Player player = mock(Player.class);
        Block container = mock(Block.class);
        Inventory top = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        InventoryOpenEvent open = mock(InventoryOpenEvent.class);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        ChestLock lock = lock();

        when(player.getUniqueId()).thenReturn(VIEWER);
        when(open.getPlayer()).thenReturn(player);
        when(open.getInventory()).thenReturn(top);
        when(resolver.inventoryBlocks(top)).thenReturn(Set.of(container));
        when(locks.byContainer(container)).thenReturn(Optional.of(lock));
        when(trust.has(ProtectionType.LOCK, SIGN_KEY, VIEWER, TrustLevel.ACCESS)).thenReturn(true);

        listener.onInventoryOpen(open);

        when(click.getWhoClicked()).thenReturn(player);
        when(click.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(top);
        when(plugin.canAccessLock(player, lock)).thenReturn(false);

        listener.onLockInventoryClick(click);

        verify(click).setCancelled(true);
        verify(player).closeInventory();
    }

    @Test
    void removingMasterKeyDuringAdministrativeLockSessionCancelsTheNextActionAndClosesTheView() {
        Player player = mock(Player.class);
        Block container = mock(Block.class);
        Inventory top = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        InventoryOpenEvent open = mock(InventoryOpenEvent.class);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        ChestLock lock = lock();

        when(player.getUniqueId()).thenReturn(VIEWER);
        when(open.getPlayer()).thenReturn(player);
        when(open.getInventory()).thenReturn(top);
        when(resolver.inventoryBlocks(top)).thenReturn(Set.of(container));
        when(locks.byContainer(container)).thenReturn(Optional.of(lock));
        when(trust.has(ProtectionType.LOCK, SIGN_KEY, VIEWER, TrustLevel.ACCESS)).thenReturn(false);
        when(masterKeys.canUse(player)).thenReturn(true, false);
        when(top.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[0]);

        listener.onInventoryOpen(open);

        when(click.getWhoClicked()).thenReturn(player);
        when(click.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(top);

        listener.onLockInventoryClick(click);

        verify(click).setCancelled(true);
        verify(player).closeInventory();
    }

    private void assertCreatingMailboxClosesExistingViewers(boolean doubleChest) {
        Player owner = mock(Player.class);
        HumanEntity staleViewer = mock(HumanEntity.class);
        SignChangeEvent event = mock(SignChangeEvent.class);
        Block sign = mock(Block.class);
        Block primary = mock(Block.class);
        Block secondary = doubleChest ? mock(Block.class) : null;
        Inventory inventory = mock(Inventory.class);
        World world = mock(World.class);
        MailboxData registeredMailbox = mock(MailboxData.class);

        Set<Block> containers = doubleChest ? Set.of(primary, secondary) : Set.of(primary);

        when(event.getPlayer()).thenReturn(owner);
        when(event.getBlock()).thenReturn(sign);
        when(event.line(0)).thenReturn(Component.text("[fs mail]"));
        when(event.line(1)).thenReturn(Component.empty());
        when(owner.getUniqueId()).thenReturn(OWNER);
        when(owner.getName()).thenReturn("Owner");
        when(owner.hasPermission("fragstealers.mail.create")).thenReturn(true);
        when(plugin.mailEnabled()).thenReturn(true);
        when(locks.bySign(sign)).thenReturn(Optional.empty());
        when(shops.bySign(sign)).thenReturn(null);
        when(mailboxes.bySign(sign)).thenReturn(null, registeredMailbox);
        when(resolver.findAttachedContainer(sign)).thenReturn(Optional.of(primary));
        when(resolver.connectedBlocks(primary)).thenReturn(containers);
        when(plugin.anyContainerProtected(any(Block.class))).thenReturn(false);
        when(resolver.inventory(primary)).thenReturn(inventory);
        when(resolver.isEmpty(inventory)).thenReturn(true);
        when(inventory.getViewers()).thenReturn(List.of(staleViewer));
        when(world.getName()).thenReturn("world");
        when(sign.getWorld()).thenReturn(world);
        when(sign.getX()).thenReturn(5);
        when(sign.getY()).thenReturn(64);
        when(sign.getZ()).thenReturn(5);
        when(primary.getWorld()).thenReturn(world);
        when(primary.getX()).thenReturn(5);
        when(primary.getY()).thenReturn(64);
        when(primary.getZ()).thenReturn(6);
        if (secondary != null) {
            when(secondary.getWorld()).thenReturn(world);
            when(secondary.getX()).thenReturn(6);
            when(secondary.getY()).thenReturn(64);
            when(secondary.getZ()).thenReturn(6);
        }
        when(trust.clear(ProtectionType.MAILBOX, SIGN_KEY)).thenReturn(true);
        when(mailboxes.add(any(MailboxData.class))).thenReturn(true);
        doReturn(mock(BukkitTask.class)).when(scheduler).runTask(eq(plugin), any(Runnable.class));

        listener.onSignChange(event);

        verify(staleViewer).closeInventory();
    }

    private ChestLock lock() {
        return new ChestLock(Set.of(CONTAINER_KEY), SIGN_KEY, OWNER, "Owner");
    }
}
