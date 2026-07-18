package me.pinnacle.fragstealers;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

public record BlockKey(String worldName, int x, int y, int z) {
    public static BlockKey from(Block block) {
        return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static Optional<BlockKey> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String[] parts = value.split(";", 4);
        if (parts.length != 4) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BlockKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public static Optional<BlockKey> decode(String encoded) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            return parse(raw);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public String serialize() {
        return worldName + ";" + x + ";" + y + ";" + z;
    }

    public String encoded() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(serialize().getBytes(StandardCharsets.UTF_8));
    }

    public Block block() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : world.getBlockAt(x, y, z);
    }
}
