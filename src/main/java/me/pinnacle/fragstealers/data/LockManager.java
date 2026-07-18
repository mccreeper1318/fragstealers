package me.pinnacle.fragstealers.data;

import me.pinnacle.fragstealers.AtomicYaml;
import me.pinnacle.fragstealers.BlockKey;
import me.pinnacle.fragstealers.ContainerResolver;
import me.pinnacle.fragstealers.FragStealers;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class LockManager {
    private final FragStealers plugin;
    private final ContainerResolver resolver;
    private final File dataFile;
    private final Map<BlockKey, ChestLock> byContainer = new HashMap<>();
    private final Map<BlockKey, ChestLock> bySign = new HashMap<>();

    public LockManager(FragStealers plugin, ContainerResolver resolver) {
        this.plugin = plugin;
        this.resolver = resolver;
        this.dataFile = new File(plugin.getDataFolder(), "locks.yml");
    }

    public void load() {
        byContainer.clear();
        bySign.clear();
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = data.getConfigurationSection("locks");
        if (section == null) {
            return;
        }

        for (String id : section.getKeys(false)) {
            String path = "locks." + id;
            Optional<BlockKey> sign = BlockKey.parse(data.getString(path + ".sign"));
            String ownerText = data.getString(path + ".owner-uuid");
            String ownerName = data.getString(path + ".owner-name", "Unknown");
            if (sign.isEmpty() || ownerText == null) {
                continue;
            }
            try {
                UUID owner = UUID.fromString(ownerText);
                Set<BlockKey> containers = new LinkedHashSet<>();
                for (String raw : data.getStringList(path + ".chests")) {
                    BlockKey.parse(raw).ifPresent(containers::add);
                }
                for (String raw : data.getStringList(path + ".containers")) {
                    BlockKey.parse(raw).ifPresent(containers::add);
                }
                if (containers.isEmpty()) {
                    continue;
                }
                register(new ChestLock(containers, sign.get(), owner, ownerName));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping invalid lock entry " + id);
            }
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        int index = 0;
        for (ChestLock lock : uniqueLocks()) {
            String path = "locks.lock-" + index++;
            data.set(path + ".sign", lock.signKey().serialize());
            data.set(path + ".owner-uuid", lock.ownerUuid().toString());
            data.set(path + ".owner-name", lock.ownerName());
            data.set(path + ".containers", lock.containerKeys().stream().map(BlockKey::serialize).toList());
        }
        AtomicYaml.save(plugin, data, dataFile);
    }

    public boolean create(Collection<Block> blocks, Block sign, Player owner) {
        Set<BlockKey> keys = new LinkedHashSet<>();
        for (Block block : blocks) {
            BlockKey key = BlockKey.from(block);
            if (byContainer.containsKey(key)) {
                return false;
            }
            keys.add(key);
        }
        BlockKey signKey = BlockKey.from(sign);
        if (keys.isEmpty() || bySign.containsKey(signKey)) {
            return false;
        }
        register(new ChestLock(keys, signKey, owner.getUniqueId(), owner.getName()));
        save();
        return true;
    }

    public Optional<ChestLock> byContainer(Block block) {
        return Optional.ofNullable(byContainer.get(BlockKey.from(block)));
    }

    public Optional<ChestLock> byContainer(BlockKey key) {
        return Optional.ofNullable(byContainer.get(key));
    }

    public Optional<ChestLock> bySign(Block block) {
        return Optional.ofNullable(bySign.get(BlockKey.from(block)));
    }

    public Optional<ChestLock> bySign(BlockKey key) {
        return Optional.ofNullable(bySign.get(key));
    }

    public boolean isProtectedBlock(Block block) {
        BlockKey key = BlockKey.from(block);
        return byContainer.containsKey(key) || bySign.containsKey(key);
    }

    public void refreshContainers(ChestLock lock) {
        boolean changed = false;
        for (BlockKey existing : new ArrayList<>(lock.containerKeys())) {
            Block block = existing.block();
            if (block == null || !resolver.isSupported(block)) {
                continue;
            }
            for (Block connected : resolver.connectedBlocks(block)) {
                BlockKey key = BlockKey.from(connected);
                ChestLock conflict = byContainer.get(key);
                if (conflict != null && conflict != lock) {
                    continue;
                }
                if (lock.addContainer(key)) {
                    changed = true;
                }
                byContainer.put(key, lock);
            }
        }
        if (changed) {
            save();
        }
    }

    public void remove(ChestLock lock) {
        bySign.remove(lock.signKey());
        for (BlockKey key : lock.containerKeys()) {
            byContainer.remove(key, lock);
        }
        save();
    }

    public void refreshAllContainers() {
        for (ChestLock record : new LinkedHashSet<>(bySign.values())) {
            refreshContainers(record);
        }
    }

    public int count() {
        return bySign.size();
    }

    private void register(ChestLock lock) {
        bySign.put(lock.signKey(), lock);
        for (BlockKey key : lock.containerKeys()) {
            byContainer.put(key, lock);
        }
    }

    private List<ChestLock> uniqueLocks() {
        return new ArrayList<>(new LinkedHashSet<>(bySign.values()));
    }
}
