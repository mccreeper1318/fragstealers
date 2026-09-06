package me.pinnacle.fragstealers;

import me.pinnacle.fragstealers.data.LockManager;
import me.pinnacle.fragstealers.data.MailboxData;
import me.pinnacle.fragstealers.data.MailboxManager;
import me.pinnacle.fragstealers.data.ShopManager;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProtectionListenerMailboxAttachmentBehaviorTest {
    @Test
    void clickingNewlyAttachedMailboxHalfBeforeDeferredRefreshIsDenied() {
        FragStealers plugin = mock(FragStealers.class);
        LockManager locks = mock(LockManager.class);
        ShopManager shops = mock(ShopManager.class);
        MailboxManager mailboxes = mock(MailboxManager.class);
        MasterKeyManager masterKeys = mock(MasterKeyManager.class);
        Player player = mock(Player.class);
        Block newHalf = mock(Block.class);
        MailboxData mailbox = mock(MailboxData.class);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);

        when(plugin.locks()).thenReturn(locks);
        when(plugin.shops()).thenReturn(shops);
        when(plugin.mailboxes()).thenReturn(mailboxes);
        when(plugin.masterKeys()).thenReturn(masterKeys);
        when(plugin.error(anyString())).thenReturn(Component.text("error"));
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(newHalf);
        when(event.getPlayer()).thenReturn(player);
        when(locks.byContainer(newHalf)).thenReturn(Optional.empty());
        when(shops.byContainer(newHalf)).thenReturn(null);
        when(mailboxes.byContainer(newHalf)).thenReturn(mailbox);

        new ProtectionListener(plugin).onContainerInteract(event);

        verify(event).setCancelled(true);
        verify(event).setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        verify(event).setUseItemInHand(org.bukkit.event.Event.Result.DENY);
    }
}
