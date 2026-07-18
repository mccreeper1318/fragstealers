package me.pinnacle.fragstealers.mail;

import me.pinnacle.fragstealers.BlockKey;
import me.pinnacle.fragstealers.FragStealers;
import me.pinnacle.fragstealers.data.MailboxData;
import me.pinnacle.fragstealers.data.MailboxManager;
import me.pinnacle.fragstealers.shop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MailboxMenuService {
    public static final int OWNER_DEPOSIT_SLOT = 11;
    public static final int OWNER_PICKUP_SLOT = 15;
    public static final int PUBLIC_DEPOSIT_SLOT = 13;

    private final FragStealers plugin;
    private final MailboxManager manager;
    private final NamespacedKey placeholderKey;
    private final Map<BlockKey, UUID> activePickup = new HashMap<>();
    private final Map<BlockKey, Set<UUID>> activeDeposits = new HashMap<>();

    public MailboxMenuService(FragStealers plugin, MailboxManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.placeholderKey = new NamespacedKey(plugin, "mailbox_full_slot");
    }

    public void openMain(Player player, MailboxData mailbox) {
        boolean manage = plugin.canManage(player, mailbox.ownerUuid());
        MailboxMenuHolder holder = new MailboxMenuHolder(mailbox.signKey(), MailboxMenuType.MAIN, null, null,
            manage && !mailbox.isOwner(player.getUniqueId()));
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Mailbox: " + mailbox.ownerName()));
        holder.inventory(inventory);
        fill(inventory);

        if (!plugin.mailEnabled()) {
            if (manage) {
                inventory.setItem(OWNER_DEPOSIT_SLOT, item(Material.BARRIER, "Deposits Temporarily Disabled", NamedTextColor.RED));
                inventory.setItem(OWNER_PICKUP_SLOT, item(Material.CHEST, "Pick Up Mail", NamedTextColor.AQUA));
            } else {
                inventory.setItem(PUBLIC_DEPOSIT_SLOT, item(Material.BARRIER, "Mailboxes Temporarily Disabled", NamedTextColor.RED));
            }
        } else if (manage) {
            inventory.setItem(OWNER_DEPOSIT_SLOT, item(Material.HOPPER, "Deposit Mail", NamedTextColor.GREEN));
            inventory.setItem(OWNER_PICKUP_SLOT, item(Material.CHEST, "Pick Up Mail", NamedTextColor.AQUA));
        } else {
            inventory.setItem(PUBLIC_DEPOSIT_SLOT, item(Material.HOPPER, "Deposit Mail", NamedTextColor.GREEN));
        }
        player.openInventory(inventory);
    }

    public void openDeposit(Player player, MailboxData mailbox) {
        if (!plugin.mailEnabled()) {
            player.sendMessage(plugin.error("FragStealers mailbox deposits are currently disabled."));
            return;
        }
        if (activePickup.containsKey(mailbox.signKey())) {
            player.sendMessage(plugin.error("This mailbox is currently being checked. Try again shortly."));
            return;
        }
        int size = mailbox.capacity();
        boolean[] blocked = new boolean[size];
        for (int slot = 0; slot < size; slot++) {
            blocked[slot] = mailbox.getItem(slot) != null;
        }
        MailboxMenuHolder holder = new MailboxMenuHolder(mailbox.signKey(), MailboxMenuType.DEPOSIT, blocked, null, false);
        Inventory inventory = Bukkit.createInventory(holder, size, Component.text("Deposit Mail for " + mailbox.ownerName()));
        holder.inventory(inventory);
        for (int slot = 0; slot < size; slot++) {
            if (blocked[slot]) inventory.setItem(slot, placeholder());
        }
        player.openInventory(inventory);
        activeDeposits.computeIfAbsent(mailbox.signKey(), ignored -> new HashSet<>()).add(player.getUniqueId());
    }

    public void openPickup(Player player, MailboxData mailbox) {
        if (!plugin.canManage(player, mailbox.ownerUuid())) {
            player.sendMessage(plugin.error("Only the mailbox owner can collect this mail."));
            return;
        }
        Set<UUID> depositors = activeDeposits.get(mailbox.signKey());
        if (depositors != null && !depositors.isEmpty()) {
            player.sendMessage(plugin.error("This mailbox is currently receiving mail. Try again shortly."));
            return;
        }
        UUID viewer = activePickup.get(mailbox.signKey());
        if (viewer != null && !viewer.equals(player.getUniqueId())) {
            player.sendMessage(plugin.error("This mailbox is already being checked."));
            return;
        }
        activePickup.put(mailbox.signKey(), player.getUniqueId());
        ItemStack[] before = mailbox.copyContents();
        MailboxMenuHolder holder = new MailboxMenuHolder(mailbox.signKey(), MailboxMenuType.PICKUP, null, before,
            !mailbox.isOwner(player.getUniqueId()));
        Inventory inventory = Bukkit.createInventory(holder, mailbox.capacity(), Component.text("Pick Up Mail"));
        holder.inventory(inventory);
        inventory.setContents(before);
        player.openInventory(inventory);
    }

    public boolean isPlaceholder(ItemStack item) {
        if (ItemUtil.isEmpty(item) || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(placeholderKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public void releaseDeposit(BlockKey signKey, UUID viewer) {
        Set<UUID> viewers = activeDeposits.get(signKey);
        if (viewers == null) return;
        viewers.remove(viewer);
        if (viewers.isEmpty()) activeDeposits.remove(signKey);
    }

    public void releasePickup(BlockKey signKey, UUID viewer) {
        activePickup.remove(signKey, viewer);
    }

    public void releaseViewer(UUID viewer) {
        activePickup.entrySet().removeIf(entry -> entry.getValue().equals(viewer));
        activeDeposits.entrySet().removeIf(entry -> {
            entry.getValue().remove(viewer);
            return entry.getValue().isEmpty();
        });
    }

    public void closeViewers(BlockKey signKey) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof MailboxMenuHolder holder && holder.signKey().equals(signKey)) {
                player.closeInventory();
            }
        }
        activePickup.remove(signKey);
        activeDeposits.remove(signKey);
    }

    public void notifyOwner(MailboxData mailbox) {
        Player owner = Bukkit.getPlayer(mailbox.ownerUuid());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(Component.text("You've Got Mail!", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        }
    }

    private ItemStack placeholder() {
        ItemStack item = item(Material.RED_STAINED_GLASS_PANE, "Mailbox Slot Full", NamedTextColor.RED);
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(Component.text("Existing mail is hidden from depositors.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(placeholderKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private ItemStack item(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
