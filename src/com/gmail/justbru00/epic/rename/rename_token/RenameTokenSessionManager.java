/**
 * @author Justin "JustBru00" Brubaker
 * 
 * This is licensed under the MPL Version 2.0. See license info in LICENSE.txt
 */
package com.gmail.justbru00.epic.rename.rename_token;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.gmail.justbru00.epic.rename.main.v3.Main;
import com.gmail.justbru00.epic.rename.utils.v3.Messager;

/**
 * Tracks active rename token sessions keyed by player UUID.
 * Thread-safe so the async chat listener can read sessions safely.
 */
public class RenameTokenSessionManager {

    private static final Map<UUID, RenameTokenSession> SESSIONS = new ConcurrentHashMap<UUID, RenameTokenSession>();

    public static void start(Player player, RenameTokenSession session) {
        SESSIONS.put(player.getUniqueId(), session);
    }

    public static RenameTokenSession get(Player player) {
        if (player == null) return null;
        return SESSIONS.get(player.getUniqueId());
    }

    public static RenameTokenSession get(UUID uuid) {
        if (uuid == null) return null;
        return SESSIONS.get(uuid);
    }

    public static boolean has(Player player) {
        return player != null && SESSIONS.containsKey(player.getUniqueId());
    }

    public static void end(Player player) {
        if (player == null) return;
        SESSIONS.remove(player.getUniqueId());
    }

    public static void end(UUID uuid) {
        if (uuid == null) return;
        SESSIONS.remove(uuid);
    }

    /**
     * Cancels every active session, refunds reserved tokens, and notifies
     * online players.
     * @param reasonMsgPath messages.yml path. May be null.
     */
    public static void cancelAll(String reasonMsgPath) {
        Iterator<Map.Entry<UUID, RenameTokenSession>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, RenameTokenSession> entry = it.next();
            UUID uuid = entry.getKey();
            RenameTokenSession session = entry.getValue();
            it.remove();
            // Cancel scheduled timeout task before processing.
            int taskId = session.getTimeoutTaskId();
            if (taskId != -1) {
                try {
                    Bukkit.getScheduler().cancelTask(taskId);
                } catch (Throwable ignored) {
                }
            }
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                try {
                    RenameToken.refundIfReserved(p, session);
                } catch (Throwable ignored) {
                }
            }
            if (reasonMsgPath != null && p != null && p.isOnline()) {
                Messager.msgPlayer(Main.getMsgFromConfig(reasonMsgPath), p);
            }
        }
    }
}
