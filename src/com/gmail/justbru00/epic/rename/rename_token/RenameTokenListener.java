/**
 * @author Justin "JustBru00" Brubaker
 * 
 * This is licensed under the MPL Version 2.0. See license info in LICENSE.txt
 */
package com.gmail.justbru00.epic.rename.rename_token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.gmail.justbru00.epic.rename.enums.v3.EcoMessage;
import com.gmail.justbru00.epic.rename.enums.v3.EpicRenameCommands;
import com.gmail.justbru00.epic.rename.enums.v3.XpMessage;
import com.gmail.justbru00.epic.rename.main.v3.Main;
import com.gmail.justbru00.epic.rename.rename_token.RenameTokenSession.Phase;
import com.gmail.justbru00.epic.rename.utils.v3.Blacklists;
import com.gmail.justbru00.epic.rename.utils.v3.CharLimit;
import com.gmail.justbru00.epic.rename.utils.v3.Debug;
import com.gmail.justbru00.epic.rename.utils.v3.EconomyManager;
import com.gmail.justbru00.epic.rename.utils.v3.FormattingCodeCounter;
import com.gmail.justbru00.epic.rename.utils.v3.FormattingPermManager;
import com.gmail.justbru00.epic.rename.utils.v3.MaterialPermManager;
import com.gmail.justbru00.epic.rename.utils.v3.Messager;
import com.gmail.justbru00.epic.rename.utils.v3.RenameUtil;
import com.gmail.justbru00.epic.rename.utils.v3.WorldChecker;
import com.gmail.justbru00.epic.rename.utils.v3.XpCostManager;

/**
 * Handles all player-driven events related to the rename token feature.
 */
