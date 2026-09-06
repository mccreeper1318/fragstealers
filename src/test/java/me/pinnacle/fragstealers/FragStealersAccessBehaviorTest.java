package me.pinnacle.fragstealers;

import me.pinnacle.fragstealers.data.ChestLock;
import me.pinnacle.fragstealers.data.MailboxData;
import me.pinnacle.fragstealers.data.ShopData;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FragStealersAccessBehaviorTest {
    private static final BlockKey SIGN = new BlockKey("world", 10, 64, 10);
    private static final BlockKey CONTAINER = new BlockKey("world", 10, 64, 11);
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TRUSTED = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private FragStealers plugin;
    private MasterKeyManager masterKeys;
    private TrustManager trust;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(FragStealers.class, CALLS_REAL_METHODS);
        masterKeys = mock(MasterKeyManager.class);
        trust = mock(TrustManager.class);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(TRUSTED);
        setField(plugin, "masterKeys", masterKeys);
        setField(plugin, "trust", trust);
    }

    @Test
    void ordinaryLockAuthorizationAcceptsOwnerTrustOrMasterKeyAndRejectsEveryoneElse() {
        ChestLock lock = lock();
        when(masterKeys.canUse(player)).thenReturn(false);
        when(trust.has(ProtectionType.LOCK, SIGN, TRUSTED, TrustLevel.ACCESS)).thenReturn(false);

        assertFalse(plugin.canAccessLock(player, lock));

        when(trust.has(ProtectionType.LOCK, SIGN, TRUSTED, TrustLevel.ACCESS)).thenReturn(true);
        assertTrue(plugin.canAccessLock(player, lock));

        when(trust.has(ProtectionType.LOCK, SIGN, TRUSTED, TrustLevel.ACCESS)).thenReturn(false);
        when(masterKeys.canUse(player)).thenReturn(true);
        assertTrue(plugin.canAccessLock(player, lock));

        Player owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(OWNER);
        assertTrue(plugin.canAccessLock(owner, lock));
    }

    @Test
    void accessLevelShopTrustCanRestockButCannotRemoveOrManageStock() {
        ShopData shop = shop();
        when(masterKeys.canUse(player)).thenReturn(false);
        when(trust.has(ProtectionType.SHOP, SIGN, TRUSTED, TrustLevel.ACCESS)).thenReturn(true);
        when(trust.has(ProtectionType.SHOP, SIGN, TRUSTED, TrustLevel.MANAGE)).thenReturn(false);

        assertTrue(plugin.canRestockShop(player, shop));
        assertFalse(plugin.canManageShop(player, shop));
    }

    @Test
    void accessLevelMailboxTrustCanCollectButRemainsReadOnly() {
        MailboxData mailbox = mailbox();
        when(masterKeys.canUse(player)).thenReturn(false);
        when(trust.has(ProtectionType.MAILBOX, SIGN, TRUSTED, TrustLevel.ACCESS)).thenReturn(true);
        when(trust.has(ProtectionType.MAILBOX, SIGN, TRUSTED, TrustLevel.MANAGE)).thenReturn(false);

        assertTrue(plugin.canCollectMailbox(player, mailbox));
        assertFalse(plugin.canManageMailbox(player, mailbox));
    }

    @Test
    void liveTrustRevocationChangesLockAuthorizationImmediately() {
        ChestLock lock = lock();
        when(masterKeys.canUse(player)).thenReturn(false);
        when(trust.has(ProtectionType.LOCK, SIGN, TRUSTED, TrustLevel.ACCESS)).thenReturn(true, false);

        assertTrue(plugin.canAccessLock(player, lock));
        assertFalse(plugin.canAccessLock(player, lock));
    }

    @Test
    void masterKeyFallbackUsesIndependentAccessTrustAfterKeyRemoval() {
        ShopData shop = shop();
        when(masterKeys.canUse(player)).thenReturn(true, false, false);
        when(trust.has(ProtectionType.SHOP, SIGN, TRUSTED, TrustLevel.ACCESS)).thenReturn(true);
        when(trust.has(ProtectionType.SHOP, SIGN, TRUSTED, TrustLevel.MANAGE)).thenReturn(false);

        assertTrue(plugin.canRestockShop(player, shop), "Master Key should grant the initial stock session");
        assertTrue(plugin.canRestockShop(player, shop), "Access trust should remain valid after the Master Key is removed");
        assertFalse(plugin.canManageShop(player, shop), "Access trust must not silently upgrade to manage permission");
    }

    private ChestLock lock() {
        return new ChestLock(Set.of(CONTAINER), SIGN, OWNER, "Owner");
    }

    private ShopData shop() {
        return new ShopData(SIGN, Set.of(CONTAINER), OWNER, "Owner", org.bukkit.Material.DIAMOND, 1,
            org.bukkit.Material.EMERALD, 1, 0L);
    }

    private MailboxData mailbox() {
        return new MailboxData(SIGN, Set.of(CONTAINER), OWNER, "Owner");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = FragStealers.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
