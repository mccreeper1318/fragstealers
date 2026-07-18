package me.pinnacle.fragstealers.data;

import me.pinnacle.fragstealers.AtomicYaml;
import me.pinnacle.fragstealers.BlockKey;
import me.pinnacle.fragstealers.ContainerResolver;
import me.pinnacle.fragstealers.FragStealers;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MailboxManager {
    private final FragStealers plugin;
    private final ContainerResolver resolver;
    private final File dataFile;
    private final Map<BlockKey, MailboxData> bySign = new HashMap<>();
    private final Map<BlockKey, MailboxData> byContainer = new HashMap<>();

    public MailboxManager(FragStealers plugin, ContainerResolver resolver) {
        this.plugin = plugin;
        this.resolver = resolver;
        this.dataFile = new File(plugin.getDataFolder(), "mailboxes.yml");
    }

    public void load() {
        bySign.clear();
        byContainer.clear();
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection root = data.getConfigurationSection("mailboxes");
        if (root == null) {
            return;
        }
        for (String encoded : root.getKeys(false)) {
            Optional<BlockKey> sign = BlockKey.decode(encoded);
            if (sign.isEmpty()) {
                continue;
            }
            String path = "mailboxes." + encoded;
            try {
                UUID owner = UUID.fromString(data.getString(path + ".owner-uuid", ""));
                String ownerName = data.getString(path + ".owner-name", "Unknown");
                Set<BlockKey> containers = new LinkedHashSet<>();
                for (String raw : data.getStringList(path + ".containers")) {
                    BlockKey.parse(raw).ifPresent(containers::add);
                }
                if (containers.isEmpty()) {
                    continue;
                }
                MailboxData mailbox = new MailboxData(sign.get(), containers, owner, ownerName);
                ConfigurationSection items = data.getConfigurationSection(path + ".items");
                if (items != null) {
                    for (String slotText : items.getKeys(false)) {
                        try {
                            int slot = Integer.parseInt(slotText);
                            mailbox.setItem(slot, items.getItemStack(slotText));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                register(mailbox);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipping invalid mailbox " + encoded + ": " + ex.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (MailboxData mailbox : bySign.values()) {
            String path = "mailboxes." + mailbox.signKey().encoded();
            data.set(path + ".sign", mailbox.signKey().serialize());
            data.set(path + ".owner-uuid", mailbox.ownerUuid().toString());
            data.set(path + ".owner-name", mailbox.ownerName());
            data.set(path + ".containers", mailbox.containerKeys().stream().map(BlockKey::serialize).toList());
            for (int slot = 0; slot < mailbox.capacity(); slot++) {
                ItemStack item = mailbox.getItem(slot);
                if (item != null) {
                    data.set(path + ".items." + slot, item);
                }
            }
        }
        AtomicYaml.save(plugin, data, dataFile);
    }

    public void add(MailboxData mailbox) {
        register(mailbox);
        save();
    }

    public void remove(MailboxData mailbox) {
        bySign.remove(mailbox.signKey());
        for (BlockKey key : mailbox.containerKeys()) {
            byContainer.remove(key, mailbox);
        }
        save();
    }

    public MailboxData bySign(Block block) { return bySign.get(BlockKey.from(block)); }
    public MailboxData bySign(BlockKey key) { return bySign.get(key); }
    public MailboxData byContainer(Block block) { return byContainer.get(BlockKey.from(block)); }
    public MailboxData byContainer(BlockKey key) { return byContainer.get(key); }

    public boolean isProtectedBlock(Block block) {
        BlockKey key = BlockKey.from(block);
        return bySign.containsKey(key) || byContainer.containsKey(key);
    }

    public boolean inventoryBelongsToAny(Inventory inventory) {
        return resolver.inventoryTouches(inventory, block -> byContainer(block) != null);
    }

    public void refreshContainers(MailboxData mailbox) {
        boolean changed = false;
        for (BlockKey existing : new ArrayList<>(mailbox.containerKeys())) {
            Block block = existing.block();
            if (block == null || !resolver.isSupported(block)) {
                continue;
            }
            for (Block connected : resolver.connectedBlocks(block)) {
                BlockKey key = BlockKey.from(connected);
                MailboxData conflict = byContainer.get(key);
                if (conflict != null && conflict != mailbox) {
                    continue;
                }
                if (mailbox.addContainer(key)) {
                    changed = true;
                }
                byContainer.put(key, mailbox);
            }
        }
        if (changed) {
            save();
        }
    }

    public boolean ownerHasMail(UUID owner) {
        return bySign.values().stream().anyMatch(mailbox -> mailbox.ownerUuid().equals(owner) && mailbox.hasMail());
    }

    public void refreshAllContainers() {
        for (MailboxData record : new LinkedHashSet<>(bySign.values())) {
            refreshContainers(record);
        }
    }

    public int count() { return bySign.size(); }

    private void register(MailboxData mailbox) {
        bySign.put(mailbox.signKey(), mailbox);
        for (BlockKey key : mailbox.containerKeys()) {
            byContainer.put(key, mailbox);
        }
    }
}
