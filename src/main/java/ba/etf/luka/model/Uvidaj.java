package ba.etf.luka.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Predstavlja jedan uviđaj obalske straže.
 * Čuva se u binarnom fajlu u user.home direktoriju.
 */
public class Uvidaj implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public enum TipUvidaja {
        SUDAR,
        POTJERA
    }

    private final TipUvidaja tip;
    private final LocalDateTime vrijemeUvidaja;
    private final int terminalBroj;

    // Plovila uključena u uviđaj
    private final List<String> imoBrojeviUcesnika;
    private final List<String> naziviUcesnika;

    // Fotografije plovila (putanje do fajlova)
    private final List<String> fotografije;

    // Identifikator fajla (za naziv binarnog fajla)
    private final String idUvidaja;

    public Uvidaj(TipUvidaja tip, int terminalBroj, List<Plovilo> ucesnici) {
        this.tip              = tip;
        this.terminalBroj     = terminalBroj;
        this.vrijemeUvidaja   = LocalDateTime.now();
        this.imoBrojeviUcesnika = new ArrayList<>();
        this.naziviUcesnika   = new ArrayList<>();
        this.fotografije      = new ArrayList<>();
        this.idUvidaja        = generisiId();

        for (Plovilo p : ucesnici) {
            imoBrojeviUcesnika.add(p.getImoBroj());
            naziviUcesnika.add(p.getNaziv());
            if (p.getPutanjaFotografije() != null
                    && !p.getPutanjaFotografije().isBlank()) {
                fotografije.add(p.getPutanjaFotografije());
            }
        }
    }

    private String generisiId() {
        return tip.name() + "_T" + terminalBroj + "_"
                + System.currentTimeMillis();
    }

    // --- Getteri ---

    public TipUvidaja getTip()                   { return tip; }
    public LocalDateTime getVrijemeUvidaja()     { return vrijemeUvidaja; }
    public int getTerminalBroj()                 { return terminalBroj; }
    public List<String> getImoBrojeviUcesnika()  { return imoBrojeviUcesnika; }
    public List<String> getNaziviUcesnika()      { return naziviUcesnika; }
    public List<String> getFotografije()         { return fotografije; }
    public String getIdUvidaja()                 { return idUvidaja; }

    @Override
    public String toString() {
        return "[" + tip + "] Terminal " + terminalBroj
                + " | " + vrijemeUvidaja
                + " | Učesnici: " + naziviUcesnika;
    }
}