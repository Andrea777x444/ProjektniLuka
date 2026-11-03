package ba.etf.luka.model.interfaces;

import java.io.Serial;
import java.io.Serializable;

/** Zajednički podaci svih državnih plovila — rotacija, prioritet, tip.
 * Kompozicija umjesto nasljeđivanja jer svaka državna klasa već nasljeđuje
 * svoj komercijalni tip (KontejnerskiBrod/Kruzer/Tanker). */
public final class DrzavniPodaci implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    private final TipDrzavnog tip;
    private final int prioritet;
    private boolean rotacija = false;

    public DrzavniPodaci(TipDrzavnog tip, int prioritet) {
        this.tip = tip;
        this.prioritet = prioritet;
    }

    public TipDrzavnog getTip()         { return tip; }
    public int getPrioritet()           { return prioritet; }
    public boolean isRotacijaUpaljena() { return rotacija; }
    public void setRotacija(boolean v)  { this.rotacija = v; }
}