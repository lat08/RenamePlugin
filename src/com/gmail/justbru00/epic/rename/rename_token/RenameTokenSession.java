/**
 * @author Justin "JustBru00" Brubaker
 * 
 * This is licensed under the MPL Version 2.0. See license info in LICENSE.txt
 */
package com.gmail.justbru00.epic.rename.rename_token;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Per-player state for the rename token interaction.
 */
public class RenameTokenSession {

    public enum Phase {
        AWAITING_GUI_CONFIRM,
        AWAITING_NAME,
        AWAITING_PREVIEW
    }

    private Phase phase;
    private final Inventory inventory;
    private final int targetSlot;
    private final ItemStack targetSnapshot;
    private String pendingNewName;
    private String pendingPreview;
    private boolean tokenReserved = false;
    private java.util.UUID reservedTokenUUID;
    private int timeoutTaskId = -1;

    public RenameTokenSession(Phase phase, Inventory inventory, int targetSlot, ItemStack targetSnapshot) {
        this.phase = phase;
        this.inventory = inventory;
        this.targetSlot = targetSlot;
        this.targetSnapshot = targetSnapshot;
    }

    public Phase getPhase() { return phase; }
    public void setPhase(Phase phase) { this.phase = phase; }

    public Inventory getInventory() { return inventory; }
    public int getTargetSlot() { return targetSlot; }
    public ItemStack getTargetSnapshot() { return targetSnapshot; }

    public String getPendingNewName() { return pendingNewName; }
    public void setPendingNewName(String pendingNewName) { this.pendingNewName = pendingNewName; }

    public String getPendingPreview() { return pendingPreview; }
    public void setPendingPreview(String pendingPreview) { this.pendingPreview = pendingPreview; }

    public boolean isTokenReserved() { return tokenReserved; }
    public void setTokenReserved(boolean tokenReserved) { this.tokenReserved = tokenReserved; }

    public java.util.UUID getReservedTokenUUID() { return reservedTokenUUID; }
    public void setReservedTokenUUID(java.util.UUID reservedTokenUUID) { this.reservedTokenUUID = reservedTokenUUID; }

    public int getTimeoutTaskId() { return timeoutTaskId; }
    public void setTimeoutTaskId(int timeoutTaskId) { this.timeoutTaskId = timeoutTaskId; }
}
