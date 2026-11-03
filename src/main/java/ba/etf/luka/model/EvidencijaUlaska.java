package ba.etf.luka.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

public class EvidencijaUlaska implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String imoBroj;
    private final String nazivPlovila;
    private final LocalDateTime vrijemeUlaska;
    private LocalDateTime vrijemeIzlaska;
    private final boolean drzavno; // vatrogasci/OS/carina — izuzeti od takse

    /**
     * Npr. sa vrijednošću 3.0: brod koji stvarno stoji 15 sekundi
     * "predstavlja" 5 simuliranih sati zadržavanja.
     */
    private static final double SEKUNDI_PO_SIMULIRANOM_SATU = 3.0;

    public EvidencijaUlaska(Plovilo plovilo) {
        this.imoBroj = plovilo.getImoBroj();
        this.nazivPlovila = plovilo.getNaziv();
        this.vrijemeUlaska = LocalDateTime.now();
        this.drzavno = plovilo instanceof ba.etf.luka.model.interfaces.DrzavnoPlovilo;
    }

    public void evidentirIzlazak() {
        this.vrijemeIzlaska = LocalDateTime.now();
    }

    /** Pretvara stvarno proteklo vrijeme (sekunde) u "simulirane sate" zadržavanja. */
    private double izracunajSimuliraneSate() {
        long sekunde = java.time.Duration.between(vrijemeUlaska, vrijemeIzlaska).getSeconds();
        return sekunde / SEKUNDI_PO_SIMULIRANOM_SATU;
    }

    public double izracunajTaksu() {
        if (drzavno) return 0.0;           // OS/Carina/Vatrogasci ne plaćaju
        if (vrijemeIzlaska == null) return 0.0; // taksa se naplaćuje TEK pri izlasku

        double sati = izracunajSimuliraneSate();

        if (sati <= 1)  return 100.0;
        if (sati <= 12) return 1000.0;
        if (sati <= 24) return 2000.0;
        return 2000.0 + (sati - 24) * 100.0; // preko 24h — nastavlja se osnovna satnica
    }

    public String  getImoBroj()             { return imoBroj; }
    public String  getNazivPlovila()        { return nazivPlovila; }
    public LocalDateTime getVrijemeUlaska() { return vrijemeUlaska; }
    public LocalDateTime getVrijemeIzlaska(){ return vrijemeIzlaska; }
    public boolean isDrzavno()              { return drzavno; }

    public String toCsvRed() {
        return String.join(",",
                imoBroj,
                nazivPlovila,
                vrijemeUlaska.toString(),
                vrijemeIzlaska != null ? vrijemeIzlaska.toString() : "u luci",
                String.format("%.2f KM", izracunajTaksu())
        );
    }
}