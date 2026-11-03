package ba.etf.luka.model;

import java.io.Serial;
import java.io.Serializable;

public class Dok implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int redniBroj;
    private Plovilo plovilo;  // null = slobodan dok

    public Dok(int redniBroj) {
        this.redniBroj = redniBroj;
        this.plovilo = null;
    }

    public boolean isSlobodan() {
        return plovilo == null;
    }

    public void privaziPlovilo(Plovilo plovilo) {
        if (!isSlobodan()) {
            throw new IllegalStateException("Dok " + redniBroj + " je već zauzet!");
        }
        this.plovilo = plovilo;
    }

    public Plovilo odvaziPlovilo() {
        Plovilo odlazece = this.plovilo;
        this.plovilo = null;
        return odlazece;
    }

    public Plovilo getPlovilo() { return plovilo; }
    public int getRedniBroj() { return redniBroj; }

    @Override
    public String toString() {
        return isSlobodan() ? "*" : plovilo.getOznaka();
    }
}