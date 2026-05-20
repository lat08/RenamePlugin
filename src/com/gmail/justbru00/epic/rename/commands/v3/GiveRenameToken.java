/**
 * @author Justin "JustBru00" Brubaker
 * 
 * This is licensed under the MPL Version 2.0. See license info in LICENSE.txt
 */
package com.gmail.justbru00.epic.rename.commands.v3;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.gmail.justbru00.epic.rename.main.v3.Main;
import com.gmail.justbru00.epic.rename.rename_token.RenameToken;
import com.gmail.justbru00.epic.rename.utils.v3.Messager;

/**
 * /giverenametoken [player] [amount]
 * 
 * If no arguments are given, the sender (must be a player) gets one token.
 * If only [amount] is given, the sender gets that many tokens.
 * If both are given, the named player gets [amount] tokens.
 */
public class GiveRenameToken implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("giverenametoken")) {
            return false;
        }

        if (!sender.hasPermission("epicrename.giverenametoken")) {
            Messager.msgSenderWithConfigMsg("token_rename.no_token_command_perm", sender);
            return true;
        }

        Player target;
        int amount;

        try {
            if (args.length == 0) {
                if (!(sender instanceof Player)) {
                    Messager.msgSenderWithConfigMsg("rename.wrong_sender", sender);
                    return true;
                }
                target = (Player) sender;
                amount = 1;
            } else if (args.length == 1) {
                // Could be either an amount (when sender is a player) or a player name (give 1 token).
                if (sender instanceof Player) {
                    Integer parsed = tryParseInt(args[0]);
                    if (parsed != null) {
                        target = (Player) sender;
                        amount = parsed;
                    } else {
                        target = Bukkit.getPlayerExact(args[0]);
                        amount = 1;
                    }
                } else {
                    target = Bukkit.getPlayerExact(args[0]);
                    amount = 1;
                }
            } else {
                target = Bukkit.getPlayerExact(args[0]);
                Integer parsed = tryParseInt(args[1]);
                if (parsed == null) {
                    Messager.msgSenderWithConfigMsg("token_rename.invalid_amount", sender);
                    return true;
                }
                amount = parsed;
            }
        } catch (Exception ex) {
            Messager.msgSenderWithConfigMsg("token_rename.invalid_amount", sender);
            return true;
        }

        if (target == null) {
            String msg = Main.getMsgFromConfig("token_rename.player_not_found")
                    .replace("{player}", args.length > 0 ? args[0] : "");
            Messager.msgSender(msg, sender);
            return true;
        }

        if (amount <= 0 || amount > 64 * 36) {
            Messager.msgSenderWithConfigMsg("token_rename.invalid_amount", sender);
            return true;
        }

        // Build tokens one at a time so each carries a unique UUID for audit
        // logging. As a side-effect, tokens with different UUIDs no longer
        // stack together in the inventory - acceptable for a rare item.
        int remaining = amount;
        while (remaining > 0) {
            ItemStack one = RenameToken.build(1);
            java.util.UUID id = RenameToken.getTokenUUID(one);
            java.util.HashMap<Integer, ItemStack> overflow = target.getInventory().addItem(one);
            if (!overflow.isEmpty()) {
                for (ItemStack drop : overflow.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), drop);
                }
                Messager.msgPlayer(Main.getMsgFromConfig("token_rename.inventory_full"), target);
            }
            com.gmail.justbru00.epic.rename.rename_token.TokenAuditLogger
                    .logGive(sender, target, id, 1);
            remaining--;
        }

        // Feedback
        if (sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId())) {
            Messager.msgSender(Main.getMsgFromConfig("token_rename.given_self")
                    .replace("{amount}", String.valueOf(amount)), sender);
        } else {
            String msg = Main.getMsgFromConfig("token_rename.given_other")
                    .replace("{player}", target.getName())
                    .replace("{amount}", String.valueOf(amount));
            Messager.msgSender(msg, sender);
        }
        return true;
    }

    private static Integer tryParseInt(String s) {
        if (s == null) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
