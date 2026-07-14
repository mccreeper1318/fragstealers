package me.pinnacle.fragstealers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FragStealersCommand implements CommandExecutor, TabCompleter {
    private final FragStealers plugin;
    private final MasterKeyManager masterKeyManager;

    public FragStealersCommand(FragStealers plugin, MasterKeyManager masterKeyManager) {
        this.plugin = plugin;
        this.masterKeyManager = masterKeyManager;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (!sender.hasPermission(MasterKeyManager.GIVE_PERMISSION)) {
            sender.sendMessage(error(plugin.message("no-command-permission")));
            return true;
        }

        if (args.length < 2
            || !args[0].equalsIgnoreCase("give")
            || !args[1].equalsIgnoreCase("masterkey")
            || args.length > 3) {
            sender.sendMessage(error(plugin.message("master-key-usage")));
            return true;
        }

        Player target;
        if (args.length == 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(error(plugin.message("player-not-found").replace("{player}", args[2])));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(error(plugin.message("master-key-console-target")));
            return true;
        }

        ItemStack masterKey = masterKeyManager.createMasterKey();
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(masterKey);
        for (ItemStack item : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), item);
        }

        String targetMessage = plugin.message("master-key-received");
        target.sendMessage(success(targetMessage));

        if (!sender.equals(target)) {
            sender.sendMessage(success(plugin.message("master-key-given")
                .replace("{player}", target.getName())));
        }

        plugin.getLogger().info(sender.getName() + " gave a FragStealers Master Key to " + target.getName() + ".");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (!sender.hasPermission(MasterKeyManager.GIVE_PERMISSION)) {
            return List.of();
        }

        if (args.length == 1) {
            return matches(args[0], List.of("give"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return matches(args[1], List.of("masterkey"));
        }

        if (args.length == 3
            && args[0].equalsIgnoreCase("give")
            && args[1].equalsIgnoreCase("masterkey")) {
            return matches(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }

        return List.of();
    }

    private List<String> matches(String input, List<String> values) {
        String lowered = input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lowered)) {
                matches.add(value);
            }
        }
        return matches;
    }

    private Component success(String message) {
        return Component.text("FragStealers: ", NamedTextColor.GOLD)
            .append(Component.text(message, NamedTextColor.GREEN));
    }

    private Component error(String message) {
        return Component.text("FragStealers: ", NamedTextColor.GOLD)
            .append(Component.text(message, NamedTextColor.RED));
    }
}
