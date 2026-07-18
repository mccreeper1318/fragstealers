package me.pinnacle.fragstealers.data;

import me.pinnacle.fragstealers.AtomicYaml;
import me.pinnacle.fragstealers.BlockKey;
import me.pinnacle.fragstealers.ContainerResolver;
import me.pinnacle.fragstealers.FragStealers;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ShopManager {
    private final FragStealers plugin;
    private final ContainerResolver resolver;
    private final File dataFile;
    private final Map<BlockKey, ShopData> bySign = new HashMap<>();
    private final Map<BlockKey, ShopData> byContainer = new HashMap<>();

    public ShopManager(FragStealers plugin, ContainerResolver resolver) {
        this.plugin = plugin;
        this.resolver = resolver;
        this.dataFile = new File(plugin.getDataFolder(), "shops.yml");
    }

    public void load() {
        bySign.clear();
        byContainer.clear();
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection root = data.getConfigurationSection("shops");
        if (root == null) {
            return;
        }
        for (String encoded : root.getKeys(false)) {
            Optional<BlockKey> sign = BlockKey.decode(encoded);
            if (sign.isEmpty()) {
                continue;
            }
            String path = "shops." + encoded;
            try {
                UUID owner = UUID.fromString(data.getString(path + ".owner-uuid", ""));
                String ownerName = data.getString(path + ".owner-name", "Unknown");
                Material sell = material(data.getString(path + ".sell-material"));
                Material price = material(data.getString(path + ".price-material"));
                int sellAmount = data.getInt(path + ".sell-amount", 0);
                int priceAmount = data.getInt(path + ".price-amount", 0);
                long earnings = data.getLong(path + ".earnings", 0L);
                Set<BlockKey> containers = new LinkedHashSet<>();
                for (String raw : data.getStringList(path + ".containers")) {
                    BlockKey.parse(raw).ifPresent(containers::add);
                }
                if (containers.isEmpty()) {
                    continue;
                }
                ShopData shop = new ShopData(sign.get(), containers, owner, ownerName, sell, sellAmount, price, priceAmount, earnings);
                if (!shop.isConfigured()) {
                    shop = new ShopData(sign.get(), containers, owner, ownerName, null, 0, null, 0, 0L);
                }
                register(shop);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipping invalid shop " + encoded + ": " + ex.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (ShopData shop : bySign.values()) {
            String path = "shops." + shop.signKey().encoded();
            data.set(path + ".sign", shop.signKey().serialize());
            data.set(path + ".owner-uuid", shop.ownerUuid().toString());
            data.set(path + ".owner-name", shop.ownerName());
            data.set(path + ".configured", shop.isConfigured());
            data.set(path + ".sell-material", shop.sellMaterial() == null ? null : shop.sellMaterial().name());
            data.set(path + ".sell-amount", shop.sellAmount());
            data.set(path + ".price-material", shop.priceMaterial() == null ? null : shop.priceMaterial().name());
            data.set(path + ".price-amount", shop.priceAmount());
            data.set(path + ".earnings", shop.isConfigured() ? shop.earnings() : 0L);
            data.set(path + ".containers", shop.containerKeys().stream().map(BlockKey::serialize).toList());
        }
        AtomicYaml.save(plugin, data, dataFile);
    }

    public void add(ShopData shop) {
        register(shop);
        save();
    }

    public void remove(ShopData shop) {
        bySign.remove(shop.signKey());
        for (BlockKey key : shop.containerKeys()) {
            byContainer.remove(key, shop);
        }
        save();
    }

    public ShopData bySign(Block block) { return bySign.get(BlockKey.from(block)); }
    public ShopData bySign(BlockKey key) { return bySign.get(key); }
    public ShopData byContainer(Block block) { return byContainer.get(BlockKey.from(block)); }
    public ShopData byContainer(BlockKey key) { return byContainer.get(key); }

    public boolean isProtectedBlock(Block block) {
        BlockKey key = BlockKey.from(block);
        return bySign.containsKey(key) || byContainer.containsKey(key);
    }

    public Inventory inventory(ShopData shop) {
        return resolver.inventory(shop.containerKeys());
    }

    public boolean inventoryBelongs(Inventory inventory, ShopData shop) {
        return resolver.inventoryTouches(inventory, block -> byContainer(block) == shop);
    }

    public boolean inventoryBelongsToAny(Inventory inventory) {
        return resolver.inventoryTouches(inventory, block -> byContainer(block) != null);
    }

    public void refreshContainers(ShopData shop) {
        boolean changed = false;
        for (BlockKey existing : new ArrayList<>(shop.containerKeys())) {
            Block block = existing.block();
            if (block == null || !resolver.isSupported(block)) {
                continue;
            }
            for (Block connected : resolver.connectedBlocks(block)) {
                BlockKey key = BlockKey.from(connected);
                ShopData conflict = byContainer.get(key);
                if (conflict != null && conflict != shop) {
                    continue;
                }
                if (shop.addContainer(key)) {
                    changed = true;
                }
                byContainer.put(key, shop);
            }
        }
        if (changed) {
            save();
        }
    }

    public void refreshAllContainers() {
        for (ShopData record : new LinkedHashSet<>(bySign.values())) {
            refreshContainers(record);
        }
    }

    public List<ShopData> all() { return List.copyOf(new LinkedHashSet<>(bySign.values())); }

    public int count() { return bySign.size(); }

    private void register(ShopData shop) {
        bySign.put(shop.signKey(), shop);
        for (BlockKey key : shop.containerKeys()) {
            byContainer.put(key, shop);
        }
    }

    private Material material(String value) {
        return value == null || value.isBlank() ? null : Material.matchMaterial(value);
    }
}
