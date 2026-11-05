package br.duplas;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.stream.Collectors;

public class DuplaCommand implements CommandExecutor, TabCompleter {

    private final DuplasPlugin pl;

    public DuplaCommand(DuplasPlugin pl) {
        this.pl = pl;
    }

    private void msg(CommandSender s, String m) {
        s.sendMessage(m);
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("duplas.admin")) return true;
        msg(s, "§cSem permissão.");
        return false;
    }

    private OfflinePlayer resolve(String name) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        return (op.isOnline() || op.hasPlayedBefore()) ? op : null;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (c.getName().equalsIgnoreCase("renegado")) return renegado(s, a);
        return dupla(s, a);
    }

    // ================= /dupla =================

    private boolean dupla(CommandSender s, String[] a) {
        if (a.length == 0) {
            help(s);
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "criar" -> criar(s, a);
            case "remover" -> remover(s, a);
            case "estrutura" -> estrutura(s, a);
            case "lista" -> lista(s);
            case "chave" -> chave(s, a);
            case "alertas" -> alertas(s, a);
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        msg(s, "§e/dupla criar <nome> <jogador1> <jogador2>");
        msg(s, "§e/dupla remover <nome>");
        msg(s, "§e/dupla estrutura <nome> [estrutura] §7(sem estrutura = lista as disponíveis)");
        msg(s, "§e/dupla lista");
        msg(s, "§e/dupla chave definir §7(item na mão vira a chave)");
        msg(s, "§e/dupla chave dar <nome>");
        msg(s, "§e/dupla alertas [nome] [limpar]");
        msg(s, "§e/renegado <adicionar|remover|lista> [jogador]");
    }

    private void criar(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length != 4) {
            msg(s, "§cUso: /dupla criar <nome> <jogador1> <jogador2>");
            return;
        }
        String name = a[1];
        if (!name.matches("[A-Za-z0-9_-]{1,24}")) {
            msg(s, "§cNome inválido (use letras, números, _ ou -).");
            return;
        }
        if (pl.duplas.containsKey(name.toLowerCase())) {
            msg(s, "§cJá existe uma dupla com esse nome.");
            return;
        }
        OfflinePlayer p1 = resolve(a[2]), p2 = resolve(a[3]);
        if (p1 == null || p2 == null) {
            msg(s, "§cJogador não encontrado (precisa já ter entrado no servidor).");
            return;
        }
        if (p1.getUniqueId().equals(p2.getUniqueId())) {
            msg(s, "§cEscolha dois jogadores diferentes.");
            return;
        }
        for (OfflinePlayer p : List.of(p1, p2)) {
            Dupla ex = pl.duplaOf(p.getUniqueId());
            if (ex != null) {
                msg(s, "§c" + p.getName() + " já está na dupla " + ex.name + ".");
                return;
            }
        }
        Dupla d = new Dupla(name);
        d.members.add(p1.getUniqueId());
        d.members.add(p2.getUniqueId());
        pl.duplas.put(name.toLowerCase(), d);
        pl.markDirty();
        msg(s, "§aDupla §f" + name + " §acriada: §f" + p1.getName() + " §ae §f" + p2.getName());
    }

    private void remover(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length != 2) {
            msg(s, "§cUso: /dupla remover <nome>");
            return;
        }
        Dupla d = pl.duplas.remove(a[1].toLowerCase());
        if (d == null) {
            msg(s, "§cDupla não encontrada.");
            return;
        }
        pl.markDirty();
        msg(s, "§aDupla §f" + d.name + " §aremovida.");
    }

    private void estrutura(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 2) {
            msg(s, "§cUso: /dupla estrutura <nome> <estrutura>");
            return;
        }
        Dupla d = pl.duplas.get(a[1].toLowerCase());
        if (d == null) {
            msg(s, "§cDupla não encontrada.");
            return;
        }
        if (a.length == 2) {
            listarEstruturas(s);
            return;
        }
        Structures.Def def = Structures.get(a[2]);
        if (def == null) {
            msg(s, "§cEstrutura inválida.");
            listarEstruturas(s);
            return;
        }
        Dupla other = pl.ownerOfStructure(def.id());
        if (other != null && other != d) {
            msg(s, "§cA estrutura §f" + def.display() + " §cjá pertence à dupla §f" + other.name + "§c.");
            return;
        }
        d.structure = def.id();
        pl.markDirty();
        msg(s, "§aA dupla §f" + d.name + " §aagora é dona de: §e" + def.display()
                + " §7(todas as estruturas desse tipo no Overworld)");
    }

    private void listarEstruturas(CommandSender s) {
        msg(s, "§6Estruturas disponíveis:");
        for (Structures.Def def : Structures.ALL.values()) {
            Dupla o = pl.ownerOfStructure(def.id());
            msg(s, " §e" + def.id() + " §7- " + def.display()
                    + (o != null ? " §c(em uso: " + o.name + ")" : " §a(livre)"));
        }
    }

    private void lista(CommandSender s) {
        if (!admin(s)) return;
        if (pl.duplas.isEmpty()) {
            msg(s, "§7Nenhuma dupla criada.");
            return;
        }
        for (Dupla d : pl.duplas.values()) {
            String mem = d.members.stream().map(u -> (pl.renegados.contains(u) ? "§c" : "§f") + pl.nameOf(u))
                    .collect(Collectors.joining("§7, "));
            Structures.Def def = Structures.get(d.structure);
            msg(s, "§e" + d.name + " §7[" + mem + "§7] estrutura: §f" + (def != null ? def.display() : "nenhuma")
                    + " §7| alertas: §f" + d.alerts.size());
        }
    }

    private void chave(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 2) {
            msg(s, "§cUso: /dupla chave <definir|dar>");
            return;
        }
        if (a[1].equalsIgnoreCase("definir")) {
            if (!(s instanceof Player p)) {
                msg(s, "§cApenas jogadores.");
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                msg(s, "§cSegure o item que será a chave.");
                return;
            }
            ItemStack t = hand.clone();
            t.setAmount(1);
            ItemMeta meta = t.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().remove(pl.keyOwner);
                t.setItemMeta(meta);
            }
            pl.keyTemplate = t;
            pl.markDirty();
            msg(s, "§aItem definido como chave: §f" + t.getType().name());
        } else if (a[1].equalsIgnoreCase("dar")) {
            if (a.length != 3) {
                msg(s, "§cUso: /dupla chave dar <nome>");
                return;
            }
            Dupla d = pl.duplas.get(a[2].toLowerCase());
            if (d == null) {
                msg(s, "§cDupla não encontrada.");
                return;
            }
            if (pl.keyTemplate == null) {
                msg(s, "§cDefina a chave primeiro: /dupla chave definir");
                return;
            }
            for (UUID u : d.members) {
                Player p = Bukkit.getPlayer(u);
                if (p == null) {
                    msg(s, "§e" + pl.nameOf(u) + " está offline, não recebeu a chave.");
                    continue;
                }
                ItemStack key = pl.keyTemplate.clone();
                ItemMeta meta = key.getItemMeta();
                meta.getPersistentDataContainer().set(pl.keyOwner, PersistentDataType.STRING, u.toString());
                key.setItemMeta(meta);
                Map<Integer, ItemStack> left = p.getInventory().addItem(key);
                left.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
                msg(s, "§aChave entregue a §f" + p.getName());
            }
        } else {
            msg(s, "§cUso: /dupla chave <definir|dar>");
        }
    }

    private void alertas(CommandSender s, String[] a) {
        Dupla d;
        if (a.length >= 2) {
            if (!admin(s)) return;
            d = pl.duplas.get(a[1].toLowerCase());
        } else {
            d = (s instanceof Player p) ? pl.duplaOf(p.getUniqueId()) : null;
        }
        if (d == null) {
            msg(s, "§cDupla não encontrada.");
            return;
        }
        if (a.length >= 3 && a[2].equalsIgnoreCase("limpar")) {
            if (!admin(s)) return;
            d.alerts.clear();
            pl.markDirty();
            msg(s, "§aAlertas da dupla §f" + d.name + " §alimpos.");
            return;
        }
        if (d.alerts.isEmpty()) {
            msg(s, "§7Nenhuma invasão registrada.");
            return;
        }
        int from = Math.max(0, d.alerts.size() - 15);
        for (Alert al : d.alerts.subList(from, d.alerts.size())) al.lines().forEach(l -> msg(s, l));
    }

    // ================= /renegado =================

    private boolean renegado(CommandSender s, String[] a) {
        if (!admin(s)) return true;
        if (a.length == 0) {
            msg(s, "§eUso: /renegado <adicionar|remover|lista> [jogador]");
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "lista" -> {
                if (pl.renegados.isEmpty()) msg(s, "§7Nenhum renegado.");
                else msg(s, "§cRenegados: §f" + pl.renegados.stream().map(pl::nameOf).collect(Collectors.joining(", ")));
            }
            case "adicionar", "remover" -> {
                if (a.length != 2) {
                    msg(s, "§cUso: /renegado " + a[0] + " <jogador>");
                    return true;
                }
                OfflinePlayer op = resolve(a[1]);
                if (op == null) {
                    msg(s, "§cJogador não encontrado.");
                    return true;
                }
                boolean add = a[0].equalsIgnoreCase("adicionar");
                if (add == pl.renegados.contains(op.getUniqueId())) {
                    msg(s, add ? "§cJá é renegado." : "§cNão é renegado.");
                    return true;
                }
                pl.setRenegado(op.getUniqueId(), add, "definido por um administrador");
            }
            default -> msg(s, "§eUso: /renegado <adicionar|remover|lista> [jogador]");
        }
        return true;
    }

    // ================= tab complete =================

    private List<String> filter(Collection<String> opts, String prefix) {
        String p = prefix.toLowerCase();
        return opts.stream().filter(o -> o.toLowerCase().startsWith(p)).sorted().collect(Collectors.toList());
    }

    private List<String> online() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("duplas.admin")) {
            if (c.getName().equals("dupla") && a.length == 1) return filter(List.of("alertas"), a[0]);
            return List.of();
        }
        if (c.getName().equalsIgnoreCase("renegado")) {
            if (a.length == 1) return filter(List.of("adicionar", "remover", "lista"), a[0]);
            if (a.length == 2 && a[0].equalsIgnoreCase("adicionar")) return filter(online(), a[1]);
            if (a.length == 2 && a[0].equalsIgnoreCase("remover"))
                return filter(pl.renegados.stream().map(pl::nameOf).collect(Collectors.toList()), a[1]);
            return List.of();
        }
        List<String> nomes = pl.duplas.values().stream().map(d -> d.name).collect(Collectors.toList());
        if (a.length == 1) return filter(List.of("criar", "remover", "estrutura", "lista", "chave", "alertas"), a[0]);
        switch (a[0].toLowerCase()) {
            case "criar":
                if (a.length == 3 || a.length == 4) return filter(online(), a[a.length - 1]);
                break;
            case "remover":
                if (a.length == 2) return filter(nomes, a[1]);
                break;
            case "estrutura":
                if (a.length == 2) return filter(nomes, a[1]);
                if (a.length == 3) return filter(Structures.ALL.keySet(), a[2]);
                break;
            case "alertas":
                if (a.length == 2) return filter(nomes, a[1]);
                if (a.length == 3) return filter(List.of("limpar"), a[2]);
                break;
            case "chave":
                if (a.length == 2) return filter(List.of("definir", "dar"), a[1]);
                if (a.length == 3 && a[1].equalsIgnoreCase("dar")) return filter(nomes, a[2]);
                break;
        }
        return List.of();
    }
}
