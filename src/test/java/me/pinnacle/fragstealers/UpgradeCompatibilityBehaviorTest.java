package me.pinnacle.fragstealers;

import me.pinnacle.fragstealers.data.AuditLogManager;
import me.pinnacle.fragstealers.data.ChestLock;
import me.pinnacle.fragstealers.data.LockManager;
import me.pinnacle.fragstealers.data.MailboxData;
import me.pinnacle.fragstealers.data.MailboxManager;
import me.pinnacle.fragstealers.data.ShopData;
import me.pinnacle.fragstealers.data.ShopManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpgradeCompatibilityBehaviorTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TRUSTED = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final BlockKey LOCK_SIGN = new BlockKey("world", 10, 64, 10);
    private static final BlockKey LOCK_CONTAINER = new BlockKey("world", 10, 64, 11);
    private static final BlockKey SHOP_SIGN = new BlockKey("world", 20, 64, 20);
    private static final BlockKey SHOP_CONTAINER = new BlockKey("world", 20, 64, 21);
    private static final BlockKey MAIL_SIGN = new BlockKey("world", 30, 64, 30);
    private static final BlockKey MAIL_CONTAINER = new BlockKey("world", 30, 64, 31);

    @TempDir
    Path tempDir;

    @Test
    void released26_2DataLoadsSavesAndReloadsWithoutMigration() throws Exception {
        FragStealers plugin = mock(FragStealers.class);
        ContainerResolver resolver = mock(ContainerResolver.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("UpgradeCompatibilityBehaviorTest"));

        writeReleased26_2Fixtures();
        String originalConfig = Files.readString(tempDir.resolve("config.yml"));

        LockManager locks = new LockManager(plugin, resolver);
        ShopManager shops = new ShopManager(plugin, resolver);
        MailboxManager mailboxes = new MailboxManager(plugin, resolver);
        TrustManager trust = new TrustManager(plugin);
        AuditLogManager audit = new AuditLogManager(plugin);

        locks.load();
        shops.load();
        mailboxes.load();
        trust.load();
        audit.loadAndPurge();

        assertLoadedState(locks, shops, mailboxes, trust);
        assertAuditEntryPresent();

        locks.save();
        shops.save();
        mailboxes.save();
        trust.save();
        audit.save();

        assertEquals(originalConfig, Files.readString(tempDir.resolve("config.yml")),
            "Existing 26.2 configuration must not be rewritten by persistence loading/saving");

        LockManager reloadedLocks = new LockManager(plugin, resolver);
        ShopManager reloadedShops = new ShopManager(plugin, resolver);
        MailboxManager reloadedMailboxes = new MailboxManager(plugin, resolver);
        TrustManager reloadedTrust = new TrustManager(plugin);
        AuditLogManager reloadedAudit = new AuditLogManager(plugin);

        reloadedLocks.load();
        reloadedShops.load();
        reloadedMailboxes.load();
        reloadedTrust.load();
        reloadedAudit.loadAndPurge();

        assertLoadedState(reloadedLocks, reloadedShops, reloadedMailboxes, reloadedTrust);
        assertAuditEntryPresent();
    }

    private void writeReleased26_2Fixtures() throws Exception {
        YamlConfiguration locks = new YamlConfiguration();
        locks.set("locks.lock-0.sign", LOCK_SIGN.serialize());
        locks.set("locks.lock-0.owner-uuid", OWNER.toString());
        locks.set("locks.lock-0.owner-name", "Owner");
        locks.set("locks.lock-0.containers", List.of(LOCK_CONTAINER.serialize()));
        locks.save(tempDir.resolve("locks.yml").toFile());

        YamlConfiguration shops = new YamlConfiguration();
        String shopPath = "shops." + SHOP_SIGN.encoded();
        shops.set(shopPath + ".sign", SHOP_SIGN.serialize());
        shops.set(shopPath + ".owner-uuid", OWNER.toString());
        shops.set(shopPath + ".owner-name", "Owner");
        shops.set(shopPath + ".configured", true);
        shops.set(shopPath + ".sell-material", Material.DIAMOND.name());
        shops.set(shopPath + ".sell-amount", 2);
        shops.set(shopPath + ".price-material", Material.EMERALD.name());
        shops.set(shopPath + ".price-amount", 3);
        shops.set(shopPath + ".earnings", 12L);
        shops.set(shopPath + ".containers", List.of(SHOP_CONTAINER.serialize()));
        shops.save(tempDir.resolve("shops.yml").toFile());

        YamlConfiguration mailboxes = new YamlConfiguration();
        String mailPath = "mailboxes." + MAIL_SIGN.encoded();
        mailboxes.set(mailPath + ".sign", MAIL_SIGN.serialize());
        mailboxes.set(mailPath + ".owner-uuid", OWNER.toString());
        mailboxes.set(mailPath + ".owner-name", "Owner");
        mailboxes.set(mailPath + ".containers", List.of(MAIL_CONTAINER.serialize()));
        mailboxes.save(tempDir.resolve("mailboxes.yml").toFile());

        YamlConfiguration trusted = new YamlConfiguration();
        String trustPath = "protections.lock." + LOCK_SIGN.encoded() + ".players." + TRUSTED;
        trusted.set("protections.lock." + LOCK_SIGN.encoded() + ".sign", LOCK_SIGN.serialize());
        trusted.set(trustPath + ".name", "TrustedPlayer");
        trusted.set(trustPath + ".level", TrustLevel.MANAGE.name());
        trusted.save(tempDir.resolve("trusted-players.yml").toFile());

        YamlConfiguration audit = new YamlConfiguration();
        String auditPath = "entries.runtime-upgrade-fixture";
        audit.set(auditPath + ".epoch-millis", System.currentTimeMillis());
        audit.set(auditPath + ".timestamp", "2026-09-28T20:00:00Z");
        audit.set(auditPath + ".administrator-uuid", TRUSTED.toString());
        audit.set(auditPath + ".administrator-name", "Administrator");
        audit.set(auditPath + ".action", "REMOVED_LOCK");
        audit.set(auditPath + ".container-type", ProtectionType.LOCK.name());
        audit.set(auditPath + ".owner-uuid", OWNER.toString());
        audit.set(auditPath + ".owner-name", "Owner");
        audit.set(auditPath + ".location", LOCK_SIGN.serialize());
        audit.set(auditPath + ".details", "26.2 compatibility fixture");
        audit.save(tempDir.resolve("audit-log.yml").toFile());

        Files.writeString(tempDir.resolve("config.yml"), "shops-enabled: true\nmail-enabled: true\nhoppers:\n  take-from-locked: false\n  put-into-locked: true\n");
    }

    private void assertLoadedState(LockManager locks, ShopManager shops, MailboxManager mailboxes, TrustManager trust) {
        assertEquals(1, locks.count());
        ChestLock lock = locks.bySign(LOCK_SIGN).orElseThrow();
        assertEquals(OWNER, lock.ownerUuid());
        assertTrue(lock.containerKeys().contains(LOCK_CONTAINER));

        assertEquals(1, shops.count());
        ShopData shop = shops.bySign(SHOP_SIGN);
        assertNotNull(shop);
        assertEquals(Material.DIAMOND, shop.sellMaterial());
        assertEquals(2, shop.sellAmount());
        assertEquals(Material.EMERALD, shop.priceMaterial());
        assertEquals(3, shop.priceAmount());
        assertEquals(12L, shop.earnings());
        assertTrue(shop.containerKeys().contains(SHOP_CONTAINER));

        assertEquals(1, mailboxes.count());
        MailboxData mailbox = mailboxes.bySign(MAIL_SIGN);
        assertNotNull(mailbox);
        assertTrue(mailbox.containerKeys().contains(MAIL_CONTAINER));

        assertEquals(TrustLevel.MANAGE, trust.level(ProtectionType.LOCK, LOCK_SIGN, TRUSTED));
    }

    private void assertAuditEntryPresent() {
        YamlConfiguration audit = YamlConfiguration.loadConfiguration(tempDir.resolve("audit-log.yml").toFile());
        assertEquals("REMOVED_LOCK", audit.getString("entries.runtime-upgrade-fixture.action"));
        assertEquals("26.2 compatibility fixture", audit.getString("entries.runtime-upgrade-fixture.details"));
    }
}
