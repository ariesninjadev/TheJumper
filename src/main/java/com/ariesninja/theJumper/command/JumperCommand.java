package com.ariesninja.theJumper.command;

import com.ariesninja.theJumper.TheJumper;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class JumperCommand implements CommandExecutor {

    private final TheJumper plugin;

    public JumperCommand(TheJumper plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "start":
                if (!sender.hasPermission("thejumper.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                plugin.getGameManager().startGame();
                sender.sendMessage(ChatColor.GREEN + "Event started.");
                return true;
            case "stop":
                if (!sender.hasPermission("thejumper.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                plugin.getGameManager().stopGame();
                sender.sendMessage(ChatColor.YELLOW + "Event stopped.");
                return true;
            case "join":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                plugin.getGameManager().join((Player) sender);
                return true;
            case "leave":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                plugin.getGameManager().leave((Player) sender);
                return true;
            case "setdiff":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " setdiff <I-IX>");
                    return true;
                }
                com.ariesninja.theJumper.library.Difficulty d = com.ariesninja.theJumper.library.Difficulty.fromId(args[1]);
                if (d == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown difficulty. Use I..IX");
                    return true;
                }
                plugin.getGameManager().setPlayerDifficulty((Player) sender, d);
                return true;
            case "reload":
                if (!sender.hasPermission("thejumper.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                plugin.getGameConfig().reload();
                sender.sendMessage(ChatColor.GREEN + "The Jumper configs reloaded.");
                return true;
            default:
                sendUsage(sender, label);
                return true;
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.AQUA + "TheJumper commands:");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " start" + ChatColor.DARK_GRAY + " - Start the event");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " stop" + ChatColor.DARK_GRAY + " - Stop the event");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " join" + ChatColor.DARK_GRAY + " - Join the event");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " leave" + ChatColor.DARK_GRAY + " - Leave the event");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " reload" + ChatColor.DARK_GRAY + " - Reload configs");
    }
}


