package br.duplas;

import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Listeners implements Listener {

    private static final Set<InventoryType> CONTAINERS = EnumSet.of(
            InventoryType.CHEST, InventoryType.BARREL, InventoryType.SHULKER_BOX,
            InventoryType.DISPENSER, InventoryType.DROPPER, InventoryType.HOPPER,
            InventoryType.FURNACE, InventoryType.BLAST_FURNACE, InventoryType.SMOKER,
            InventoryType.BREWING);

    private final DuplasPlugin pl;

    public Listeners(DuplasPlugin pl) {
        this.pl = pl;
    }

    /** Dono da chave (se o item for uma chave entregue pelo plugin). */
    private UUID keyOwnerOf(ItemStack st) {
        if (st == null || st.getType().isAir() || !st.hasItemMeta()) return null;
        String s = st.getItemMeta().getPersistentDataContainer().get(pl.keyOwner, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------- entrada / saida ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        pl.applyLook(p);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            Dupla d = pl.duplaOf(p.getUniqueId());
            if (d == null || d.alerts.isEmpty()) return;
            int max = pl.getConfig().getInt("alertas-ao-entrar", 10);
            List<Alert> list = d.alerts.subList(Math.max(0, d.alerts.size() - max), d.alerts.size());
            p.sendMessage("§8§m                                        ");
            p.sendMessage("§cSua dupla sofreu §f" + d.alerts.size() + " §cinvasão(ões). Últimas " + list.size() + ":");
            for (Alert a : list) for (String line : a.lines()) p.sendMessage(line);
            p.sendMessage("§8§m                                        ");
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pl.tracker.end(e.getPlayer().getUniqueId());
    }

    // ---------- registro de acoes dentro da estrutura ----------

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!CONTAINERS.contains(e.getInventory().getType())) return;
        pl.tracker.onOpen(p, e.getInventory().getContents());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!CONTAINERS.contains(e.getInventory().getType())) return;
        pl.tracker.onClose(p, e.getInventory().getContents());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        pl.tracker.onBreak(e.getPlayer(), e.getBlock().getType());
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ItemStack st = e.getItem().getItemStack();

        UUID owner = keyOwnerOf(st);
        if (owner != null && !owner.equals(p.getUniqueId())
                && pl.getConfig().getBoolean("chave.renegado-se-roubada", true)) {
            Dupla od = pl.duplaOf(owner);
            boolean parceiro = od != null && od.members.contains(p.getUniqueId());
            if (!parceiro) pl.setRenegado(owner, true, "teve a chave roubada por " + p.getName());
        }
        pl.tracker.onPickup(p, st);
    }

    // ---------- perda da chave ----------

    @EventHandler
    public void onRemove(EntityRemoveEvent e) {
        if (!(e.getEntity() instanceof Item item)) return;
        String c = e.getCause().name();
        if (!(c.equals("DEATH") || c.equals("DESPAWN") || c.equals("OUT_OF_WORLD") || c.equals("EXPLODE"))) return;
        UUID owner = keyOwnerOf(item.getItemStack());
        if (owner == null) return;
        if (pl.getConfig().getBoolean("chave.renegado-se-destruida", true))
            pl.setRenegado(owner, true, "a chave foi destruída");
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        UUID owner = keyOwnerOf(e.getItemDrop().getItemStack());
        if (owner == null || !owner.equals(e.getPlayer().getUniqueId())) return;
        if (pl.getConfig().getBoolean("chave.renegado-se-dropada", false))
            pl.setRenegado(owner, true, "dropou a chave");
    }
}
