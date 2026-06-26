package me.pinnacle.fragstealers;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class LockManager {
    private final FragStealers plugin;
    private final File dataFile;
    private FileConfiguration data;

    private final Map<BlockKey, ChestLock> locksByChest = new HashMap<>();
    private final Map<BlockKey, ChestLock> locksBySign = new HashMap<>();

    public LockManager(FragStealers plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "locks.yml");
    }

    public void load() {
        locksByChest.clear();
        locksBySign.clear();

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }

        data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = data.getConfigurationSection("locks");
        if (section == null) {
            return;
        }

        for (String id : section.getKeys(false)) {
            String path = "locks." + id;
            Optional<BlockKey> signKey = BlockKey.parse(data.getString(path + ".sign"));
            String ownerUuidText = data.getString(path + ".owner-uuid");
            String ownerName = data.getString(path + ".owner-name", "Unknown");
            List<String> chestStrings = data.getStringList(path + ".chests");

            if (signKey.isEmpty() || ownerUuidText == null || chestStrings.isEmpty()) {
                continue;
            }

            UUID ownerUuid;
            try {
                ownerUuid = UUID.fromString(ownerUuidText);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            Set<BlockKey> chestKeys = new LinkedHashSet<>();
            for (String chestString : chestStrings) {
                BlockKey.parse(chestString).ifPresent(chestKeys::add);
            }

            if (chestKeys.isEmpty()) {
                continue;
            }

            ChestLock lock = new ChestLock(chestKeys, signKey.get(), ownerUuid, ownerName);
            locksBySign.put(lock.signKey(), lock);
            for (BlockKey chestKey : lock.chestKeys()) {
                locksByChest.put(chestKey, lock);
            }
        }
    }

    public void save() {
        if (data == null) {
            data = new YamlConfiguration();
        }

        data.set("locks", null);

        int index = 0;
        for (ChestLock lock : uniqueLocks()) {
            String path = "locks.lock-" + index++;
            data.set(path + ".sign", lock.signKey().serialize());
            data.set(path + ".owner-uuid", lock.ownerUuid().toString());
            data.set(path + ".owner-name", lock.ownerName());
            data.set(path + ".chests", lock.chestKeys().stream().map(BlockKey::serialize).toList());
        }

        try {
            data.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save locks.yml", ex);
        }
    }

    public boolean createLock(Collection<Block> chestBlocks, Block signBlock, Player owner) {
        Set<BlockKey> chestKeys = new LinkedHashSet<>();
        for (Block chestBlock : chestBlocks) {
            BlockKey key = BlockKey.from(chestBlock);
            if (locksByChest.containsKey(key)) {
                return false;
            }
            chestKeys.add(key);
        }

        BlockKey signKey = BlockKey.from(signBlock);
        if (locksBySign.containsKey(signKey)) {
            return false;
        }

        ChestLock lock = new ChestLock(chestKeys, signKey, owner.getUniqueId(), owner.getName());
        locksBySign.put(signKey, lock);
        for (BlockKey chestKey : chestKeys) {
            locksByChest.put(chestKey, lock);
        }
        save();
        return true;
    }

    public Optional<ChestLock> getByChest(Block block) {
        return Optional.ofNullable(locksByChest.get(BlockKey.from(block)));
    }

    public Optional<ChestLock> getBySign(Block block) {
        return Optional.ofNullable(locksBySign.get(BlockKey.from(block)));
    }

    public boolean isProtectedBlock(Block block) {
        BlockKey key = BlockKey.from(block);
        return locksByChest.containsKey(key) || locksBySign.containsKey(key);
    }

    public void remove(ChestLock lock) {
        locksBySign.remove(lock.signKey());
        for (BlockKey chestKey : lock.chestKeys()) {
            locksByChest.remove(chestKey);
        }
        save();
    }

    private List<ChestLock> uniqueLocks() {
        return new ArrayList<>(new LinkedHashSet<>(locksBySign.values()));
    }
}
