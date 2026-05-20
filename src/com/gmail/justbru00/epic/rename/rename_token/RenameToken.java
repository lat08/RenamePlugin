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

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.gmail.justbru00.epic.rename.main.v3.Main;
import com.gmail.justbru00.epic.rename.utils.v3.Debug;
import com.gmail.justbru00.epic.rename.utils.v3.Messager;

/**
 * Factory + identifier for the Rename Token item.
 * Uses PersistentDataContainer so the token cannot be faked by anvil-renaming.
 */
public class RenameToken {

    public static final String PDC_KEY_NAME = "rename_token";
    public static final String PDC_KEY_UUID = "token_uuid";
    public static final String PDC_KEY_RENAMED_LORE = "renamed_lore";

    private static NamespacedKey key;
    private static NamespacedKey uuidKey;
    private static NamespacedKey renamedLoreKey;

    public static NamespacedKey getKey() {
        if (key == null) {
            key = new NamespacedKey(Main.getInstance(), PDC_KEY_NAME);
        }
        return key;
    }

    public static NamespacedKey getUUIDKey() {
        if (uuidKey == null) {
            uuidKey = new NamespacedKey(Main.getInstance(), PDC_KEY_UUID);
        }
        return uuidKey;
    }

    public static NamespacedKey getRenamedLoreKey() {
        if (renamedLoreKey == null) {
            renamedLoreKey = new NamespacedKey(Main.getInstance(), PDC_KEY_RENAMED_LORE);
        }
        return renamedLoreKey;
    }

    /**
     * Builds a Rename Token ItemStack. Each call generates a fresh per-token
     * UUID, so tokens built in separate calls do not stack together.
     */
    public static ItemStack build(int amount) {
        String matName = Main.getInstance().getConfig().getString("token_rename.item.material", "NAME_TAG");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) {
            Debug.send("[RenameToken] Material '" + matName + "' from token_rename.item.material is invalid. Falling back to NAME_TAG.");
            mat = Material.NAME_TAG;
        }

        ItemStack token = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = token.getItemMeta();
        if (meta == null) {
            return token;
        }

        String displayName = Main.getInstance().getConfig().getString("token_rename.item.display_name", "&6&lRename Token");
        meta.setDisplayName(Messager.color(displayName));

        List<String> rawLore = Main.getInstance().getConfig().getStringList("token_rename.item.lore");
        if (rawLore != null && !rawLore.isEmpty()) {
            List<String> coloredLore = new ArrayList<String>(rawLore.size());
            for (String line : rawLore) {
                coloredLore.add(Messager.color(line));
            }
            meta.setLore(coloredLore);
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(getKey(), PersistentDataType.BYTE, (byte) 1);
        pdc.set(getUUIDKey(), PersistentDataType.STRING, UUID.randomUUID().toString());

        token.setItemMeta(meta);
        return token;
    }

    /**
     * Returns the per-token UUID stored in the PDC, or null for non-tokens or
     * legacy tokens without a UUID.
     */
    public static UUID getTokenUUID(ItemStack stack) {
        if (!isToken(stack)) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        String s = meta.getPersistentDataContainer().get(getUUIDKey(), PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Refunds a fresh token if the session has one reserved. Safe to call
     * multiple times - the reservation flag is cleared on first call.
     */
    public static void refundIfReserved(Player player, RenameTokenSession session) {
        if (session == null || !session.isTokenReserved()) return;
        if (player == null) return;
        session.setTokenReserved(false);
        UUID old = session.getReservedTokenUUID();

        ItemStack token = build(1);
        TokenAuditLogger.logRefund(player, old, getTokenUUID(token));

        if (player.isOnline()) {
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(token);
            if (!overflow.isEmpty() && player.getWorld() != null) {
                for (ItemStack drop : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }
    }

    public static boolean isToken(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        if (!stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(getKey(), PersistentDataType.BYTE);
    }

    public static boolean isFeatureEnabled() {
        return Main.getInstance().getConfig().getBoolean("token_rename.enabled", true);
    }

    public static boolean shouldConsumeOnUse() {
        return Main.getInstance().getConfig().getBoolean("token_rename.consume_on_use", true);
    }

    public static boolean isTokenBlacklistedMaterial(Material m) {
        if (m == null) return true;
        List<String> blacklist = Main.getInstance().getConfig().getStringList("token_rename.blacklist_materials");
        if (blacklist == null || blacklist.isEmpty()) return false;
        for (String entry : blacklist) {
            if (entry == null) continue;
            if (m.toString().equalsIgnoreCase(entry)) return true;
        }
        return false;
    }
}
