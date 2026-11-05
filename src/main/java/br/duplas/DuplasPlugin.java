package br.duplas;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.util.*;

public final class DuplasPlugin extends JavaPlugin {

    public final Map<String, Dupla> duplas = new LinkedHashMap<>();
    public final Set<UUID> renegados = new HashSet<>();
    public ItemStack keyTemplate;
    public NamespacedKey keyOwner;
    public Tracker tracker;
    private boolean dirty;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            Alert.zone = ZoneId.of(getConfig().getString("fuso", "America/Sao_Paulo"));
        } catch (Exception ignored) {}
        keyOwner = new NamespacedKey(this, "key_owner");
        loadData();
        tracker = new Tracker(this);

        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        DuplaCommand cmd = new DuplaCommand(this);
        getCommand("dupla").setExecutor(cmd);
        getCommand("dupla").setTabCompleter(cmd);
        getCommand("renegado").setExecutor(cmd);
        getCommand("renegado").setTabCompleter(cmd);

        getServer().getScheduler().runTaskTimer(this, tracker::tick, 40L, 10L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (dirty) saveData();
        }, 600L, 600L);

        for (Player p : Bukkit.getOnlinePlayers()) applyLook(p);
    }

    @Override
    public void onDisable() {
        if (tracker != null) tracker.endAll();
        saveData();
    }

    // ---------- consultas ----------

    public Dupla duplaOf(UUID id) {
        for (Dupla d : duplas.values()) if (d.members.contains(id)) return d;
        return null;
    }

    public Dupla ownerOfStructure(String structureId) {
        for (Dupla d : duplas.values()) if (structureId.equals(d.structure)) return d;
        return null;
    }

    public void notifyDupla(Dupla d, String msg) {
        for (UUID u : d.members) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendMessage(msg);
        }
    }

    public String nameOf(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() != null ? op.getName() : id.toString().substring(0, 8);
    }

    public void markDirty() {
        dirty = true;
    }

    // ---------- renegados ----------

    private Team renegadoTeam() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam("renegados");
        if (t == null) t = sb.registerNewTeam("renegados");
        t.color(NamedTextColor.RED);
        return t;
    }

    public void applyLook(OfflinePlayer op) {
        if (op.getName() == null) return;
        Team t = renegadoTeam();
        boolean ren = renegados.contains(op.getUniqueId());
        if (ren) t.addEntry(op.getName());
        else t.removeEntry(op.getName());

        Player p = op.getPlayer();
        if (p != null) {
            if (ren) {
                Component red = Component.text(p.getName(), NamedTextColor.RED);
                p.displayName(red);
                p.playerListName(red);
            } else {
                p.displayName(null);
                p.playerListName(null);
            }
        }
    }

    public void setRenegado(UUID id, boolean value, String reason) {
        boolean changed = value ? renegados.add(id) : renegados.remove(id);
        if (!changed) return;
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        applyLook(op);
        markDirty();
        String name = nameOf(id);
        if (value) {
            Bukkit.broadcast(Component.text("☠ " + name + " virou RENEGADO (" + reason + ")", NamedTextColor.RED));
            Dupla d = duplaOf(id);
            if (d != null) notifyDupla(d, "§c" + name + " perdeu a chave e virou renegado.");
        } else {
            Bukkit.broadcast(Component.text(name + " não é mais renegado.", NamedTextColor.GREEN));
        }
    }

    // ---------- persistencia ----------

    private File dataFile() {
        return new File(getDataFolder(), "data.yml");
    }

    public void loadData() {
        File f = dataFile();
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        keyTemplate = y.getItemStack("key");
        for (String s : y.getStringList("renegados")) renegados.add(UUID.fromString(s));
        ConfigurationSection ds = y.getConfigurationSection("duplas");
        if (ds == null) return;
        for (String n : ds.getKeys(false)) {
            ConfigurationSection c = ds.getConfigurationSection(n);
            if (c == null) continue;
            Dupla d = new Dupla(c.getString("name", n));
            for (String m : c.getStringList("members")) d.members.add(UUID.fromString(m));
            d.structure = c.getString("structure");
            ConfigurationSection al = c.getConfigurationSection("alerts");
            if (al != null) {
                List<String> ks = new ArrayList<>(al.getKeys(false));
                ks.sort(Comparator.comparingInt(Integer::parseInt));
                for (String k : ks) d.alerts.add(Alert.load(al.getConfigurationSection(k)));
            }
            duplas.put(n.toLowerCase(), d);
        }
    }

    public void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("key", keyTemplate);
        List<String> r = new ArrayList<>();
        for (UUID u : renegados) r.add(u.toString());
        y.set("renegados", r);
        for (Map.Entry<String, Dupla> e : duplas.entrySet()) {
            Dupla d = e.getValue();
            ConfigurationSection c = y.createSection("duplas." + e.getKey());
            c.set("name", d.name);
            List<String> ms = new ArrayList<>();
            for (UUID u : d.members) ms.add(u.toString());
            c.set("members", ms);
            c.set("structure", d.structure);
            ConfigurationSection al = c.createSection("alerts");
            for (int i = 0; i < d.alerts.size(); i++) d.alerts.get(i).save(al.createSection(String.valueOf(i)));
        }
        try {
            getDataFolder().mkdirs();
            y.save(dataFile());
            dirty = false;
        } catch (IOException ex) {
            getLogger().severe("Falha ao salvar data.yml: " + ex.getMessage());
        }
    }
}
