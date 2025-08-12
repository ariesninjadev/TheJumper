package com.ariesninja.theJumper.command;

import com.ariesninja.theJumper.TheJumper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class JumperCommand implements CommandExecutor, org.bukkit.command.TabCompleter {

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
                sender.sendMessage(ChatColor.DARK_AQUA + "[ADMIN] " + ChatColor.GREEN + "Event started.");
                return true;
            case "stop":
                if (!sender.hasPermission("thejumper.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                plugin.getGameManager().stopGame();
                sender.sendMessage(ChatColor.DARK_AQUA + "[ADMIN] " + ChatColor.YELLOW + "Event stopped.");
                return true;
            case "reset":
                if (!sender.hasPermission("thejumper.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                plugin.getGameManager().resetEventAreaAsync(sender);
                sender.sendMessage(ChatColor.DARK_AQUA + "[ADMIN] " + ChatColor.GREEN + "Reset queued: clearing event area.");
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
                if (!sender.hasPermission("thejumper.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " setdiff <I-IX>");
                    return true;
                }
                com.ariesninja.theJumper.library.Difficulty d = com.ariesninja.theJumper.library.Difficulty.fromId(args[1]);
                if (d == null) {
                    sender.sendMessage(ChatColor.DARK_AQUA + "[ADMIN] " + ChatColor.RED + "Unknown difficulty. Use I..IX");
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
                sender.sendMessage(ChatColor.DARK_AQUA + "[ADMIN] " + ChatColor.GREEN + "The Jumper configs reloaded.");
                return true;
            case "joinall":
                if (!sender.hasPermission("thejumper.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                org.bukkit.Bukkit.getOnlinePlayers().forEach(plugin.getGameManager()::join);
                sender.sendMessage(ChatColor.DARK_AQUA + "[ADMIN] " + ChatColor.GREEN + "Sent all online players to the event.");
                return true;
            case "spec":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " spec <player>");
                    return true;
                }
                org.bukkit.entity.Player specTarget = org.bukkit.Bukkit.getPlayerExact(args[1]);
                if (specTarget == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
                com.ariesninja.theJumper.TheJumper.getInstance().getGameManager(); // ensure init
                ((Player) sender).setGameMode(org.bukkit.GameMode.SPECTATOR);
                com.ariesninja.theJumper.TheJumper.getInstance().getServer().getScheduler().runTask(com.ariesninja.theJumper.TheJumper.getInstance(), () -> {
                    com.ariesninja.theJumper.TheJumper.getInstance().getSpectateManager().addSpectator((Player) sender, specTarget);
                });
                sender.sendMessage(ChatColor.AQUA + "Spectating " + specTarget.getName() + ". Use /unspec to stop.");
                return true;
            case "unspec":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                com.ariesninja.theJumper.TheJumper.getInstance().getSpectateManager().removeSpectator((Player) sender);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + sender.getName());
                ((Player) sender).setGameMode(org.bukkit.GameMode.ADVENTURE);
                sender.sendMessage(ChatColor.YELLOW + "Stopped spectating.");
                return true;
            case "clearscore":
                if (!sender.hasPermission("thejumper.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " clearscore <player>");
                    return true;
                }
                org.bukkit.OfflinePlayer offTarget = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
                if (offTarget == null || offTarget.getUniqueId() == null) {
                    sender.sendMessage(ChatColor.DARK_AQUA + "[ADMIN] " + ChatColor.RED + "Player not found.");
                    return true;
                }
                com.ariesninja.theJumper.game.PlayerSession sess = plugin.getGameManager().getSessionManager().getSession(offTarget.getUniqueId());
                if (sess == null) {
                    sender.sendMessage(ChatColor.DARK_AQUA + "[ADMIN] " + ChatColor.RED + "No session found for that player.");
                    return true;
                }
                sess.resetAllScore();
                sess.resetBestScore();
                sender.sendMessage(ChatColor.DARK_AQUA + "[ADMIN] " + ChatColor.GREEN + "Cleared score for " + (offTarget.getName() != null ? offTarget.getName() : offTarget.getUniqueId()));
                return true;
            default:
                sendUsage(sender, label);
                return true;
        }
    }

    @Override
    public java.util.List<String> onTabComplete(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        java.util.List<String> subs = java.util.Arrays.asList("start","stop","reset","join","leave","reload","setdiff","joinall","clearscore","spec","unspec");
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            java.util.List<String> out = new java.util.ArrayList<>();
            for (String s : subs) if (s.startsWith(p)) out.add(s);
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setdiff")) {
            return java.util.Arrays.asList("I","II","III","IV","V","VI","VII","VIII","IX");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spec")) {
            java.util.List<String> names = new java.util.ArrayList<>();
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        return java.util.Collections.emptyList();
    }
    private void sendUsage(CommandSender sender, String label) {
        // For players only
        sender.sendMessage(ChatColor.AQUA + "Jumper commands:");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " join" + ChatColor.DARK_GRAY + " - Join the event");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " leave" + ChatColor.DARK_GRAY + " - Leave the event");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " spec " + ChatColor.RED + "[player]" + ChatColor.DARK_GRAY + " - Spectate a player");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " unspec" + ChatColor.DARK_GRAY + " - Stop spectating");
    }
}


