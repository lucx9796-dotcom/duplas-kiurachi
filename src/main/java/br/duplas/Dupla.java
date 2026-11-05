package br.duplas;

import java.util.*;

public class Dupla {
    public final String name;
    public final List<UUID> members = new ArrayList<>();
    public String structure; // id do catalogo (ex: casa_da_bruxa) ou null
    public final List<Alert> alerts = new ArrayList<>();

    public Dupla(String name) {
        this.name = name;
    }
}
