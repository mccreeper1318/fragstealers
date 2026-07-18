package me.pinnacle.fragstealers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

public final class AtomicYaml {
    private AtomicYaml() {
    }

    public static void save(JavaPlugin plugin, YamlConfiguration configuration, File target) {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().severe("Could not create data folder for " + target.getName());
            return;
        }

        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            configuration.save(temporary);
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not safely save " + target.getName(), ex);
            if (temporary.exists() && !temporary.delete()) {
                plugin.getLogger().warning("Could not remove temporary file " + temporary.getName());
            }
        }
    }
}
