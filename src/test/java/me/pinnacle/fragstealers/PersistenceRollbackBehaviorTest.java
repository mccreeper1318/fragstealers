package me.pinnacle.fragstealers;

import me.pinnacle.fragstealers.data.LockManager;
import me.pinnacle.fragstealers.data.MailboxData;
import me.pinnacle.fragstealers.data.MailboxManager;
import me.pinnacle.fragstealers.data.ShopData;
import me.pinnacle.fragstealers.data.ShopManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersistenceRollbackBehaviorTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TRUSTED = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final BlockKey SIGN = new BlockKey("world", 40, 64, 40);
    private static final BlockKey CONTAINER = new BlockKey("world", 40, 64, 41);

    @TempDir
    Path tempDir;

    private FragStealers plugin;
    private ContainerResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(FragStealers.class);
        resolver = mock(ContainerResolver.class);
        Path notDirectory = tempDir.resolve("not-a-directory");
        Files.writeString(notDirectory, "force YAML writes to fail");
        when(plugin.getDataFolder()).thenReturn(notDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("FragStealersPersistenceTest"));
    }

    @Test
    void failedLockWriteRollsBackNewProtectionRegistration() {
        LockManager manager = new LockManager(plugin, resolver);
        Block container = block("world", 40, 64, 41);
        Block sign = block("world", 40, 64, 40);

        assertFalse(manager.create(List.of(container), sign, OWNER, "Owner"));
        assertFalse(manager.byContainer(CONTAINER).isPresent());
        assertFalse(manager.bySign(SIGN).isPresent());
    }

    @Test
    void failedShopWriteRollsBackNewShopRegistration() {
        ShopManager manager = new ShopManager(plugin, resolver);
        ShopData shop = new ShopData(SIGN, Set.of(CONTAINER), OWNER, "Owner",
            Material.DIAMOND, 1, Material.EMERALD, 1, 0L);

        assertFalse(manager.add(shop));
        assertNull(manager.bySign(SIGN));
        assertNull(manager.byContainer(CONTAINER));
    }

    @Test
    void failedMailboxWriteRollsBackNewMailboxRegistration() {
        MailboxManager manager = new MailboxManager(plugin, resolver);
        MailboxData mailbox = new MailboxData(SIGN, Set.of(CONTAINER), OWNER, "Owner");

        assertFalse(manager.add(mailbox));
        assertNull(manager.bySign(SIGN));
        assertNull(manager.byContainer(CONTAINER));
    }

    @Test
    void failedTrustWriteRollsBackRequestedTrustChange() {
        TrustManager manager = new TrustManager(plugin);
        ProtectedTarget target = new ProtectedTarget(ProtectionType.LOCK, SIGN, OWNER, "Owner");

        assertFalse(manager.trust(target, TRUSTED, "Trusted", TrustLevel.ACCESS));
        assertFalse(manager.has(ProtectionType.LOCK, SIGN, TRUSTED, TrustLevel.ACCESS));
    }

    private Block block(String worldName, int x, int y, int z) {
        World world = mock(World.class);
        Block block = mock(Block.class);
        when(world.getName()).thenReturn(worldName);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        return block;
    }
}
