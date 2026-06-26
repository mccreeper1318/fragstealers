package me.pinnacle.fragstealers;

import org.bukkit.block.Block;

import java.util.Optional;

public record BlockKey(String worldName, int x, int y, int z) {

    public static BlockKey from(Block block) {
        return new BlockKey(
            block.getWorld().getName(),
            block.getX(),
            block.getY(),
            block.getZ()
        );
    }

    public static Optional<BlockKey> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String[] parts = value.split(";");
        if (parts.length != 4) {
            return Optional.empty();
        }

        try {
            return Optional.of(new BlockKey(
                parts[0],
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3])
            ));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public String serialize() {
        return worldName + ";" + x + ";" + y + ";" + z;
    }
}