public class RenameTokenListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();

        // GUI clicks are routed and cancelled here.
        InventoryView view = e.getView();
        if (view != null && RenameTokenConfirmGUI.isOurGUI(view.getTopInventory())) {
            handleGuiClick(e, player);
            return;
        }

        // Block movement of the target slot during chat phases.
        RenameTokenSession session = RenameTokenSessionManager.get(player);
        if (session != null && (session.getPhase() == Phase.AWAITING_NAME
                || session.getPhase() == Phase.AWAITING_PREVIEW)) {
            Inventory clicked = e.getClickedInventory();
            if (clicked != null && clicked.equals(session.getInventory())
                    && e.getSlot() == session.getTargetSlot()) {
                e.setCancelled(true);
                return;
            }
            if (e.getAction() == InventoryAction.HOTBAR_SWAP
                    || e.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
                if (e.getSlot() == session.getTargetSlot()
                        && clicked != null && clicked.equals(session.getInventory())) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

        // Trigger: cursor holds a token, left-click a target item in own inv.
        if (!RenameToken.isFeatureEnabled()) return;
        if (RenameTokenSessionManager.has(player)) return;

        ItemStack cursor = e.getCursor();
        ItemStack target = e.getCurrentItem();

        if (!RenameToken.isToken(cursor)) return;
        if (target == null || target.getType() == Material.AIR) return;
        if (e.getClick() != ClickType.LEFT) return;

        Inventory clicked = e.getClickedInventory();
        if (clicked == null || !(clicked instanceof PlayerInventory)) return;
        if (!clicked.equals(player.getInventory())) return;

        if (RenameToken.isToken(target)) {
            e.setCancelled(true);
            Messager.msgPlayer(Main.getMsgFromConfig("token_rename.cannot_rename_token"), player);
            return;
        }

        e.setCancelled(true);

        // Take the token off the cursor immediately so the GUI opens with an
        // empty cursor - prevents client/server desync.
        ItemStack tokenItem = cursor.clone();
        player.setItemOnCursor(null);

        startSession(player, target.clone(), e.getSlot(), tokenItem);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();

        InventoryView view = e.getView();
        if (view != null && RenameTokenConfirmGUI.isOurGUI(view.getTopInventory())) {
            e.setCancelled(true);
            return;
        }

        // Block drag involving a token cursor.
        if (RenameToken.isToken(e.getOldCursor())) {
            e.setCancelled(true);
            return;
        }

        // While awaiting name/preview, block drags onto the target slot.
        RenameTokenSession s = RenameTokenSessionManager.get(player);
        if (s != null && (s.getPhase() == Phase.AWAITING_NAME
                || s.getPhase() == Phase.AWAITING_PREVIEW)) {
            if (e.getRawSlots() != null) {
                for (Integer raw : e.getRawSlots()) {
                    if (raw == null) continue;
                    int converted = e.getView().convertSlot(raw);
                    if (converted == s.getTargetSlot()) {
                        e.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Inventory close: cancel if GUI closed without clicking YES
    // ---------------------------------------------------------------------
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player player = (Player) e.getPlayer();
        if (!RenameTokenConfirmGUI.isOurGUI(e.getInventory())) return;

        RenameTokenSession session = RenameTokenSessionManager.get(player);
        if (session == null) return;

        if (session.getPhase() == Phase.AWAITING_GUI_CONFIRM) {
            playGuiSound(player, "cancel");
            cancelWithRefund(player, "token_rename.cancelled");
        }
    }

    // ---------------------------------------------------------------------
    // Async chat: name input + preview confirmation
    // ---------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        final Player player = e.getPlayer();
        final RenameTokenSession session = RenameTokenSessionManager.get(player);
        if (session == null) return;

        Phase phase = session.getPhase();
        if (phase != Phase.AWAITING_NAME && phase != Phase.AWAITING_PREVIEW) return;

        e.setCancelled(true);
        final String message = e.getMessage();

        Bukkit.getScheduler().runTask(Main.getInstance(), new Runnable() {
            @Override
            public void run() {
                RenameTokenSession current = RenameTokenSessionManager.get(player);
                if (current == null) return;

                if (current.getPhase() == Phase.AWAITING_NAME) {
                    handleNameInput(player, current, message);
                } else if (current.getPhase() == Phase.AWAITING_PREVIEW) {
                    handlePreviewInput(player, current, message);
                }
            }
        });
    }

    // ---------------------------------------------------------------------
    // Cleanup events
    // ---------------------------------------------------------------------
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        RenameTokenSession s = RenameTokenSessionManager.get(e.getPlayer());
        if (s != null) {
            cancelTimeoutTask(s);
            RenameToken.refundIfReserved(e.getPlayer(), s);
        }
        RenameTokenSessionManager.end(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        RenameTokenSession s = RenameTokenSessionManager.get(p);
        if (s != null) {
            cancelTimeoutTask(s);
            // Refunded token drops with the rest of the death pile.
            RenameToken.refundIfReserved(p, s);
            RenameTokenSessionManager.end(p);
        }
    }

    /**
     * Block item drops during a rename to protect the target.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player player = e.getPlayer();
        if (!isInRenameFlow(player)) return;
        e.setCancelled(true);
        Messager.msgPlayer(Main.getMsgFromConfig("token_rename.in_progress"), player);
    }

    /**
     * Block right-click during a rename so the target isn't used/placed.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        if (!isInRenameFlow(player)) return;
        e.setCancelled(true);
        Messager.msgPlayer(Main.getMsgFromConfig("token_rename.in_progress"), player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Player player = e.getPlayer();
        if (!isInRenameFlow(player)) return;
        e.setCancelled(true);
        Messager.msgPlayer(Main.getMsgFromConfig("token_rename.in_progress"), player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        Player player = e.getPlayer();
        if (!isInRenameFlow(player)) return;
        e.setCancelled(true);
        Messager.msgPlayer(Main.getMsgFromConfig("token_rename.in_progress"), player);
    }

    private boolean isInRenameFlow(Player player) {
        RenameTokenSession s = RenameTokenSessionManager.get(player);
        if (s == null) return false;
        Phase p = s.getPhase();
        return p == Phase.AWAITING_GUI_CONFIRM
                || p == Phase.AWAITING_NAME
                || p == Phase.AWAITING_PREVIEW;
    }

    /** Cancel session, refund any reserved token, optionally send a message. */
    private void cancelWithRefund(Player player, String msgKey) {
        RenameTokenSession s = RenameTokenSessionManager.get(player);
        cancelTimeoutTask(s);
        RenameToken.refundIfReserved(player, s);
        RenameTokenSessionManager.end(player);
        if (msgKey != null) {
            Messager.msgPlayer(Main.getMsgFromConfig(msgKey), player);
        }
    }

    /** Schedules an auto-cancel task for the session. No-op if timeout disabled. */
    private void scheduleTimeout(final Player player, final RenameTokenSession session) {
        int seconds = Main.getInstance().getConfig()
                .getInt("token_rename.session_timeout_seconds", 60);
        if (seconds <= 0) return;
        int taskId = Bukkit.getScheduler().runTaskLater(Main.getInstance(), new Runnable() {
            @Override
            public void run() {
                RenameTokenSession current = RenameTokenSessionManager.get(player);
                if (current == null || current != session) return;
                cancelWithRefund(player, "token_rename.session_timeout");
            }
        }, seconds * 20L).getTaskId();
        session.setTimeoutTaskId(taskId);
    }

    /** Cancels the timeout task associated with the session, if any. */
    private void cancelTimeoutTask(RenameTokenSession session) {
        if (session == null) return;
        int taskId = session.getTimeoutTaskId();
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            session.setTimeoutTaskId(-1);
        }
    }

    /** Plays a configured GUI sound (token_rename.gui.sounds.{key}). No-op if disabled. */
    private void playGuiSound(Player player, String key) {
        if (player == null) return;
        String base = "token_rename.gui.sounds." + key;
        if (!Main.getInstance().getConfig().getBoolean(base + ".enabled", false)) return;
        String name = Main.getInstance().getConfig().getString(base + ".name");
        if (name == null || name.isEmpty()) return;
        float volume = (float) Main.getInstance().getConfig().getDouble(base + ".volume", 1.0);
        float pitch = (float) Main.getInstance().getConfig().getDouble(base + ".pitch", 1.0);
        try {
            player.playSound(player.getLocation(), name, volume, pitch);
        } catch (Throwable ignored) {
            // Invalid sound name or unsupported overload - swallow silently.
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Begin a new session: validate preconditions and open the confirm GUI. */
    private void startSession(Player player, ItemStack targetSnapshot, int targetSlot, ItemStack tokenItem) {
        if (!WorldChecker.checkWorld(player)) {
            Messager.msgPlayer(Main.getMsgFromConfig("rename.disabled_world"), player);
            returnToken(player, tokenItem);
            return;
        }

        if (!player.hasPermission("epicrename.tokenrename.use")) {
            Messager.msgPlayer(Main.getMsgFromConfig("token_rename.no_permission"), player);
            returnToken(player, tokenItem);
            return;
        }

        Material targetType = targetSnapshot.getType();

        if (RenameToken.isTokenBlacklistedMaterial(targetType)) {
            Messager.msgPlayer(Main.getMsgFromConfig("token_rename.blacklist_target"), player);
            returnToken(player, tokenItem);
            return;
        }

        if (!Blacklists.checkMaterialBlacklist(targetType, player)) {
            Messager.msgPlayer(Main.getMsgFromConfig("rename.blacklisted_material_found"), player);
            returnToken(player, tokenItem);
            return;
        }

        if (!checkExistingNameOnSnapshot(targetSnapshot, player)) {
            Messager.msgPlayer(Main.getMsgFromConfig("rename.blacklisted_existing_name_found"), player);
            returnToken(player, tokenItem);
            return;
        }
        if (!checkExistingLoreOnSnapshot(targetSnapshot, player)) {
            Messager.msgPlayer(Main.getMsgFromConfig("rename.blacklisted_existing_lore_found"), player);
            returnToken(player, tokenItem);
            return;
        }

        if (!MaterialPermManager.checkPerms(EpicRenameCommands.RENAME, targetSnapshot, player)) {
            Messager.msgPlayer(Main.getMsgFromConfig("rename.no_permission_for_material"), player);
            returnToken(player, tokenItem);
            return;
        }

        RenameTokenSession session = new RenameTokenSession(
                Phase.AWAITING_GUI_CONFIRM,
                player.getInventory(),
                targetSlot,
                targetSnapshot
        );

        if (RenameToken.shouldConsumeOnUse()) {
            // Token was already taken from cursor - mark it reserved now.
            UUID tokenId = RenameToken.getTokenUUID(tokenItem);
            session.setTokenReserved(true);
            session.setReservedTokenUUID(tokenId);
            TokenAuditLogger.logConsume(player, tokenId, targetSnapshot);
        } else {
            // Not consuming - return token to inventory.
            returnToken(player, tokenItem);
        }

        RenameTokenSessionManager.start(player, session);
        scheduleTimeout(player, session);
        RenameTokenConfirmGUI.open(player, targetSnapshot);
    }

    /** Returns a token item to the player's inventory, dropping at feet if full. */
    private void returnToken(Player player, ItemStack token) {
        if (token == null || player == null) return;
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(token);
        if (!overflow.isEmpty() && player.getWorld() != null) {
            for (ItemStack drop : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    /** Handle clicks inside the confirm GUI. Cancels all clicks then routes YES/NO. */
    private void handleGuiClick(InventoryClickEvent e, Player player) {
        // Cancel every click while the GUI is open.
        e.setCancelled(true);

        InventoryView view = e.getView();
        if (view == null) return;

        Inventory top = view.getTopInventory();
        Inventory clicked = e.getClickedInventory();

        if (clicked == null || !clicked.equals(top)) {
            return;
        }

        int slot = e.getRawSlot();
        RenameTokenSession session = RenameTokenSessionManager.get(player);
        if (session == null) {
            player.closeInventory();
            return;
        }

        if (RenameTokenConfirmGUI.isYesSlot(slot)) {
            playGuiSound(player, "confirm");
            // Token was already taken from cursor at trigger time.
            session.setPhase(Phase.AWAITING_NAME);
            Messager.msgPlayer(Main.getMsgFromConfig("token_rename.enter_name"), player);
            Messager.msgPlayer(Main.getMsgFromConfig("token_rename.enter_name_hint"), player);
            player.setItemOnCursor(null);
            Bukkit.getScheduler().runTask(Main.getInstance(), new Runnable() {
                @Override
                public void run() {
                    player.setItemOnCursor(null);
                    InventoryView open = player.getOpenInventory();
                    if (open != null && RenameTokenConfirmGUI.isOurGUI(open.getTopInventory())) {
                        player.closeInventory();
                    }
                }
            });
            return;
        }

        if (RenameTokenConfirmGUI.isNoSlot(slot)) {
            playGuiSound(player, "cancel");
            cancelWithRefund(player, "token_rename.cancelled");
            player.setItemOnCursor(null);
            Bukkit.getScheduler().runTask(Main.getInstance(), new Runnable() {
                @Override
                public void run() {
                    player.setItemOnCursor(null);
                    player.closeInventory();
                }
            });
            return;
        }
    }

    /** Phase: AWAITING_NAME. Player typed a candidate name or '/cancel'. */
    private void handleNameInput(Player player, RenameTokenSession session, String message) {
        if (message == null) return;
        String trimmed = message.trim();

        if (trimmed.equalsIgnoreCase("cancel") || trimmed.equalsIgnoreCase("/cancel")) {
            cancelWithRefund(player, "token_rename.cancelled");
            return;
        }

        // Verify target slot still holds the snapshot item.
        if (!targetMatchesSnapshot(session)) {
            cancelWithRefund(player, "token_rename.target_changed");
            return;
        }

        String[] argsArray = trimmed.split(" ");
        boolean replaceUnderscores = Main.getInstance().getConfig().getBoolean("replace_underscores");
        if (replaceUnderscores) {
            for (int i = 0; i < argsArray.length; i++) argsArray[i] = argsArray[i].replace("_", " ");
        }
        String complete = replaceUnderscores ? trimmed.replace("_", " ") : trimmed;

        if (!Blacklists.checkTextBlacklist(argsArray, player)) {
            Messager.msgPlayer(Main.getMsgFromConfig("rename.blacklisted_word_found"), player);
            return;
        }
        if (!CharLimit.checkCharLimit(argsArray, player)) {
            String maxMsg = Main.getMsgFromConfig("rename_character_limit.name_too_long")
                    .replace("{char}", String.valueOf(CharLimit.getCharLimit()));
            Messager.msgPlayer(maxMsg, player);
            return;
        }
        if (!FormattingPermManager.checkPerms(EpicRenameCommands.RENAME, complete, player)) {
            // Message handled by FormattingPermManager
            return;
        }
        if (!FormattingCodeCounter.checkMinColorCodes(player, complete, EpicRenameCommands.RENAME, true)) {
            FormattingCodeCounter.sendMinNotReachedMsg(player, EpicRenameCommands.RENAME);
            return;
        }
        if (!FormattingCodeCounter.checkMaxColorCodes(player, complete, EpicRenameCommands.RENAME, true)) {
            FormattingCodeCounter.sendMaxReachedMsg(player, EpicRenameCommands.RENAME);
            return;
        }

        String preview = Messager.color(
                Main.getInstance().getConfig().getString("command_argument.prefixes.rename", "")
                + complete
                + Main.getInstance().getConfig().getString("command_argument.suffixes.rename", ""));

        session.setPendingNewName(complete);
        session.setPendingPreview(preview);
        session.setPhase(Phase.AWAITING_PREVIEW);

        String previewMsg = Main.getMsgFromConfig("token_rename.confirm_preview").replace("{preview}", preview);
        Messager.msgPlayer(previewMsg, player);
        Messager.msgPlayer(Main.getMsgFromConfig("token_rename.confirm_preview_hint"), player);
    }

    /** Phase: AWAITING_PREVIEW. Player must type 'yes' to apply. */
    private void handlePreviewInput(Player player, RenameTokenSession session, String message) {
        String trimmed = message == null ? "" : message.trim();
        if (!trimmed.equalsIgnoreCase("yes")) {
            cancelWithRefund(player, "token_rename.cancelled");
            return;
        }

        // Re-verify the target item hasn't changed.
        if (!targetMatchesSnapshot(session)) {
            cancelWithRefund(player, "token_rename.target_changed");
            return;
        }

        applyRename(player, session);
    }

    /** Final apply step: charge eco/xp, rename item, finalize token consumption. */
    private void applyRename(Player player, RenameTokenSession session) {
        ItemStack target = session.getInventory().getItem(session.getTargetSlot());
        if (target == null || target.getType() == Material.AIR) {
            cancelWithRefund(player, "token_rename.target_changed");
            return;
        }

        EcoMessage ecoStatus = EconomyManager.takeMoney(player, EpicRenameCommands.RENAME);
        if (ecoStatus == EcoMessage.TRANSACTION_ERROR) {
            cancelWithRefund(player, null);
            return;
        }
        XpMessage xpStatus = XpCostManager.takeXp(player, EpicRenameCommands.RENAME);
        if (xpStatus == XpMessage.TRANSACTION_ERROR) {
            cancelWithRefund(player, null);
            return;
        }

        String complete = Messager.color(
                Main.getInstance().getConfig().getString("command_argument.prefixes.rename", "")
                + session.getPendingNewName()
                + Main.getInstance().getConfig().getString("command_argument.suffixes.rename", ""));

        ItemStack renamed = RenameUtil.renameItemStack(player, complete, target);
        if (Main.getInstance().getConfig().getBoolean("token_rename.add_renamed_lore", true)) {
            renamed = applyRenamedLore(player, renamed);
        }
        session.getInventory().setItem(session.getTargetSlot(), renamed);

        boolean consumed = session.isTokenReserved();
        UUID consumedId = session.getReservedTokenUUID();
        session.setTokenReserved(false);

        Debug.send("[RenameTokenListener] Renamed item for " + player.getName()
                + " new='" + complete + "' consumed=" + consumed);
        TokenAuditLogger.logRenameDone(player, consumedId, complete);

        cancelTimeoutTask(session);
        RenameTokenSessionManager.end(player);
        Messager.msgPlayer(Main.getMsgFromConfig(consumed
                ? "token_rename.success"
                : "token_rename.success_no_consume"), player);
    }

    /** Adds or replaces the "renamed by" lore line on the item using PDC tracking. */
    private ItemStack applyRenamedLore(Player player, ItemStack item) {
        if (item == null) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String newLoreLine = Messager.color(
                Main.getMsgFromConfig("token_rename.renamed_lore")
                        .replace("{player}", player.getName()));

        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey loreKey = RenameToken.getRenamedLoreKey();
        String oldLoreLine = pdc.get(loreKey, PersistentDataType.STRING);
        if (oldLoreLine != null) {
            lore.remove(oldLoreLine);
        }

        lore.add(0, newLoreLine);
        meta.setLore(lore);
        pdc.set(loreKey, PersistentDataType.STRING, newLoreLine);
        item.setItemMeta(meta);
        return item;
    }

    /** True if the recorded slot still holds an item similar to the snapshot. */
    private boolean targetMatchesSnapshot(RenameTokenSession session) {
        ItemStack current = session.getInventory().getItem(session.getTargetSlot());
        if (current == null) return false;
        if (session.getTargetSnapshot() == null) return false;
        return current.isSimilar(session.getTargetSnapshot());
    }

    /** {@link Blacklists#checkExistingName(Player)} but for an arbitrary snapshot. */
    private boolean checkExistingNameOnSnapshot(ItemStack stack, Player player) {
        if (stack == null || !stack.hasItemMeta()) return true;
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return true;
        String itemName = org.bukkit.ChatColor.stripColor(meta.getDisplayName());
        if (itemName == null) return true;
        for (String blacklistedString : Main.getInstance().getConfig().getStringList("blacklists.existingname")) {
            if (blacklistedString == null) continue;
            String stripped = org.bukkit.ChatColor.stripColor(Messager.color(blacklistedString));
            if (itemName.toLowerCase().contains(stripped.toLowerCase())) {
                if (player.hasPermission("epicrename.bypass.existingname")) {
                    if (!Main.getBooleanFromConfig("disable_bypass_messages")) {
                        Messager.msgPlayer(Main.getMsgFromConfig("blacklists.existingname.bypass"), player);
                    }
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    /** {@link Blacklists#checkExistingLore(Player)} but for an arbitrary snapshot. */
    private boolean checkExistingLoreOnSnapshot(ItemStack stack, Player player) {
        if (stack == null || !stack.hasItemMeta()) return true;
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta == null) return true;
        List<String> loreLines = meta.getLore();
        if (loreLines == null) return true;
        for (String loreLine : loreLines) {
            String strippedLine = org.bukkit.ChatColor.stripColor(loreLine);
            for (String blacklistedString : Main.getInstance().getConfig().getStringList("blacklists.existingloreline")) {
                if (blacklistedString == null) continue;
                String stripped = org.bukkit.ChatColor.stripColor(Messager.color(blacklistedString));
                if (strippedLine != null && strippedLine.toLowerCase().contains(stripped.toLowerCase())) {
                    if (player.hasPermission("epicrename.bypass.existinglore")) {
                        if (!Main.getBooleanFromConfig("disable_bypass_messages")) {
                            Messager.msgPlayer(Main.getMsgFromConfig("blacklists.existinglore.bypass"), player);
                        }
                        return true;
                    }
                    return false;
                }
            }
        }
        return true;
    }
}
