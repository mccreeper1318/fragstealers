package me.pinnacle.fragstealers;

import me.pinnacle.fragstealers.data.MailboxData;
import me.pinnacle.fragstealers.data.MailboxManager;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MailboxConnectedContainerBehaviorTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final BlockKey SIGN_KEY = new BlockKey("world", 5, 64, 5);
    private static final BlockKey EXISTING_KEY = new BlockKey("world", 5, 64, 6);
    private static final BlockKey NEW_HALF_KEY = new BlockKey("world", 6, 64, 6);

    @TempDir
    Path tempDir;

    @Test
    void newlyAttachedHalfResolvesThroughRegisteredNeighborWithoutPrematureRegistration() throws Exception {
        FragStealers plugin = mock(FragStealers.class);
        ContainerResolver resolver = mock(ContainerResolver.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        MailboxManager manager = new MailboxManager(plugin, resolver);

        Block existing = block(EXISTING_KEY);
        Block newHalf = block(NEW_HALF_KEY);
        MailboxData mailbox = new MailboxData(SIGN_KEY, Set.of(EXISTING_KEY), OWNER, "Owner");
        Map<BlockKey, MailboxData> mappings = containerMappings(manager);
        mappings.put(EXISTING_KEY, mailbox);

        LinkedHashSet<Block> connected = new LinkedHashSet<>();
        connected.add(newHalf);
        connected.add(existing);
        when(resolver.connectedBlocks(newHalf)).thenReturn(connected);

        assertSame(mailbox, manager.byContainer(existing));
        assertSame(mailbox, manager.byContainer(newHalf));
        assertTrue(manager.isProtectedBlock(newHalf));
        assertFalse(mappings.containsKey(NEW_HALF_KEY));
    }

    @Test
    void unrelatedContainerStillDoesNotResolveToMailbox() throws Exception {
        FragStealers plugin = mock(FragStealers.class);
        ContainerResolver resolver = mock(ContainerResolver.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        MailboxManager manager = new MailboxManager(plugin, resolver);

        Block unrelated = block(NEW_HALF_KEY);
        when(resolver.connectedBlocks(unrelated)).thenReturn(Set.of(unrelated));

        assertNull(manager.byContainer(unrelated));
        assertFalse(manager.isProtectedBlock(unrelated));
    }

    @SuppressWarnings("unchecked")
    private Map<BlockKey, MailboxData> containerMappings(MailboxManager manager) throws Exception {
        Field field = MailboxManager.class.getDeclaredField("byContainer");
        field.setAccessible(true);
        return (Map<BlockKey, MailboxData>) field.get(manager);
    }

    private Block block(BlockKey key) {
        World world = mock(World.class);
        Block block = mock(Block.class);
        when(world.getName()).thenReturn(key.worldName());
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(key.x());
        when(block.getY()).thenReturn(key.y());
        when(block.getZ()).thenReturn(key.z());
        return block;
    }
}
