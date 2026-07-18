package me.pinnacle.fragstealers.mail;

import me.pinnacle.fragstealers.FragStealers;
import me.pinnacle.fragstealers.ProtectionType;
import me.pinnacle.fragstealers.data.MailboxData;
import me.pinnacle.fragstealers.data.MailboxManager;
import me.pinnacle.fragstealers.shop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class MailboxListener implements Listener {
    private final FragStealers plugin;
    private final MailboxManager manager;
    private final MailboxMenuService menus;

    public MailboxListener(FragStealers plugin, MailboxManager manager, MailboxMenuService menus) {
        this.plugin = plugin;
        this.manager = manager;
        this.menus = menus;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        MailboxData mailbox = manager.bySign(event.getClickedBlock());
        if (mailbox == null) return;
        event.setCancelled(true);
        menus.openMain(event.getPlayer(), mailbox);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MailboxMenuHolder holder)) return;

        if (holder.type() == MailboxMenuType.MAIN) {
            event.setCancelled(true);
            if (holder.adminOverride() && !plugin.masterKeys().canUse(player)) {
                plugin.getServer().getScheduler().runTask(plugin, player::closeInventory);
                player.sendMessage(plugin.error("Keep the Master Key in your main hand while managing this mailbox."));
                return;
            }
            handleMain(event, player, holder, top);
            return;
        }
        if (holder.type() == MailboxMenuType.DEPOSIT) {
            enforceDeposit(event, player, holder, top);
        } else if (holder.type() == MailboxMenuType.PICKUP) {
            if (holder.adminOverride() && !plugin.masterKeys().canUse(player)) {
                event.setCancelled(true);
                plugin.getServer().getScheduler().runTask(plugin, player::closeInventory);
                player.sendMessage(plugin.error("Keep the Master Key in your main hand while managing this mailbox."));
                return;
            }
            enforcePickup(event, player, top);
            if (holder.adminOverride()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!plugin.masterKeys().canUse(player)) player.closeInventory();
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MailboxMenuHolder holder)) return;
        if (holder.type() == MailboxMenuType.MAIN) {
            event.setCancelled(true);
            return;
        }
        if (holder.type() == MailboxMenuType.PICKUP && holder.adminOverride() && !plugin.masterKeys().canUse(player)) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, player::closeInventory);
            player.sendMessage(plugin.error("Keep the Master Key in your main hand while managing this mailbox."));
            return;
        }
        if (plugin.masterKeys().isMasterKey(event.getOldCursor())) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("Master Keys cannot be mailed."));
            return;
        }
        if (holder.type() == MailboxMenuType.DEPOSIT) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < top.getSize() && holder.blocked(rawSlot)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof MailboxMenuHolder holder)) return;
        if (holder.type() == MailboxMenuType.DEPOSIT) {
            finishDeposit(player, inventory, holder);
        } else if (holder.type() == MailboxMenuType.PICKUP) {
            finishPickup(player, inventory, holder);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        menus.releaseViewer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!manager.ownerHasMail(event.getPlayer().getUniqueId())) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline() && manager.ownerHasMail(event.getPlayer().getUniqueId())) {
                event.getPlayer().sendMessage(Component.text("You've Got Mail!", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            }
        }, 40L);
    }

    private void handleMain(InventoryClickEvent event, Player player, MailboxMenuHolder holder, Inventory top) {
        if (event.getClickedInventory() != top) return;
        MailboxData mailbox = manager.bySign(holder.signKey());
        if (mailbox == null) {
            plugin.getServer().getScheduler().runTask(plugin, player::closeInventory);
            return;
        }
        boolean manage = plugin.canManage(player, mailbox.ownerUuid());
        int slot = event.getSlot();
        if ((manage && slot == MailboxMenuService.OWNER_DEPOSIT_SLOT)
            || (!manage && slot == MailboxMenuService.PUBLIC_DEPOSIT_SLOT)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> menus.openDeposit(player, mailbox));
        } else if (manage && slot == MailboxMenuService.OWNER_PICKUP_SLOT) {
            plugin.getServer().getScheduler().runTask(plugin, () -> menus.openPickup(player, mailbox));
        }
    }

    private void enforceDeposit(InventoryClickEvent event, Player player, MailboxMenuHolder holder, Inventory top) {
        int raw = event.getRawSlot();
        boolean topSlot = raw >= 0 && raw < top.getSize();
        if (event.getClick() == ClickType.DOUBLE_CLICK || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        if (topSlot && (holder.blocked(raw) || menus.isPlaceholder(event.getCurrentItem()))) {
            event.setCancelled(true);
            return;
        }
        ItemStack moving = null;
        if (!topSlot && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            moving = event.getCurrentItem();
        } else if (topSlot && (event.getAction() == InventoryAction.PLACE_ALL || event.getAction() == InventoryAction.PLACE_ONE
            || event.getAction() == InventoryAction.PLACE_SOME || event.getAction() == InventoryAction.SWAP_WITH_CURSOR)) {
            moving = event.getCursor();
        } else if (topSlot && isHotbarAction(event.getAction())) {
            if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
                moving = player.getInventory().getItem(event.getHotbarButton());
            } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
                moving = player.getInventory().getItemInOffHand();
            }
        }
        if (plugin.masterKeys().isMasterKey(moving) || menus.isPlaceholder(moving)) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("That item cannot be placed in a mailbox."));
        }
    }


    private void enforcePickup(InventoryClickEvent event, Player player, Inventory top) {
        int raw = event.getRawSlot();
        boolean topSlot = raw >= 0 && raw < top.getSize();
        ItemStack moving = null;
        if (!topSlot && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            moving = event.getCurrentItem();
        } else if (topSlot && (event.getAction() == InventoryAction.PLACE_ALL || event.getAction() == InventoryAction.PLACE_ONE
            || event.getAction() == InventoryAction.PLACE_SOME || event.getAction() == InventoryAction.SWAP_WITH_CURSOR)) {
            moving = event.getCursor();
        } else if (topSlot && isHotbarAction(event.getAction())) {
            if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
                moving = player.getInventory().getItem(event.getHotbarButton());
            } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
                moving = player.getInventory().getItemInOffHand();
            }
        }
        if (plugin.masterKeys().isMasterKey(moving) || menus.isPlaceholder(moving)) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("That item cannot be placed in a mailbox."));
        }
    }

    private boolean isHotbarAction(InventoryAction action) {
        return action == InventoryAction.HOTBAR_SWAP;
    }

    private void finishDeposit(Player player, Inventory inventory, MailboxMenuHolder holder) {
        menus.releaseDeposit(holder.signKey(), player.getUniqueId());
        MailboxData mailbox = manager.bySign(holder.signKey());
        boolean accepting = mailbox != null && plugin.mailEnabled();
        List<ItemStack> returnItems = new ArrayList<>();
        boolean accepted = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (holder.blocked(slot)) continue;
            ItemStack item = inventory.getItem(slot);
            if (ItemUtil.isEmpty(item) || menus.isPlaceholder(item)) continue;
            if (plugin.masterKeys().isMasterKey(item)) {
                returnItems.add(item.clone());
                continue;
            }
            if (accepting && mailbox.getItem(slot) == null) {
                mailbox.setItem(slot, item);
                accepted = true;
            } else {
                returnItems.add(item.clone());
            }
        }
        if (!returnItems.isEmpty()) ItemUtil.giveOrDrop(player, returnItems);
        if (mailbox != null && accepted) {
            manager.save();
            menus.notifyOwner(mailbox);
            player.sendMessage(plugin.success("Mail deposited for " + mailbox.ownerName() + "."));
        } else if (!accepting && !returnItems.isEmpty()) {
            player.sendMessage(plugin.error("Mailbox deposits are currently disabled. Your items were returned."));
        }
    }

    private void finishPickup(Player player, Inventory inventory, MailboxMenuHolder holder) {
        MailboxData mailbox = manager.bySign(holder.signKey());
        menus.releasePickup(holder.signKey(), player.getUniqueId());
        if (mailbox == null) return;
        ItemStack[] after = inventory.getContents();
        List<ItemStack> rejected = new ArrayList<>();
        for (int slot = 0; slot < after.length; slot++) {
            ItemStack item = after[slot];
            if (plugin.masterKeys().isMasterKey(item) || menus.isPlaceholder(item)) {
                if (!ItemUtil.isEmpty(item) && !menus.isPlaceholder(item)) rejected.add(item.clone());
                after[slot] = null;
            }
        }
        if (!rejected.isEmpty()) ItemUtil.giveOrDrop(player, rejected);
        mailbox.replaceContents(after);
        manager.save();
        if (holder.adminOverride()) {
            String details = ItemUtil.summarizeDifference(holder.before(), after);
            if (!details.equals("No items removed")) {
                plugin.audit().log(player, "WITHDREW_MAIL", ProtectionType.MAILBOX, mailbox.ownerUuid(), mailbox.ownerName(), mailbox.signKey(), details);
            }
        }
    }
}
