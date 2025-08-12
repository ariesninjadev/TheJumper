package com.ariesninja.theJumper.command;

import com.ariesninja.theJumper.TheJumper;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

public final class JumperHelpCommand implements CommandExecutor {
    private final TheJumper plugin;

    public JumperHelpCommand(TheJumper plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player player = (Player) sender;

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return true;
        meta.setTitle("The Jumper Guide");
        meta.setAuthor("TheJumper");

        String page1 = ChatColor.GOLD + "Welcome to " + ChatColor.BOLD + "The Jumper!\n\n" + ChatColor.RESET +
                "- Reach the highlighted blocks to gain points.\n" +
                "- Complete 20 jumps to rank up.\n" +
                "- If you fall once, you restart your level.\n" +
                "- Fall again on the same level, completely restart.";

        String page2 = ChatColor.BLUE + "Controls & UI\n\n" + ChatColor.RESET +
                "- Actionbar shows Level, Progress, Score, and Time.\n" +
                "- Scoreboard shows current high scores\n";

        String page3 = ChatColor.RED + "To get started, run " + ChatColor.AQUA + "/jumper join\n\n" + ChatColor.RESET +
                "Leave anytime with " + ChatColor.AQUA + "/jumper leave";

        meta.addPage(page1, page2, page3);
        book.setItemMeta(meta);

        player.openBook(book);
        return true;
    }
}


