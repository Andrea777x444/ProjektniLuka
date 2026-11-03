package ba.etf.luka.model;

import ba.etf.luka.user.SimulacijaEngine;
import ba.etf.luka.util.LoggerUtil;

import java.io.Serial;
import java.io.Serializable;
import java.util.Random;

/**
 * Plovilo je nit (Thread) koja izvršava vlastitu logiku kretanja kroz kanal terminala.
 */
public abstract class Plovilo extends Thread implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    // --- Osnovni podaci (serijalizabilni) ---
    private String naziv;
    private String imoBroj;
    private int    brojMotora;
    private String registarskiBroj;
    private String putanjaFotografije;
    private double brzina;

    private transient Terminal ciljniUlazniTerminal; // terminal koji je korisnik izabrao kao odrediste ulaska


    private transient SimulacijaEngine engine;
    private transient Terminal         trenutniTerminal;
    private transient volatile Stanje  stanje = Stanje.NA_DOKU;
    private transient int              red;
    private transient int              kolona;

    private transient volatile boolean bezSkracenice     = false; // za potjeru: OS ne smije "skraćivati" put

    private transient volatile boolean aktivan           = false;
    private transient long             rokIzlaska        = 0;  // apsolutni rok izlaska (pomjera se za pauze)
    private transient volatile boolean pauziranZbogSudara = false; // stoji dok traje uviđaj na terminalu
    private transient volatile boolean pauziranZaPreticanje = false; // neko me pretiče, stojim
    private transient volatile boolean stigaoNaSudar     = false;
    private transient volatile boolean imuniNaSudar      = false; // sudionik/državno nakon uviđaja ne izaziva novi sudar


    private transient int      kolonaCilja    = -1;
    private transient Terminal ciljniTerminal = null;


    private static final Random RANDOM = new Random();
    public static final int RED_KAPIJA_ULAZ  = 4;
    public static final int RED_KAPIJA_IZLAZ = 5;


    /** Vjerovatnoća sudara pri mimoilaženju.  */
    private static final double VJEROVATNOCA_SUDARA = 0.02;

    private static final int SLOBODNIH_ZA_PRETICANJE = 3;

    public static final int RED_GORNJI_DOK = 0;
    public static final int RED_LIJEVO     = 1;
    public static final int RED_DESNO      = 2;
    public static final int RED_DONJI_DOK  = 3;
    public static final int KOLONA_IZLAZA  = 0;


    public enum Stanje {
        NA_DOKU,
        ULAZ,
        IZLAZ,
        DOLAZAK_NA_SUDAR,
        CEKA_UVIDAJ,
        IZASLO
    }

    public Plovilo(String naziv, String imoBroj, int brojMotora,
                   String registarskiBroj, String putanjaFotografije) {
        super("Plovilo-" + imoBroj);
        setDaemon(true);
        this.naziv              = naziv;
        this.imoBroj            = imoBroj;
        this.brojMotora         = brojMotora;
        this.registarskiBroj    = registarskiBroj;
        this.putanjaFotografije = putanjaFotografije;
        this.brzina             = 0.5 + RANDOM.nextDouble() * 1.5;
    }

    public abstract String getOznaka();


    protected int getPrioritet() {
        return 0;
    }

    protected boolean isRotacijaUpaljena() {
        return false;
    }

    /** Priprema plovilo koje kreće sa doka ka izlazu. Pozvati start() nakon ovoga. */
    public void pripremiIzlaz(SimulacijaEngine engine, Terminal terminal) {
        this.engine           = engine;
        this.trenutniTerminal = terminal;
        this.stanje           = Stanje.IZLAZ;
        this.aktivan          = true;
        this.pauziranZbogSudara   = false;
        this.pauziranZaPreticanje = false;
        this.bezSkracenice       = false;
        int[] pos = pronadiPozicijuNaTerminalu(terminal);
        if (pos != null) { red = pos[0]; kolona = pos[1]; }
        else             { red = RED_DESNO; kolona = KOLONA_IZLAZA; }
    }

    /** Zabrani/dozvoli "skraćenicu" (skok na lijevu traku) pri izlasku. */
    public void postaviBezSkracenice(boolean v) { this.bezSkracenice = v; }

    /** Priprema novo plovilo koje ULAZI u luku i traži slobodan dok. */
    public void pripremiUlaz(SimulacijaEngine engine, Terminal terminal) {
        this.engine               = engine;
        this.ciljniUlazniTerminal = terminal;
        this.stanje               = Stanje.ULAZ;
        this.aktivan              = true;
        this.pauziranZbogSudara   = false;
        this.pauziranZaPreticanje = false;
    }

    /**
     * Priprema državno plovilo za dolazak na sudar.
     */
    public void pripremiDolazakNaSudar(SimulacijaEngine engine,
                                       Terminal ciljniTerminal, int kolonaSudara) {
        this.engine         = engine;
        this.ciljniTerminal = ciljniTerminal;
        this.kolonaCilja    = kolonaSudara;
        this.stanje         = Stanje.DOLAZAK_NA_SUDAR;
        this.aktivan        = true;
        this.stigaoNaSudar  = false;
        this.pauziranZbogSudara   = false;
        this.pauziranZaPreticanje = false;
    }


    @Override
    public void run() {
        try {
            while (aktivan && stanje != Stanje.IZASLO) {
                switch (stanje) {
                    case ULAZ             -> fazaUlaz();
                    case IZLAZ            -> fazaIzlaz();
                    case DOLAZAK_NA_SUDAR -> fazaDolazakNaSudar();
                    case CEKA_UVIDAJ      -> cekajMalo();
                    default               -> { return; }
                }
            }
        } catch (InterruptedException e) {

        } finally {
            aktivan = false;
        }
    }

    private void fazaIzlaz() throws InterruptedException {
        int duzina = trenutniTerminal.getDuzinaKanala();

        rokIzlaska = System.currentTimeMillis() + 22_000;

        // 1. Ako smo na doku, izađi na desnu traku na istoj koloni
        if (red == RED_GORNJI_DOK || red == RED_DONJI_DOK) {
            int mojaKolona = kolona;
            if (!cekajPa(RED_DESNO, mojaKolona)) { forsirajIzlaz(); return; }
            synchronized (trenutniTerminal) {
                if (!trenutniTerminal.isKanalSlobodan(RED_DESNO, mojaKolona)) {
                    return;
                }
                ukloniSaDoka(red, mojaKolona);
                trenutniTerminal.setPozicijaKanala(RED_DESNO, mojaKolona, this);
                red = RED_DESNO; kolona = mojaKolona;
            }
            osvjezi(); spavaj();
        }

        // 2. Desna traka: idi udesno do kraja kanala
        if (red == RED_DESNO) {
            int neuspjeha = 0;
            while (aktivan && stanje == Stanje.IZLAZ && kolona < duzina - 1) {
                if (pauziranZbogSudara || (engine != null && engine.isGlobalnoPauzirano())) {
                    long prije = System.currentTimeMillis();
                    cekajDokPauza();
                    rokIzlaska += System.currentTimeMillis() - prije; // pauza se ne broji
                    if (!aktivan) return;
                }
                if (stanje != Stanje.IZLAZ) return; // preusmjereni (npr. na sudar)
                if (rokIstekao()) { forsirajIzlaz(); return; }

                if (getPrioritet() > 0 && !bezSkracenice && kolona > 0
                        && trenutniTerminal.isKanalSlobodan(RED_LIJEVO, kolona)) {
                    synchronized (trenutniTerminal) {
                        if (trenutniTerminal.isKanalSlobodan(RED_LIJEVO, kolona)) {
                            if (trenutniTerminal.getPozicijaKanala(RED_DESNO, kolona) == this)
                                trenutniTerminal.setPozicijaKanala(RED_DESNO, kolona, null);
                            trenutniTerminal.setPozicijaKanala(RED_LIJEVO, kolona, this);
                            red = RED_LIJEVO;
                        }
                    }
                    if (red == RED_LIJEVO) { osvjezi(); spavaj(); break; } // pređi na lijevu petlju
                }

                if (korak(RED_DESNO, kolona + 1, true)) {
                    neuspjeha = 0;
                } else {
                    if (!aktivan) return;
                    neuspjeha++;
                    if (neuspjeha >= 35) { forsirajIzlaz(); return; }
                    Thread.sleep(150);
                }
            }
            if (!aktivan || stanje != Stanje.IZLAZ) return;

            // 3. Prelaz gore na lijevu traku (zadnja kolona) — samo ako smo
            //    stigli do kraja (nismo se već popeli skraćenicom)
            if (red == RED_DESNO) {
                if (!predjiNaLijevuTraku(duzina - 1)) { forsirajIzlaz(); return; }
            }
        }

        // 4. Lijeva traka: idi ulijevo do izlaza
        int neuspjeha = 0;
        while (aktivan && stanje == Stanje.IZLAZ && kolona > KOLONA_IZLAZA) {
            if (pauziranZbogSudara || (engine != null && engine.isGlobalnoPauzirano())) {
                long prije = System.currentTimeMillis();
                cekajDokPauza();
                rokIzlaska += System.currentTimeMillis() - prije;
                if (!aktivan) return;
            }
            if (stanje != Stanje.IZLAZ) return; // preusmjereni
            if (rokIstekao()) { forsirajIzlaz(); return; }
            if (korak(RED_LIJEVO, kolona - 1, false)) {
                neuspjeha = 0;
            } else {
                if (!aktivan) return;
                neuspjeha++;
                if (neuspjeha >= 35) { forsirajIzlaz(); return; }
                Thread.sleep(150);
            }
        }
        if (!aktivan || stanje != Stanje.IZLAZ) return;

        // 5. Stigli smo do izlazne linije OVOG terminala.
        if (!izadjiIzKanalaNaIzlaznuKapiju()) { forsirajIzlaz(); return; }
        nastaviKaIzlazuLuke();
    }
    /**
     * Plovilo je stiglo do izlazne linije (RED_LIJEVO, kolona 0) svog trenutnog
     * terminala. Ako je to Terminal 1 (prava granica luke), stvarno napusta
     * luku. Inace mora TRANZITIRATI kroz prethodni terminal (blizi izlazu) —
     * pojaviti se na njegovoj ulaznoj pa izlaznoj liniji — i tako redom sve dok
     * ne stigne do Terminala 1.
     */
    private void nastaviKaIzlazuLuke() throws InterruptedException {
        if (trenutniTerminal.getRedniBroj() == 1) {
            izadjiIzLuke();
            return;
        }
        Terminal prethodni = engine.getLuka().getTerminal(trenutniTerminal.getRedniBroj() - 2);
        napustiIzlaznuKapijuTrenutnog();
        if (!udjiNaUlaznuKapiju(prethodni))         { forsirajIzlaz(); return; }
        if (!predjiSaUlazneNaIzlaznuKapiju())       { forsirajIzlaz(); return; }
        nastaviKaIzlazuLuke();
    }
    public void nastaviKaIzlazu() {
        this.pauziranZbogSudara = false;
        this.imuniNaSudar       = true;
        this.stanje = Stanje.IZLAZ;
    }

    /** Atomski prelaz sa desne trake (zadnja kolona) na lijevu traku (zadnja kolona). */
    private boolean predjiNaLijevuTraku(int zadnjaKolona) throws InterruptedException {
        int cekano = 0;
        while (aktivan && cekano < 10_000) {
            if (trenutniTerminal.isKanalSlobodan(RED_LIJEVO, zadnjaKolona)) break;
            Thread.sleep(150);
            cekano += 150;
        }
        if (!aktivan) return false;

        synchronized (trenutniTerminal) {
            if (!trenutniTerminal.isKanalSlobodan(RED_LIJEVO, zadnjaKolona)) {
                return false; // pozivalac će forsirati izlaz
            }
            if (trenutniTerminal.getPozicijaKanala(RED_DESNO, kolona) == this) {
                trenutniTerminal.setPozicijaKanala(RED_DESNO, kolona, null);
            }
            trenutniTerminal.setPozicijaKanala(RED_LIJEVO, zadnjaKolona, this);
            red = RED_LIJEVO; kolona = zadnjaKolona;
        }
        osvjezi(); spavaj();
        return true;
    }

    /** Vraća [red, kolona] doka na kojem je ovo plovilo privezano, ili null ako nije na doku. */
    public int[] getPozicijaDoka(Terminal terminal) {
        for (int k = 0; k < terminal.getGornjiDokovi().size(); k++) {
            Dok d = terminal.getGornjiDokovi().get(k);
            if (!d.isSlobodan() && d.getPlovilo() == this) return new int[]{RED_GORNJI_DOK, k};
        }
        for (int k = 0; k < terminal.getDonjiDokovi().size(); k++) {
            Dok d = terminal.getDonjiDokovi().get(k);
            if (!d.isSlobodan() && d.getPlovilo() == this) return new int[]{RED_DONJI_DOK, k};
        }
        return null;
    }
    // ================================================================
    // FAZA: ULAZ
    // ================================================================

    private void fazaUlaz() throws InterruptedException {
        Terminal t1 = engine.getLuka().getTerminal(0);
        if (!udjiNaUlaznuKapiju(t1)) { stanje = Stanje.IZASLO; return; }
        engine.evidentirUlazak(this);

        while (trenutniTerminal.getRedniBroj() < ciljniUlazniTerminal.getRedniBroj()) {
            if (!predjiSaUlazneNaIzlaznuKapiju()) { oznaciNijePrivezanoIIzadji(); return; }
            Terminal sljedeci = engine.getLuka().getTerminal(trenutniTerminal.getRedniBroj());
            napustiIzlaznuKapijuTrenutnog();
            if (!udjiNaUlaznuKapiju(sljedeci)) { oznaciNijePrivezanoIIzadji(); return; }
        }

        while (true) {
            if (!udjiUKanalIzUlazneKapije()) { oznaciNijePrivezanoIIzadji(); return; }
            if (fazaUlazNaTerminalu()) return; // privezano ili preusmjereno na potjeru
            if (!aktivan) return;

            int sljedeciIdx = trenutniTerminal.getRedniBroj();
            if (sljedeciIdx >= engine.getLuka().getBrojTerminala()) {
                oznaciNijePrivezanoIIzadji();
                return;
            }
            if (!izadjiIzKanalaNaIzlaznuKapiju()) { oznaciNijePrivezanoIIzadji(); return; }
            Terminal sljedeci = engine.getLuka().getTerminal(sljedeciIdx);
            napustiIzlaznuKapijuTrenutnog();
            if (!udjiNaUlaznuKapiju(sljedeci)) { oznaciNijePrivezanoIIzadji(); return; }
        }
    }
    /** Cekanje pa ulazak na ulaznu kapiju datog terminala (max ~8s). Postavlja trenutniTerminal. */
    private boolean udjiNaUlaznuKapiju(Terminal t) throws InterruptedException {
        int cekano = 0;
        while (aktivan && !t.isUlaznaKapijaSlobodna() && cekano < 8000) {
            Thread.sleep(150); cekano += 150;
        }
        if (!aktivan) return false;
        synchronized (t) {
            if (!t.isUlaznaKapijaSlobodna()) return false;
            t.udjiNaUlaznuKapiju(this);
            trenutniTerminal = t;
            red = RED_KAPIJA_ULAZ;
        }
        osvjezi(); spavaj();
        return true;
    }

    /** Tranzit: direktno sa ulazne na izlaznu kapiju ISTOG terminala (bez ulaska u kanal). */
    private boolean predjiSaUlazneNaIzlaznuKapiju() throws InterruptedException {
        int cekano = 0;
        while (aktivan && !trenutniTerminal.isIzlaznaKapijaSlobodna() && cekano < 8000) {
            Thread.sleep(150); cekano += 150;
        }
        if (!aktivan) return false;
        synchronized (trenutniTerminal) {
            if (!trenutniTerminal.isIzlaznaKapijaSlobodna()) return false;
            trenutniTerminal.napustiUlaznuKapiju(this);
            trenutniTerminal.udjiNaIzlaznuKapiju(this);
            red = RED_KAPIJA_IZLAZ;
        }
        osvjezi(); spavaj();
        return true;
    }

    /** Sa ulazne kapije fizicki udji u kanal na kolonu 0 (RED_DESNO). */
    private boolean udjiUKanalIzUlazneKapije() throws InterruptedException {
        int cekano = 0;
        while (aktivan && !trenutniTerminal.isKanalSlobodan(RED_DESNO, KOLONA_IZLAZA) && cekano < 8000) {
            Thread.sleep(150); cekano += 150;
        }
        if (!aktivan) return false;
        synchronized (trenutniTerminal) {
            if (!trenutniTerminal.isKanalSlobodan(RED_DESNO, KOLONA_IZLAZA)) return false;
            trenutniTerminal.napustiUlaznuKapiju(this);
            trenutniTerminal.setPozicijaKanala(RED_DESNO, KOLONA_IZLAZA, this);
            red = RED_DESNO; kolona = KOLONA_IZLAZA;
        }
        osvjezi(); spavaj();
        return true;
    }

    /** Iz kanala (bilo koje trenutne pozicije) na izlaznu kapiju ISTOG terminala. */
    private boolean izadjiIzKanalaNaIzlaznuKapiju() throws InterruptedException {
        int cekano = 0;
        while (aktivan && !trenutniTerminal.isIzlaznaKapijaSlobodna() && cekano < 8000) {
            Thread.sleep(150); cekano += 150;
        }
        if (!aktivan) return false;
        synchronized (trenutniTerminal) {
            if (!trenutniTerminal.isIzlaznaKapijaSlobodna()) return false;
            if (red == RED_LIJEVO || red == RED_DESNO) {
                if (trenutniTerminal.getPozicijaKanala(red, kolona) == this)
                    trenutniTerminal.setPozicijaKanala(red, kolona, null);
            }
            trenutniTerminal.udjiNaIzlaznuKapiju(this);
            red = RED_KAPIJA_IZLAZ;
        }
        osvjezi(); spavaj();
        return true;
    }

    private void napustiIzlaznuKapijuTrenutnog() {
        if (trenutniTerminal != null) trenutniTerminal.napustiIzlaznuKapiju(this);
    }

    /**
     * Puna pretraga dokova na TRENUTNOM terminalu (dvije prolaze kroz RED_DESNO
     * traku, kao i ranije). Pretpostavlja da je plovilo VEC na (RED_DESNO, 0).
     * Vraca true ako je privezano (ili preusmjereno na potjeru — u oba slucaja
     * fazaUlaz vise nema šta raditi), false ako nije nasao mjesto (pozicija
     * ostaje na trenutnom terminalu, na koloni 0 — pozivalac odlucuje dalje).
     */
    private boolean fazaUlazNaTerminalu() throws InterruptedException {
        int duzina = trenutniTerminal.getDuzinaKanala();
        boolean privezano = false;

        // PROLAZ 1: udesno, provjeravaj SAMO donje dokove.
        while (aktivan) {
            boolean lovljen = engine.jeLovljenoNaTerminalu(this, trenutniTerminal);
            if (lovljen) {
                if (engine.provjeriIPokreniPotjeruUlazna(this, trenutniTerminal, kolona)) {
                    stanje = Stanje.CEKA_UVIDAJ;
                    return true;
                }
            } else if (pokusajDonjiDok(kolona)) {
                privezano = true; break;
            }
            if (kolona >= duzina - 1) break;
            if (!korak(RED_DESNO, kolona + 1, true)) {
                if (!aktivan) break;
                Thread.sleep(150);
            }
        }

        // PROLAZ 2: ulijevo (ista traka), provjeravaj SAMO gornje dokove.
        if (!privezano && aktivan) {
            while (aktivan) {
                if (pokusajGornjiDok(kolona)) { privezano = true; break; }
                if (kolona <= 0) break;
                if (!korak(RED_DESNO, kolona - 1, false)) {
                    if (!aktivan) break;
                    Thread.sleep(150);
                }
            }
        }
        if (!privezano && aktivan) privezano = pokusajDonjiDok(kolona) || pokusajGornjiDok(kolona);

        if (privezano) {
            engine.ploviloPrivezano(this);
            stanje = Stanje.IZASLO;
            return true;
        }
        return false;
    }

    /** Nije naslo mjesto ni na jednom terminalu — plovilo ne moze uci u luku. */
    private void oznaciNijePrivezanoIIzadji() {
        if (trenutniTerminal != null) {
            if (red == RED_KAPIJA_ULAZ) {
                trenutniTerminal.napustiUlaznuKapiju(this);
            } else if (red == RED_KAPIJA_IZLAZ) {
                trenutniTerminal.napustiIzlaznuKapiju(this);
            } else {
                synchronized (trenutniTerminal) {
                    if (trenutniTerminal.getPozicijaKanala(red, kolona) == this)
                        trenutniTerminal.setPozicijaKanala(red, kolona, null);
                }
            }
        }
        engine.ploviloNijePrivezano(this);
        osvjezi();
        stanje = Stanje.IZASLO;
    }

    // ================================================================
    // FAZA: DOLAZAK NA SUDAR (teleport na ulaz ciljnog terminala + kretanje)
    // ================================================================

    private void fazaDolazakNaSudar() throws InterruptedException {
        if (trenutniTerminal == null) trenutniTerminal = ciljniTerminal; // sigurnosni fallback

        if (trenutniTerminal == ciljniTerminal) {
            // ISTI terminal kao sudar: udji direktno na SVOJU kolonu u kanalu
            // (ne ide se na ulaznu liniju — vec smo tu, samo se odvezujemo).
            int[] pos = getPozicijaDoka(trenutniTerminal);
            if (pos != null) {
                int dockRed = pos[0], mojaKolona = pos[1];
                synchronized (trenutniTerminal) {
                    if (dockRed == RED_GORNJI_DOK) ukloniSaDoka(RED_GORNJI_DOK, mojaKolona);
                    else ukloniSaDoka(RED_DONJI_DOK, mojaKolona);
                }
                if (!udjiNaKolonu(mojaKolona)) { zavrsiDolazakBezMjesta(); return; }
            }
            // ako je vec u kanalu (preusmjereno dok se kretalo po istom terminalu)
            // ostaje gdje jeste — nastavljamo direktno ka koloni sudara ispod.
        } else {
            // DRUGI terminal: prvo do granice (izlazne linije) SVOG terminala...
            if (!doGraniceSvogTerminala()) { zavrsiDolazakBezMjesta(); return; }
            // ...pa tranzit kroz lanac terminala do ULAZA terminala sudara.
            while (trenutniTerminal.getRedniBroj() != ciljniTerminal.getRedniBroj()) {
                boolean kaMoru = trenutniTerminal.getRedniBroj() > ciljniTerminal.getRedniBroj();
                ukloniSeSaTrenutnePozicije();
                Terminal sljedeci = engine.getLuka().getTerminal(
                        kaMoru ? trenutniTerminal.getRedniBroj() - 2
                                : trenutniTerminal.getRedniBroj());
                if (!dostaviNaTerm(sljedeci)) { zavrsiDolazakBezMjesta(); return; }
                if (trenutniTerminal.getRedniBroj() != ciljniTerminal.getRedniBroj()) {
                    if (!predjiNaLijevuTraku(KOLONA_IZLAZA)) { zavrsiDolazakBezMjesta(); return; }
                }
            }
            // stigli smo na ULAZNU liniju (RED_DESNO,0) terminala sudara
        }

        int cilj = nadjiSlobodnuKolonuBlizuSudara();
        if (cilj < 0) { zavrsiDolazakBezMjesta(); return; }
        int smjer = (cilj > kolona) ? 1 : -1;
        while (aktivan && kolona != cilj) {
            if (!korakBezSudara(RED_DESNO, kolona + smjer)) break;
        }
        stigaoNaSudar = true;
        LoggerUtil.logInfo("[UVIDAJ] " + naziv + " stiglo na kolonu " + kolona
                + " terminala " + trenutniTerminal.getRedniBroj());
        stanje = Stanje.CEKA_UVIDAJ;
    }

    /** Ulazak na kanal sa doka na tacno zadatoj koloni (isto kao fazaIzlaz korak 1). */
    private boolean udjiNaKolonu(int kol) throws InterruptedException {
        if (!cekajPa(RED_DESNO, kol)) return false;
        synchronized (trenutniTerminal) {
            if (!trenutniTerminal.isKanalSlobodan(RED_DESNO, kol)) return false;
            trenutniTerminal.setPozicijaKanala(RED_DESNO, kol, this);
            red = RED_DESNO; kolona = kol;
        }
        osvjezi(); spavaj();
        return true;
    }

    /**
     * Sa TRENUTNE pozicije (dok ili kanal) na SVOM terminalu dovodi plovilo do
     * izlazne linije (RED_LIJEVO, kolona 0) — koristeci drzavnu skracenicu.
     */
    private boolean doGraniceSvogTerminala() throws InterruptedException {
        int[] pos = getPozicijaDoka(trenutniTerminal);
        if (pos != null) {
            int dockRed = pos[0], mojaKolona = pos[1];
            synchronized (trenutniTerminal) {
                if (dockRed == RED_GORNJI_DOK) ukloniSaDoka(RED_GORNJI_DOK, mojaKolona);
                else ukloniSaDoka(RED_DONJI_DOK, mojaKolona);
            }
            if (!udjiNaKolonu(mojaKolona)) return false;
        }
        if (red == RED_DESNO) {
            if (!predjiNaLijevuTraku(kolona)) return false; // skracenica na istoj koloni
        }
        while (aktivan && kolona > KOLONA_IZLAZA) {
            if (!korakBezSudara(RED_LIJEVO, kolona - 1)) Thread.sleep(150);
        }
        return kolona == KOLONA_IZLAZA && red == RED_LIJEVO;
    }

    private void zavrsiDolazakBezMjesta() {
        LoggerUtil.logInfo("[UVIDAJ] " + naziv + " nije moglo stici do mjesta sudara — odustajem.");
        stigaoNaSudar = true;
        stanje = Stanje.CEKA_UVIDAJ;
    }

    /** Uklanja plovilo sa doka ili kanala u njegovom trenutnom terminalu. */
    private void ukloniSeSaTrenutnePozicije() {
        if (trenutniTerminal == null) return;
        synchronized (trenutniTerminal) {
            if (red == RED_GORNJI_DOK || red == RED_DONJI_DOK) {
                ukloniSaDoka(red, kolona);
            } else if (red == RED_LIJEVO || red == RED_DESNO) {
                if (trenutniTerminal.getPozicijaKanala(red, kolona) == this)
                    trenutniTerminal.setPozicijaKanala(red, kolona, null);
            }
        }
        osvjezi();
    }


    /** dostavlja plovilo na (RED_DESNO, kolona 0) ciljni terminal */
    private boolean dostaviNaTerm(Terminal terminal) throws InterruptedException {
        int cekano = 0;
        while (aktivan && cekano < 8000) {
            synchronized (terminal) {
                if (terminal.isKanalSlobodan(RED_DESNO, KOLONA_IZLAZA)) {
                    terminal.setPozicijaKanala(RED_DESNO, KOLONA_IZLAZA, this);
                    trenutniTerminal = terminal;
                    red = RED_DESNO; kolona = KOLONA_IZLAZA;
                    osvjezi(); spavaj();
                    LoggerUtil.logInfo("[UVIDAJ] " + naziv + " teleportovan na ulaz terminala "
                            + terminal.getRedniBroj());
                    return true;
                }
            }
            Thread.sleep(150);
            cekano += 150;
        }
        return false;
    }


    private int nadjiSlobodnuKolonuBlizuSudara() {
        int duzina = trenutniTerminal.getDuzinaKanala();
        final int MAX = 5;
        for (int off = 1; off <= MAX; off++) {
            int iza = kolonaCilja - off;
            if (iza >= 0 && trenutniTerminal.isKanalSlobodan(RED_DESNO, iza)) return iza;
            int ispred = kolonaCilja + off;
            if (ispred < duzina && trenutniTerminal.isKanalSlobodan(RED_DESNO, ispred)) return ispred;
        }
        return -1;
    }

    /**
     * Normalni korak sa provjerom sudara i  preticanjem.
     * Vraća true ako se pomjerio, false ako nije (zauzeto / prekinut).
     */
    private boolean korak(int ciljRed, int ciljKolona, boolean pozitivno)
            throws InterruptedException {
        if (!aktivan) return false;

        cekajDokPauza();
        if (!aktivan) return false;

        Plovilo ispred = trenutniTerminal.getPozicijaKanala(ciljRed, ciljKolona);
        if (ispred != null) {

            if (getPrioritet() > 0 && ciljRed == RED_DESNO
                    && mozePreticati(ciljRed, pozitivno, ispred)) {
                return izvrsiPreticanje(ciljRed, ispred, pozitivno);
            }
            return false; // zauzeto — pozivalac odlučuje šta dalje
        }

        // Provjera mimoilaženja (moguć sudar) prije pomaka
        if (provjeriMimoilazenje(ciljRed, ciljKolona)) {
            // sudar prijavljen; stani i čekaj
            cekajDokPauza();
            return false;
        }
        if (!aktivan) return false;

        return premjesti(ciljRed, ciljKolona);
    }

    /**
     * Korak bez mogućnosti izazivanja sudara (za dolazak na sudar).
     * Ako je zauzeto, državno pokušava preticanje; inače kratko čeka.
     */
    private boolean korakBezSudara(int ciljRed, int ciljKolona)
            throws InterruptedException {
        if (!aktivan) return false;

        Plovilo ispred = trenutniTerminal.getPozicijaKanala(ciljRed, ciljKolona);
        if (ispred != null) {
            boolean pozitivno = ciljKolona > kolona;
            if (mozePreticati(ciljRed, pozitivno, ispred)) {
                boolean ok = izvrsiPreticanje(ciljRed, ispred, pozitivno);
                if (!ok) Thread.sleep(150);
                return ok;
            }
            Thread.sleep(200);
            return false;
        }
        return premjesti(ciljRed, ciljKolona);
    }

    /** Atomski pomak na (ciljRed, ciljKolona) ako je slobodno. */
    private boolean premjesti(int ciljRed, int ciljKolona) throws InterruptedException {
        synchronized (trenutniTerminal) {
            Plovilo na = trenutniTerminal.getPozicijaKanala(ciljRed, ciljKolona);
            if (na != null && na != this) return false;
            if (trenutniTerminal.getPozicijaKanala(red, kolona) == this)
                trenutniTerminal.setPozicijaKanala(red, kolona, null);
            trenutniTerminal.setPozicijaKanala(ciljRed, ciljKolona, this);
            red = ciljRed; kolona = ciljKolona;
        }
        osvjezi(); spavaj();
        return true;
    }


    private boolean mozePreticati(int nasRed, boolean pozitivno, Plovilo ispred) {
        int suprotni = (nasRed == RED_DESNO) ? RED_LIJEVO : RED_DESNO;
        int smjer    = pozitivno ? 1 : -1;


        int slobodnih = 0;
        for (int k = kolona; k >= 0 && k < trenutniTerminal.getDuzinaKanala(); k += smjer) {
            if (trenutniTerminal.isKanalSlobodan(suprotni, k)) slobodnih++;
            else break;
            if (slobodnih >= SLOBODNIH_ZA_PRETICANJE) return true;
        }
        return false;
    }

    /**
     * Preteci: pređi u suprotnu traku na trenutnoj koloni, kreći se dok
     * ne prođemo pretečenog, pa se vrati u originalnu traku.
     */
    private boolean izvrsiPreticanje(int origRed, Plovilo pretečeni, boolean pozitivno)
            throws InterruptedException {
        int suprotni = (origRed == RED_DESNO) ? RED_LIJEVO : RED_DESNO;
        int smjer    = pozitivno ? 1 : -1;
        int duzina   = trenutniTerminal.getDuzinaKanala();
        int kolonaPreticeni = pretečeni.getKolona();

        pretečeni.postaviPauzaPreticanje(true);
        LoggerUtil.logInfo("[PRETICANJE] " + naziv + " pretiče " + pretečeni.getNaziv());
        try {
            // 1. Pređi u suprotnu traku na trenutnoj koloni
            synchronized (trenutniTerminal) {
                if (!trenutniTerminal.isKanalSlobodan(suprotni, kolona)) return false;
                if (trenutniTerminal.getPozicijaKanala(origRed, kolona) == this)
                    trenutniTerminal.setPozicijaKanala(origRed, kolona, null);
                trenutniTerminal.setPozicijaKanala(suprotni, kolona, this);
                red = suprotni;
            }
            osvjezi(); spavaj();

            // 2. Kreći se u suprotnoj traci dok ne prođemo pretečenog (za jedno polje)
            while (aktivan) {
                int sljed = kolona + smjer;
                if (sljed < 0 || sljed >= duzina) break;
                boolean prosli = pozitivno ? (kolona > kolonaPreticeni)
                        : (kolona < kolonaPreticeni);
                if (prosli) break;
                if (!trenutniTerminal.isKanalSlobodan(suprotni, sljed)) break;
                synchronized (trenutniTerminal) {
                    if (!trenutniTerminal.isKanalSlobodan(suprotni, sljed)) break;
                    trenutniTerminal.setPozicijaKanala(suprotni, kolona, null);
                    trenutniTerminal.setPozicijaKanala(suprotni, sljed, this);
                    kolona = sljed;
                }
                osvjezi(); spavaj();
            }

            // 3. Vrati se u originalnu traku (OBAVEZNO — inače se pozicija i
            //    očekivana traka raziđu i fazaIzlaz se vrti u prazno).
            int cekano = 0;
            while (aktivan) {
                synchronized (trenutniTerminal) {
                    if (trenutniTerminal.isKanalSlobodan(origRed, kolona)) {
                        trenutniTerminal.setPozicijaKanala(suprotni, kolona, null);
                        trenutniTerminal.setPozicijaKanala(origRed, kolona, this);
                        red = origRed;
                        osvjezi();
                        return true;
                    }
                }
                // Originalna traka na ovoj koloni zauzeta — sačekaj kratko pa probaj.
                Thread.sleep(150);
                cekano += 150;
                if (cekano >= 3000) {
                    // Ne možemo se vratiti na originalnu traku. Da izbjegnemo
                    // nekonzistentno stanje (pozicija na jednoj traci, faza očekuje
                    // drugu), signaliziramo neuspjeh — pozivalac (fazaIzlaz) će nakon
                    // dovoljno neuspjeha forsirati izlaz. 'red' ostaje stvaran (suprotni).
                    red = suprotni;
                    osvjezi();
                    return false;
                }
            }
            return aktivan;
        } finally {
            pretečeni.postaviPauzaPreticanje(false);
        }
    }


    /**
     * Provjerava da li dolazi do sudara sa plovilom u suprotnoj traci na ciljnoj koloni.
     * @return true ako je sudar prijavljen (plovilo treba stati)
     */
    private boolean provjeriMimoilazenje(int nasRed, int ciljKolona) {
        if (getPrioritet() > 0) return false;
        if (stanje != Stanje.IZLAZ) return false; // samo tokom normalnog izlaza
        if (imuniNaSudar) return false;           // sudionik prethodnog uviđaja ne izaziva novi sudar

        int suprotna = (nasRed == RED_DESNO) ? RED_LIJEVO : RED_DESNO;
        Plovilo drugo = trenutniTerminal.getPozicijaKanala(suprotna, ciljKolona);
        if (drugo == null || drugo == this) return false;
        if (drugo.getPrioritet() > 0) return false;
        if (drugo.getStanje() != Stanje.IZLAZ) return false;
        if (drugo.imuniNaSudar) return false;     // ni suprotno ne smije biti imuno

        if (RANDOM.nextDouble() >= VJEROVATNOCA_SUDARA) return false;

        int idx = trenutniTerminal.getRedniBroj() - 1;
        if (engine.isTerminalBlokiran(idx)) return false;

        return engine.prijaviSudar(this, drugo, trenutniTerminal);
    }


    private synchronized void izadjiIzLuke() {
        if (stanje == Stanje.IZASLO) return;
        stanje = Stanje.IZASLO;
        aktivan = false;

        if (trenutniTerminal != null) {
            if (red == RED_KAPIJA_ULAZ) {
                trenutniTerminal.napustiUlaznuKapiju(this);
            } else if (red == RED_KAPIJA_IZLAZ) {
                trenutniTerminal.napustiIzlaznuKapiju(this);
            } else {
                synchronized (trenutniTerminal) {
                    if (trenutniTerminal.getPozicijaKanala(red, kolona) == this)
                        trenutniTerminal.setPozicijaKanala(red, kolona, null);
                }
            }
        }
        engine.ploviloIzaslo(this);
        engine.evidentirIzlazak(this);
        osvjezi();
        LoggerUtil.logInfo("[DOGADJAJ] " + naziv + " napusta terminal.");
    }

    /** Fallback kad plovilo ne može fizički do izlaza (pun terminal): ukloni ga svejedno. */
    private void forsirajIzlaz() {
        LoggerUtil.logInfo("[FORCE-EXIT] " + naziv + " zaglavljeno na ["
                + red + "," + kolona + "] — forsiran izlaz.");
        izadjiIzLuke();
    }


    /** Blokira dok traje pauza zbog sudara (uviđaj) ILI globalna pauza simulacije. */
    private void cekajDokPauza() throws InterruptedException {
        int cekano = 0;
        while ((pauziranZbogSudara || pauziranZaPreticanje) && aktivan && cekano < 30_000) {
            Thread.sleep(100);
            cekano += 100;
        }
        // Globalna pauza (korisnik unosi plovilo) — vrijeme se NE broji u rok izlaska.
        if (engine != null && engine.isGlobalnoPauzirano()) {
            long prije = System.currentTimeMillis();
            while (engine.isGlobalnoPauzirano() && aktivan) {
                Thread.sleep(100);
            }
            rokIzlaska += System.currentTimeMillis() - prije; // pomjeri rok za trajanje pauze
        }
    }

    private void cekajMalo() throws InterruptedException {
        // Državno plovilo čeka da uviđaj završi. Kad ga engine prebaci u IZLAZ
        // (nastaviKaIzlazu), petlja u petljaKretanja automatski prelazi na fazaIzlaz.
        Thread.sleep(150);
    }

    /** Čeka da polje postane slobodno (max ~8s). Vraća false ako nije uspjelo. */
    private boolean cekajPa(int r, int k) throws InterruptedException {
        int cekano = 0;
        while (aktivan && !trenutniTerminal.isKanalSlobodan(r, k)) {
            // Globalna pauza se ne broji u timeout čekanja.
            while (engine != null && engine.isGlobalnoPauzirano() && aktivan) Thread.sleep(100);
            Thread.sleep(100);
            cekano += 100;
            if (cekano >= 8000) return false;
        }
        return aktivan;
    }

    /** Kraće čekanje da se polje ispred oslobodi (max ~6s). */
    private boolean cekajOslobadjanje(int r, int k) throws InterruptedException {
        int cekano = 0;
        while (aktivan && !trenutniTerminal.isKanalSlobodan(r, k)) {
            while (engine != null && engine.isGlobalnoPauzirano() && aktivan) Thread.sleep(100);
            Thread.sleep(150);
            cekano += 150;
            if (cekano >= 6000) return false;
        }
        return aktivan;
    }

    // ================================================================
    // POMOĆNE
    // ================================================================

    private void osvjezi() {
        if (engine != null) engine.osvjeziPrikaz();
    }

    /**
     * Vraća true samo ako je apsolutni rok izlaska stvarno istekao.
     * Ako je simulacija globalno pauzirana, prvo sačeka pauzu i pomjeri rok
     * (pauza se ne broji), pa tek onda provjeri — tako plovilo ne force-exita
     * samo zato što je korisnik dugo unosio novo plovilo.
     */
    private boolean rokIstekao() throws InterruptedException {
        if (engine != null && engine.isGlobalnoPauzirano()) {
            long prije = System.currentTimeMillis();
            while (engine.isGlobalnoPauzirano() && aktivan) Thread.sleep(100);
            rokIzlaska += System.currentTimeMillis() - prije;
        }
        return System.currentTimeMillis() > rokIzlaska;
    }

    private void spavaj() throws InterruptedException {
        // Ako je globalna pauza aktivna, sačekaj je prije spavanja (rok se pomjera u cekajDokPauza).
        if (engine != null && engine.isGlobalnoPauzirano()) cekajDokPauza();
        long ms = (long) (600.0 / brzina);
        Thread.sleep(Math.max(200, Math.min(ms, 1200)));
    }

    private boolean pokusajDonjiDok(int k) {
        synchronized (trenutniTerminal) {
            if (k < trenutniTerminal.getDonjiDokovi().size()) {
                Dok d = trenutniTerminal.getDonjiDokovi().get(k);
                if (d.isSlobodan()) {
                    d.privaziPlovilo(this);
                    if (trenutniTerminal.getPozicijaKanala(red, k) == this)
                        trenutniTerminal.setPozicijaKanala(red, k, null);
                    aktivan = false;
                    osvjezi();
                    LoggerUtil.logInfo("[DOGADJAJ] " + naziv + " privezano na donji dok " + k);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean pokusajGornjiDok(int k) {
        synchronized (trenutniTerminal) {
            if (k < trenutniTerminal.getGornjiDokovi().size()) {
                Dok g = trenutniTerminal.getGornjiDokovi().get(k);
                if (g.isSlobodan()) {
                    g.privaziPlovilo(this);
                    if (trenutniTerminal.getPozicijaKanala(red, k) == this)
                        trenutniTerminal.setPozicijaKanala(red, k, null);
                    aktivan = false;
                    osvjezi();
                    LoggerUtil.logInfo("[DOGADJAJ] " + naziv + " privezano na gornji dok " + k);
                    return true;
                }
            }
        }
        return false;
    }

    private int[] pronadiPozicijuNaTerminalu(Terminal terminal) {
        for (int r = 1; r <= 2; r++)
            for (int k = 0; k < terminal.getDuzinaKanala(); k++)
                if (terminal.getPozicijaKanala(r, k) == this)
                    return new int[]{r, k};
        for (int k = 0; k < terminal.getGornjiDokovi().size(); k++) {
            Dok d = terminal.getGornjiDokovi().get(k);
            if (!d.isSlobodan() && d.getPlovilo() == this) return new int[]{RED_GORNJI_DOK, k};
        }
        for (int k = 0; k < terminal.getDonjiDokovi().size(); k++) {
            Dok d = terminal.getDonjiDokovi().get(k);
            if (!d.isSlobodan() && d.getPlovilo() == this) return new int[]{RED_DONJI_DOK, k};
        }
        return null;
    }

    private void ukloniSaDoka(int dockRed, int k) {
        if (dockRed == RED_GORNJI_DOK && k < trenutniTerminal.getGornjiDokovi().size()) {
            Dok d = trenutniTerminal.getGornjiDokovi().get(k);
            if (!d.isSlobodan() && d.getPlovilo() == this) d.odvaziPlovilo();
        } else if (dockRed == RED_DONJI_DOK && k < trenutniTerminal.getDonjiDokovi().size()) {
            Dok d = trenutniTerminal.getDonjiDokovi().get(k);
            if (!d.isSlobodan() && d.getPlovilo() == this) d.odvaziPlovilo();
        }
    }


    /** Pauziraj plovilo dok traje uviđaj na njegovom terminalu. */
    public void pauzirajZbogSudara() { this.pauziranZbogSudara = true; }

    /** Oslobodi plovilo nakon uviđaja da nastavi ka izlazu. */
    public void oslobodiNakonUvidaja() { this.pauziranZbogSudara = false; }

    /**
     * Preusmjerava ŽIVU nit državnog plovila ka sudaru na ciljnom terminalu.
     * Živa run() petlja vidi promjenu stanja i prelazi na fazuDolazakNaSudar.
     */
    public void preusmjeriNaSudar(SimulacijaEngine engine,
                                  Terminal ciljniTerminal, int kolonaSudara) {
        this.engine           = engine;
        this.ciljniTerminal   = ciljniTerminal;
        this.kolonaCilja      = kolonaSudara;
        this.stigaoNaSudar    = false;
        this.pauziranZbogSudara   = false;
        this.pauziranZaPreticanje = false;
        this.stanje = Stanje.DOLAZAK_NA_SUDAR;
    }

    /** Trajno zaustavi nit plovila (kraj simulacije). */
    public void zaustavi() {
        this.aktivan = false;
        interrupt();
    }

    /**
     * Forsira izlazak plovila izvana (poziva nadzorna nit engine-a kad detektuje
     * da je plovilo zaglavljeno). Uklanja plovilo sa pozicije, gasi nit i
     * evidentira izlazak preko engine-a.
     */
    public void forsirajIzlazIzvana() {
        if (stanje == Stanje.IZASLO) return;
        forsirajIzlaz();
        interrupt();
    }

    void postaviPauzaPreticanje(boolean v) { this.pauziranZaPreticanje = v; }

    // ================================================================
    // GETTERI / SETTERI
    // ================================================================

    public boolean jeStigaoNaSudar()        { return stigaoNaSudar; }
    public Stanje  getStanje()              { return stanje; }
    public boolean isAktivan()              { return aktivan; }
    public int     getRed()                 { return red; }
    public int     getKolona()              { return kolona; }
    public Terminal getTrenutniTerminal()   { return trenutniTerminal; }
    public void    postaviEngine(SimulacijaEngine e)  { this.engine = e; }
    public void    postaviTerminal(Terminal t)        { this.trenutniTerminal = t; }

    public String  getNaziv()               { return naziv; }
    public void    setNaziv(String n)       { this.naziv = n; }
    public String  getImoBroj()             { return imoBroj; }
    public void    setImoBroj(String i)     { this.imoBroj = i; }
    public int     getBrojMotora()          { return brojMotora; }
    public void    setBrojMotora(int n)     { this.brojMotora = n; }
    public String  getRegistarskiBroj()     { return registarskiBroj; }
    public void    setRegistarskiBroj(String r) { this.registarskiBroj = r; }
    public String  getPutanjaFotografije()  { return putanjaFotografije; }
    public void    setPutanjaFotografije(String p) { this.putanjaFotografije = p; }
    public double  getBrzina()              { return brzina; }
    public void    setBrzina(double b)      { this.brzina = b; }

    public String getOznakaZaPrikaz() {
        String o = getOznaka();
        if (isRotacijaUpaljena()) o += "R";
        return o;
    }

    @Override public String toString() {
        return naziv + " [" + imoBroj + "] v=" + String.format("%.2f", brzina);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Plovilo p)) return false;
        return imoBroj != null && imoBroj.equals(p.imoBroj);
    }

    @Override public int hashCode() {
        return imoBroj != null ? imoBroj.hashCode() : 0;
    }
}