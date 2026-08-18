package me.pinnacle.fragstealers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TrustManager {
    private final FragStealers plugin;
    private final File dataFile;
    private final Map<TargetKey, Map<UUID, TrustEntry>> entries = new HashMap<>();

    public TrustManager(FragStealers plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "trusted-players.yml");
    }

    public void load() {
        entries.clear();
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection protections = data.getConfigurationSection("protections");
        if (protections == null) {
            return;
        }

        for (String typeName : protections.getKeys(false)) {
            ProtectionType type;
            try {
                type = ProtectionType.valueOf(typeName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping unknown trust protection type " + typeName);
                continue;
            }

            ConfigurationSection typeSection = protections.getConfigurationSection(typeName);
            if (typeSection == null) {
                continue;
            }
            for (String encoded : typeSection.getKeys(false)) {
                BlockKey signKey = BlockKey.decode(encoded).orElse(null);
                ConfigurationSection players = typeSection.getConfigurationSection(encoded + ".players");
                if (signKey == null || players == null) {
                    continue;
                }
                TargetKey target = new TargetKey(type, signKey);
                Map<UUID, TrustEntry> targetEntries = new LinkedHashMap<>();
                for (String uuidText : players.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidText);
                        String path = uuidText + ".";
                        String name = players.getString(path + "name", uuidText);
                        TrustLevel level = TrustLevel.parse(players.getString(path + "level"));
                        if (level != null) {
                            targetEntries.put(uuid, new TrustEntry(uuid, name, level));
                        }
                    } catch (IllegalArgumentException ignored) {
                        plugin.getLogger().warning("Skipping invalid trusted player UUID " + uuidText);
                    }
                }
                if (!targetEntries.isEmpty()) {
                    entries.put(target, targetEntries);
                }
            }
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<TargetKey, Map<UUID, TrustEntry>> targetEntry : entries.entrySet()) {
            TargetKey target = targetEntry.getKey();
            String base = "protections." + target.type().name().toLowerCase(Locale.ROOT) + "."
                + target.signKey().encoded();
            data.set(base + ".sign", target.signKey().serialize());
            for (TrustEntry entry : targetEntry.getValue().values()) {
                String path = base + ".players." + entry.playerUuid();
                data.set(path + ".name", entry.playerName());
                data.set(path + ".level", entry.level().name());
            }
        }
        AtomicYaml.save(plugin, data, dataFile);
    }

    public TrustLevel level(ProtectionType type, BlockKey signKey, UUID playerUuid) {
        TrustEntry entry = entries.getOrDefault(new TargetKey(type, signKey), Map.of()).get(playerUuid);
        return entry == null ? null : entry.level();
    }

    public boolean has(ProtectionType type, BlockKey signKey, UUID playerUuid, TrustLevel required) {
        TrustLevel level = level(type, signKey, playerUuid);
        return level != null && level.allows(required);
    }

    public void trust(ProtectedTarget target, UUID playerUuid, String playerName, TrustLevel level) {
        TargetKey key = new TargetKey(target.type(), target.signKey());
        entries.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
            .put(playerUuid, new TrustEntry(playerUuid, playerName, level));
        save();
    }

    public TrustEntry findByName(ProtectedTarget target, String playerName) {
        if (playerName == null) {
            return null;
        }
        for (TrustEntry entry : list(target)) {
            if (entry.playerName().equalsIgnoreCase(playerName)) {
                return entry;
            }
        }
        return null;
    }

    public boolean untrust(ProtectedTarget target, UUID playerUuid) {
        TargetKey key = new TargetKey(target.type(), target.signKey());
        Map<UUID, TrustEntry> targetEntries = entries.get(key);
        if (targetEntries == null || targetEntries.remove(playerUuid) == null) {
            return false;
        }
        if (targetEntries.isEmpty()) {
            entries.remove(key);
        }
        save();
        return true;
    }

    public List<TrustEntry> list(ProtectedTarget target) {
        List<TrustEntry> result = new ArrayList<>(entries
            .getOrDefault(new TargetKey(target.type(), target.signKey()), Map.of()).values());
        result.sort(Comparator.comparing(TrustEntry::playerName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public void clear(ProtectionType type, BlockKey signKey) {
        if (entries.remove(new TargetKey(type, signKey)) != null) {
            save();
        }
    }

    private record TargetKey(ProtectionType type, BlockKey signKey) {
    }
}

enum TrustLevel {
    ACCESS,
    MANAGE;

    public boolean allows(TrustLevel required) {
        return ordinal() >= required.ordinal();
    }

    public String displayName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TrustLevel parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

record TrustEntry(UUID playerUuid, String playerName, TrustLevel level) {
}

record ProtectedTarget(ProtectionType type, BlockKey signKey, UUID ownerUuid, String ownerName) {
}
