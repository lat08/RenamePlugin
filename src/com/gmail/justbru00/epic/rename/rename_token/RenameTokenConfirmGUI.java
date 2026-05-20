/**
 * @author Justin "JustBru00" Brubaker
 *
 * This is licensed under the MPL Version 2.0. See license info in LICENSE.txt
 */
package com.gmail.justbru00.epic.rename.rename_token;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.gmail.justbru00.epic.rename.main.v3.Main;
import com.gmail.justbru00.epic.rename.utils.v3.Messager;

/**
 * Confirm GUI shown when a rename token flow starts.
 * Layout, slots, materials, custom model data and lore are all configurable
 * via the {@code token_rename.gui.*} section in config.yml.
 */
public class RenameTokenConfirmGUI {

    private static final int DEFAULT_SIZE = 27;
    private static final int DEFAULT_SLOT_YES = 15;
    private static final int DEFAULT_SLOT_NO = 11;
    private static final int DEFAULT_SLOT_TARGET = 13;
    private static final int DEFAULT_SLOT_INFO = 4;

    public static class RenameTokenGuiHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    public static int getSize() {
        int size = Main.getInstance().getConfig().getInt("token_rename.gui.size", DEFAULT_SIZE);
        if (size <= 0 || size % 9 != 0 || size > 54) return DEFAULT_SIZE;
        return size;
    }

    public static int getSlotYes() {
        return clampSlot("token_rename.gui.confirm.slot", DEFAULT_SLOT_YES);
    }

    public static int getSlotNo() {
        return clampSlot("token_rename.gui.cancel.slot", DEFAULT_SLOT_NO);
    }

    public static List<Integer> getSlotsYes() {
        return readSlots("token_rename.gui.confirm", DEFAULT_SLOT_YES);
    }

    public static List<Integer> getSlotsNo() {
        return readSlots("token_rename.gui.cancel", DEFAULT_SLOT_NO);
    }

    public static boolean isYesSlot(int slot) {
        return getSlotsYes().contains(slot);
    }

    public static boolean isNoSlot(int slot) {
        return getSlotsNo().contains(slot);
    }

    /**
     * Reads slot list from {basePath}.slots (preferred) or falls back to
     * {basePath}.slot (single int) for backward compatibility.
     */
    private static List<Integer> readSlots(String basePath, int def) {
        int max = getSize() - 1;
        List<Integer> raw = Main.getInstance().getConfig().getIntegerList(basePath + ".slots");
        List<Integer> valid = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            for (Integer s : raw) {
                if (s != null && s >= 0 && s <= max) valid.add(s);
            }
        }
        if (!valid.isEmpty()) return valid;
        int single = clampSlot(basePath + ".slot", def);
        valid.add(single);
        return valid;
    }

    public static int getSlotTarget() {
        return clampSlot("token_rename.gui.target_slot", DEFAULT_SLOT_TARGET);
    }

    public static int getSlotInfo() {
        return clampSlot("token_rename.gui.info.slot", DEFAULT_SLOT_INFO);
    }

    private static int clampSlot(String path, int def) {
        int v = Main.getInstance().getConfig().getInt(path, def);
        int max = getSize() - 1;
        return (v < 0 || v > max) ? def : v;
    }

    public static boolean isOurGUI(Inventory inv) {
        if (inv == null) return false;
        return inv.getHolder() instanceof RenameTokenGuiHolder;
    }

    public static void open(Player player, ItemStack targetSnapshot) {
        if (player == null) return;

        String title = Messager.color(
                Main.getInstance().getConfig().getString("token_rename.gui.title", "Confirm Rename"));

        RenameTokenGuiHolder holder = new RenameTokenGuiHolder();
        int size = getSize();
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        ItemStack filler = buildItemFromConfig("token_rename.gui.filler", Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler);
        }

        if (targetSnapshot != null) {
            inv.setItem(getSlotTarget(), targetSnapshot.clone());
        }
        inv.setItem(getSlotInfo(), buildItemFromConfig("token_rename.gui.info", Material.PAPER));
        ItemStack confirmItem = buildItemFromConfig("token_rename.gui.confirm", Material.LIME_CONCRETE);
        for (Integer s : getSlotsYes()) inv.setItem(s, confirmItem);
        ItemStack cancelItem = buildItemFromConfig("token_rename.gui.cancel", Material.RED_CONCRETE);
        for (Integer s : getSlotsNo()) inv.setItem(s, cancelItem);

        player.openInventory(inv);
    }

    /**
     * Build an item from a config sub-block (material, custom_model_data,
     * display_name, lore). Falls back to {@code defaultMaterial} if material
     * is missing or unknown on the running server version.
     */
    private static ItemStack buildItemFromConfig(String basePath, Material defaultMaterial) {
        String matName = Main.getInstance().getConfig().getString(basePath + ".material");
        Material mat = matName == null ? null : Material.matchMaterial(matName);
        if (mat == null) mat = defaultMaterial != null ? defaultMaterial : Material.PAPER;

        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        String name = Main.getInstance().getConfig().getString(basePath + ".display_name", " ");
        meta.setDisplayName(Messager.color(name));

        List<String> rawLore = Main.getInstance().getConfig().getStringList(basePath + ".lore");
        if (rawLore != null && !rawLore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>(rawLore.size());
            for (String line : rawLore) {
                coloredLore.add(Messager.color(line));
            }
            meta.setLore(coloredLore);
        }

        int cmd = Main.getInstance().getConfig().getInt(basePath + ".custom_model_data", 0);
        if (cmd > 0) {
            try {
                meta.setCustomModelData(cmd);
            } catch (Throwable ignored) {
                // setCustomModelData not available on MC 1.13 - ignore.
            }
        }

        stack.setItemMeta(meta);
        return stack;
    }
}
