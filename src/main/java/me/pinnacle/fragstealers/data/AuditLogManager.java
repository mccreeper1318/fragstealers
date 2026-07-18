package me.pinnacle.fragstealers.data;

import me.pinnacle.fragstealers.AtomicYaml;
import me.pinnacle.fragstealers.BlockKey;
import me.pinnacle.fragstealers.FragStealers;
import me.pinnacle.fragstealers.ProtectionType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AuditLogManager {
    private static final long RETENTION_MILLIS = Duration.ofDays(30).toMillis();

    private final FragStealers plugin;
    private final File dataFile;
    private YamlConfiguration data;

    public AuditLogManager(FragStealers plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "audit-log.yml");
    }

    public void loadAndPurge() {
        data = YamlConfiguration.loadConfiguration(dataFile);
        purgeExpired();
    }

    public synchronized void log(Player administrator, String action, ProtectionType type, UUID ownerUuid,
                                 String ownerName, BlockKey location, String details) {
        if (data == null) {
            data = new YamlConfiguration();
        }
        long now = System.currentTimeMillis();
        String id = now + "-" + UUID.randomUUID().toString().substring(0, 8);
        String path = "entries." + id;
        data.set(path + ".epoch-millis", now);
        data.set(path + ".timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(now)));
        data.set(path + ".administrator-uuid", administrator.getUniqueId().toString());
        data.set(path + ".administrator-name", administrator.getName());
        data.set(path + ".action", action);
        data.set(path + ".container-type", type.name());
        data.set(path + ".owner-uuid", ownerUuid.toString());
        data.set(path + ".owner-name", ownerName);
        data.set(path + ".location", location.serialize());
        data.set(path + ".details", details == null ? "" : details);
        save();
        plugin.getLogger().warning("AUDIT: " + administrator.getName() + " " + action + " " + type
            + " owned by " + ownerName + " at " + location.serialize() + ". " + details);
    }

    public synchronized void purgeExpired() {
        if (data == null) {
            data = YamlConfiguration.loadConfiguration(dataFile);
        }
        ConfigurationSection entries = data.getConfigurationSection("entries");
        if (entries == null) {
            save();
            return;
        }
        long cutoff = System.currentTimeMillis() - RETENTION_MILLIS;
        List<String> remove = new ArrayList<>();
        for (String id : entries.getKeys(false)) {
            if (data.getLong("entries." + id + ".epoch-millis", 0L) < cutoff) {
                remove.add(id);
            }
        }
        for (String id : remove) {
            data.set("entries." + id, null);
        }
        if (!remove.isEmpty() || !dataFile.exists()) {
            save();
        }
    }

    public synchronized void save() {
        if (data == null) {
            data = new YamlConfiguration();
        }
        AtomicYaml.save(plugin, data, dataFile);
    }
}
