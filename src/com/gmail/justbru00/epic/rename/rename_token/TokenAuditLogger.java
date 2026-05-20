/**
 * @author Justin "JustBru00" Brubaker
 *
 * This is licensed under the MPL Version 2.0. See license info in LICENSE.txt
 */
package com.gmail.justbru00.epic.rename.rename_token;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.gmail.justbru00.epic.rename.main.v3.Main;
import com.gmail.justbru00.epic.rename.utils.v3.Debug;
import com.gmail.justbru00.epic.rename.utils.v3.Messager;

/**
 * Append-only audit log for Rename Token lifecycle events.
 * Writes to console + plugins/EpicRename/token_audit.log.
 */
public final class TokenAuditLogger {

    private static final String FILE_NAME = "token_audit.log";
    private static final SimpleDateFormat TS_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private TokenAuditLogger() {}

    public static void logGive(CommandSender giver, Player target, UUID id, int amount) {
        String giverName = giver == null ? "?" : giver.getName();
        String playerName = target == null ? "?" : target.getName();
        write(String.format("GIVE     player=%s  giver=%s  uuid=%s  amount=%d",
                playerName, giverName, str(id), amount));
    }

    public static void logConsume(Player player, UUID id, ItemStack target) {
        String playerName = player == null ? "?" : player.getName();
        Material mat = target == null ? null : target.getType();
        write(String.format("CONSUME  player=%s  uuid=%s  target=%s",
                playerName, str(id), mat == null ? "?" : mat.toString()));
    }

    public static void logRefund(Player player, UUID oldId, UUID newId) {
        String playerName = player == null ? "?" : player.getName();
        write(String.format("REFUND   player=%s  oldUuid=%s  newUuid=%s",
                playerName, str(oldId), str(newId)));
    }

    public static void logRenameDone(Player player, UUID id, String newName) {
        String playerName = player == null ? "?" : player.getName();
        String safe = newName == null ? "" : newName.replace("\n", " ").replace("\r", " ");
        write(String.format("RENAME_DONE player=%s  uuid=%s  newName='%s'",
                playerName, str(id), safe));
    }

    private static String str(UUID id) {
        return id == null ? "-" : id.toString();
    }

    private static void write(String body) {
        String full = "[" + TS_FORMAT.format(new Date()) + "] " + body;

        try {
            Messager.msgConsole("&7[TokenAudit] &f" + full);
        } catch (Throwable ignored) {
        }

        try {
            File folder = Main.getInstance().getDataFolder();
            if (folder != null && !folder.exists()) {
                folder.mkdirs();
            }
            FileWriter fw = new FileWriter(new File(folder, FILE_NAME), true);
            try {
                fw.write(full);
                fw.write(System.lineSeparator());
                fw.flush();
            } finally {
                fw.close();
            }
        } catch (IOException ex) {
            Debug.send("[TokenAuditLogger] Failed to write audit line: " + ex.getMessage());
        } catch (Throwable t) {
            Debug.send("[TokenAuditLogger] Unexpected error: " + t.getMessage());
        }
    }
}
