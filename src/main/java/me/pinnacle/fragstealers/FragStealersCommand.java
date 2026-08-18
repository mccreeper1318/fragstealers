package me.pinnacle.fragstealers;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FragStealersCommand implements CommandExecutor, TabCompleter {
    private static final String RELOAD_PERMISSION = "fragstealers.admin.reload";
    private static final String TRUST_PERMISSION = "fragstealers.trust.manage";
    private static final int TARGET_DISTANCE = 6;
    private final FragStealers plugin;

    public FragStealersCommand(FragStealers plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(RELOAD_PERMISSION)) {
                sender.sendMessage(plugin.error("You do not have permission to reload FragStealers."));
                return true;
            }
            plugin.reloadSettings();
            sender.sendMessage(plugin.success("Configuration reloaded."));
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("masterkey") && args.length <= 3) {
            return giveMasterKey(sender, args);
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("trust")) {
            return trustPlayer(sender, args);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("untrust")) {
            return untrustPlayer(sender, args[1]);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("trusted")) {
            return listTrusted(sender);
        }

        sender.sendMessage(plugin.success("Commands: /fs trust <player> [access|manage], /fs untrust <player>, /fs trusted, /fs give masterkey [player], /fs reload"));
        return true;
    }

    private boolean trustPlayer(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(plugin.error("Usage: /fs trust <player> [access|manage]"));
            return true;
        }
        Player owner = trustOwner(sender);
        if (owner == null) return true;
        ProtectedTarget target = ownedTarget(owner);
        if (target == null) return true;

        TrustLevel level = args.length == 3 ? TrustLevel.parse(args[2]) : TrustLevel.ACCESS;
        if (level == null) {
            owner.sendMessage(plugin.error("Trust level must be access or manage."));
            return true;
        }

        OfflinePlayer trusted = Bukkit.getPlayerExact(args[1]);
        if (trusted == null) {
            trusted = plugin.getServer().getOfflinePlayerIfCached(args[1]);
        }
        if (trusted == null || trusted.getName() == null) {
            owner.sendMessage(plugin.error("Player '" + args[1] + "' is not known to this server."));
            return true;
        }
        if (trusted.getUniqueId().equals(owner.getUniqueId())) {
            owner.sendMessage(plugin.error("You already own this protection."));
            return true;
        }

        plugin.trust().trust(target, trusted.getUniqueId(), trusted.getName(), level);
        owner.sendMessage(plugin.success(trusted.getName() + " now has " + level.displayName()
            + " access to this " + target.type().name().toLowerCase(Locale.ROOT) + "."));
        return true;
    }

    private boolean untrustPlayer(CommandSender sender, String playerName) {
        Player owner = trustOwner(sender);
        if (owner == null) return true;
        ProtectedTarget target = ownedTarget(owner);
        if (target == null) return true;

        TrustEntry entry = plugin.trust().findByName(target, playerName);
        if (entry == null || !plugin.trust().untrust(target, entry.playerUuid())) {
            owner.sendMessage(plugin.error(playerName + " is not trusted on this protection."));
            return true;
        }
        owner.sendMessage(plugin.success("Removed " + entry.playerName() + " from this protection's trust list."));
        return true;
    }

    private boolean listTrusted(CommandSender sender) {
        Player owner = trustOwner(sender);
        if (owner == null) return true;
        ProtectedTarget target = ownedTarget(owner);
        if (target == null) return true;

        List<TrustEntry> entries = plugin.trust().list(target);
        if (entries.isEmpty()) {
            owner.sendMessage(plugin.success("No players are trusted on this protection."));
            return true;
        }
        owner.sendMessage(plugin.success("Trusted players for this " + target.type().name().toLowerCase(Locale.ROOT) + ":"));
        for (TrustEntry entry : entries) {
            owner.sendMessage(Component.text("- " + entry.playerName() + " (" + entry.level().displayName() + ")", NamedTextColor.YELLOW));
        }
        return true;
    }

    private Player trustOwner(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.error("Trust commands must be used in game while looking at a protected sign or container."));
            return null;
        }
        if (!player.hasPermission(TRUST_PERMISSION)) {
            player.sendMessage(plugin.error("You do not have permission to manage trusted players."));
            return null;
        }
        return player;
    }

    private ProtectedTarget ownedTarget(Player player) {
        Block block = player.getTargetBlockExact(TARGET_DISTANCE);
        ProtectedTarget target = block == null ? null : plugin.protectedTarget(block);
        if (target == null) {
            player.sendMessage(plugin.error("Look directly at a FragStealers sign or protected container within " + TARGET_DISTANCE + " blocks."));
            return null;
        }
        if (!target.ownerUuid().equals(player.getUniqueId())) {
            player.sendMessage(plugin.error("Only " + target.ownerName() + " can change this protection's trusted players."));
            return null;
        }
        return target;
    }

    private boolean giveMasterKey(CommandSender sender, String[] args) {
        if (!sender.hasPermission(MasterKeyManager.GIVE_PERMISSION)) {
            sender.sendMessage(plugin.error("You do not have permission to give Master Keys."));
            return true;
        }
        Player target;
        if (args.length == 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(plugin.error("Player " + args[2] + " is not online."));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(plugin.error("Console must specify an online player."));
            return true;
        }

        ItemStack key = plugin.masterKeys().createMasterKey();
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(key);
        for (ItemStack item : overflow.values()) target.getWorld().dropItemNaturally(target.getLocation(), item);
        target.sendMessage(plugin.success("You received a FragStealers Master Key."));
        if (!sender.equals(target)) sender.sendMessage(plugin.success("Gave a Master Key to " + target.getName() + "."));
        plugin.getLogger().info(sender.getName() + " gave a FragStealers Master Key to " + target.getName() + ".");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission(TRUST_PERMISSION)) {
                options.add("trust");
                options.add("untrust");
                options.add("trusted");
            }
            if (sender.hasPermission(MasterKeyManager.GIVE_PERMISSION)) options.add("give");
            if (sender.hasPermission(RELOAD_PERMISSION)) options.add("reload");
            return matches(args[0], options);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))
            && sender.hasPermission(TRUST_PERMISSION)) {
            return matches(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("trust") && sender.hasPermission(TRUST_PERMISSION)) {
            return matches(args[2], List.of("access", "manage"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission(MasterKeyManager.GIVE_PERMISSION)) {
            return matches(args[1], List.of("masterkey"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("masterkey")
            && sender.hasPermission(MasterKeyManager.GIVE_PERMISSION)) {
            return matches(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        return List.of();
    }

    private List<String> matches(String input, List<String> values) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
