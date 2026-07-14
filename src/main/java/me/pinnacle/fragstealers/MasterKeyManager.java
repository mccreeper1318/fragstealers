package me.pinnacle.fragstealers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class MasterKeyManager {
    public static final String GIVE_PERMISSION = "fragstealers.masterkey.give";
    public static final String USE_PERMISSION = "fragstealers.masterkey.use";

    private final NamespacedKey masterKeyTag;

    public MasterKeyManager(FragStealers plugin) {
        this.masterKeyTag = new NamespacedKey(plugin, "master_key");
    }

    public ItemStack createMasterKey() {
        ItemStack item = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(noItalic(Component.text("FragStealers Master Key", NamedTextColor.GOLD)));
        meta.lore(List.of(
            noItalic(Component.text("Administrative protection override", NamedTextColor.GRAY)),
            noItalic(Component.text("Right-click protected storage to open it", NamedTextColor.YELLOW)),
            noItalic(Component.text("Break a protected sign to remove its lock", NamedTextColor.YELLOW))
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(masterKeyTag, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isMasterKey(ItemStack item) {
        if (item == null || item.getType() != Material.WOODEN_AXE || !item.hasItemMeta()) {
            return false;
        }

        Byte value = item.getItemMeta()
            .getPersistentDataContainer()
            .get(masterKeyTag, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public boolean canUse(Player player) {
        return player.hasPermission(USE_PERMISSION)
            && isMasterKey(player.getInventory().getItemInMainHand());
    }

    private Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
