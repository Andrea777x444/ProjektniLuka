package ba.etf.luka.model.interfaces;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Zajednički podaci za sve varijante obalske straže (potjera). */
public final class PotjeraPodaci implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    private List<String> listaPotjera = new ArrayList<>();
    private final String putanjaFajlaPotjera;
    private transient Set<String> zavrseniPotjere = new HashSet<>();

    public PotjeraPodaci(String putanjaFajlaPotjera) {
        this.putanjaFajlaPotjera = putanjaFajlaPotjera;
    }

    public boolean isPotjeraNa(String imoBroj) {
        if (listaPotjera == null || listaPotjera.isEmpty()) return false;
        if (zavrseniPotjere == null) zavrseniPotjere = new HashSet<>();
        if (zavrseniPotjere.contains(imoBroj)) return false;
        return listaPotjera.contains(imoBroj);
    }
    public void zavrsiPotjeru(String imoBroj)        { if (zavrseniPotjere == null) zavrseniPotjere = new HashSet<>(); zavrseniPotjere.add(imoBroj); }
    public void dodajPotjeru(String imoBroj)         { listaPotjera.add(imoBroj); }
    public List<String> getListaPotjera()            { return listaPotjera; }
    public void setListaPotjera(List<String> lista)  { this.listaPotjera = lista; }
    public String getPutanjaFajlaPotjera()           { return putanjaFajlaPotjera; }
}