package ba.etf.luka.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Terminal implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int redniBroj;
    private final int brojDokova;      // stvarni broj dokova iz konfiguracije
    private final List<Dok> gornjiDokovi;
    private final List<Dok> donjiDokovi;

    private final Plovilo[][] kanal;
    private Plovilo ulaznaKapija;   // "↓" — cekanje prije ulaska u kanal
    private Plovilo izlaznaKapija;  // "↑" — cekanje nakon izlaska iz kanala

    // Konstanta za kompatibilnost sa PloviloThread-om
    // Postavlja se dinamicki u konstruktoru
    private final int duzinаKanala;

    public Terminal(int redniBroj, int brojDokova) {
        this.redniBroj    = redniBroj;
        this.brojDokova   = brojDokova;
        this.duzinаKanala = brojDokova; // Kanal = tacno broj dokova, nista vise
        this.kanal        = new Plovilo[4][duzinаKanala];

        this.gornjiDokovi = new ArrayList<>();
        this.donjiDokovi  = new ArrayList<>();
        for (int i = 0; i < brojDokova; i++) {
            gornjiDokovi.add(new Dok(i));
            donjiDokovi.add(new Dok(i));
        }
    }

    // Ovo je kljucna metoda — PloviloThread koristi DUZINA_KANALA
    // Vise nije staticka konstanta nego metoda koja vraca stvarnu duzinu
    public int getDuzinaKanala() { return duzinаKanala; }

    // --- Upravljanje dokovima ---

    public int getBrojSlobodnihVezova() {
        int slobodni = 0;
        for (Dok d : gornjiDokovi) if (d.isSlobodan()) slobodni++;
        for (Dok d : donjiDokovi)  if (d.isSlobodan()) slobodni++;
        return slobodni;
    }

    public boolean imaSlobiodanDok() {
        return getBrojSlobodnihVezova() > 0;
    }

    public Dok getFirstSlobodanDok() {
        for (Dok d : gornjiDokovi) if (d.isSlobodan()) return d;
        for (Dok d : donjiDokovi)  if (d.isSlobodan()) return d;
        return null;
    }

    public List<Dok> getGornjiDokovi() { return gornjiDokovi; }
    public List<Dok> getDonjiDokovi()  { return donjiDokovi; }

    // --- Upravljanje kanalom ---

    public Plovilo getPozicijaKanala(int red, int kolona) {
        if (kolona < 0 || kolona >= duzinаKanala) return null;
        return kanal[red][kolona];
    }

    public void setPozicijaKanala(int red, int kolona, Plovilo plovilo) {
        if (kolona < 0 || kolona >= duzinаKanala) return;
        kanal[red][kolona] = plovilo;
    }

    public boolean isKanalSlobodan(int red, int kolona) {
        if (kolona < 0 || kolona >= duzinаKanala) return false;
        return kanal[red][kolona] == null;
    }

    public void ocistiKanal() {
        for (int red = 0; red < 4; red++)
            for (int k = 0; k < duzinаKanala; k++)
                kanal[red][k] = null;
        ulaznaKapija = null;
        izlaznaKapija = null;
    }

    public Plovilo[][] getKanal() { return kanal; }

    // --- Prikaz ---

    public String[][] getMatricaPrikaza() {
        String[][] prikaz = new String[4][duzinаKanala];

        for (int i = 0; i < gornjiDokovi.size(); i++)
            prikaz[0][i] = gornjiDokovi.get(i).toString();

        for (int i = 0; i < donjiDokovi.size(); i++)
            prikaz[3][i] = donjiDokovi.get(i).toString();

        for (int k = 0; k < duzinаKanala; k++) {
            prikaz[1][k] = kanal[1][k] != null
                    ? formatOznaka(kanal[1][k]) : null;
            prikaz[2][k] = kanal[2][k] != null
                    ? formatOznaka(kanal[2][k]) : null;
        }
        return prikaz;
    }
    /**
     * Provjerava da li se plovilo nalazi u nekom doku ovog terminala.
     */
    public boolean sadrziPloviloUDoku(Plovilo plovilo) {
        for (Dok d : getGornjiDokovi()) {
            if (!d.isSlobodan() && d.getPlovilo() == plovilo) return true;
        }
        for (Dok d : getDonjiDokovi()) {
            if (!d.isSlobodan() && d.getPlovilo() == plovilo) return true;
        }
        return false;
    }
    public synchronized boolean isUlaznaKapijaSlobodna()  { return ulaznaKapija == null; }
    public synchronized boolean isIzlaznaKapijaSlobodna() { return izlaznaKapija == null; }

    public synchronized boolean udjiNaUlaznuKapiju(Plovilo p) {
        if (ulaznaKapija != null) return false;
        ulaznaKapija = p; return true;
    }
    public synchronized void napustiUlaznuKapiju(Plovilo p) {
        if (ulaznaKapija == p) ulaznaKapija = null;
    }
    public synchronized boolean udjiNaIzlaznuKapiju(Plovilo p) {
        if (izlaznaKapija != null) return false;
        izlaznaKapija = p; return true;
    }
    public synchronized void napustiIzlaznuKapiju(Plovilo p) {
        if (izlaznaKapija == p) izlaznaKapija = null;
    }

    public String getUlaznaKapijaOznaka() {
        return ulaznaKapija != null ? formatOznaka(ulaznaKapija) : "↓";
    }
    public String getIzlaznaKapijaOznaka() {
        return izlaznaKapija != null ? formatOznaka(izlaznaKapija) : "↑";
    }

    private String formatOznaka(Plovilo p) {
        String oznaka = p.getOznaka();
        if (p instanceof ba.etf.luka.model.interfaces.DrzavnoPlovilo dp
                && dp.isRotacijaUpaljena()) {
            oznaka += "R";
        }
        return oznaka;
    }
    public Dok getFirstSlobodanDonjiDok() {
        for (Dok d : donjiDokovi) if (d.isSlobodan()) return d;
        return null;
    }
    /** Bira nasumičan slobodan dok između gornjih i donjih (ravnomjeran raspored). */
    public Dok getSlucajanSlobodanDok(java.util.Random random) {
        List<Dok> slobodni = new ArrayList<>();
        for (Dok d : gornjiDokovi) if (d.isSlobodan()) slobodni.add(d);
        for (Dok d : donjiDokovi)  if (d.isSlobodan()) slobodni.add(d);
        if (slobodni.isEmpty()) return null;
        return slobodni.get(random.nextInt(slobodni.size()));
    }

    // --- Evidencija ---

    public List<Plovilo> getSvaPlovilaD() {
        List<Plovilo> sva = new ArrayList<>();
        for (Dok d : gornjiDokovi) if (!d.isSlobodan()) sva.add(d.getPlovilo());
        for (Dok d : donjiDokovi)  if (!d.isSlobodan()) sva.add(d.getPlovilo());
        return sva;
    }

    public int getRedniBroj()   { return redniBroj; }
    public int getBrojDokova()  { return brojDokova; }

    @Override
    public String toString() {
        return "Terminal " + redniBroj
                + " (slobodni: " + getBrojSlobodnihVezova() + ")";
    }
}