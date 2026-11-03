package ba.etf.luka.user;

import ba.etf.luka.model.*;
import ba.etf.luka.model.interfaces.DrzavnoPlovilo;
import ba.etf.luka.model.interfaces.Proganjac;
import ba.etf.luka.util.LoggerUtil;
import javafx.application.Platform;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 *  - pokretanju plovila ka izlazu (15% sa svakog terminala),
 *  - dodavanju novih plovila tokom simulacije,
 *  - obradi sudara (blokada terminala + uviđaj),
 *  - završetku simulacije.
 */
public class SimulacijaEngine {

    private final Luka luka;
    private final UvidajManager uvidajManager;

    private final List<Plovilo> aktivna         = new CopyOnWriteArrayList<>();
    private final Set<String>   izasliImo       = ConcurrentHashMap.newKeySet();
    private final Set<String>   uSudaruImo      = ConcurrentHashMap.newKeySet();

    private final AtomicInteger zaVezivanje     = new AtomicInteger(0);

    private final Set<String>   trebaIzaci      = ConcurrentHashMap.newKeySet();

    private final Map<Integer, Boolean> blokiraniTerminali = new ConcurrentHashMap<>();


    private static final int PAR_OP = 1;
    private final AtomicInteger aktivnihOperacija = new AtomicInteger(0);

    private volatile boolean zavrsena = false;
    private volatile boolean globalnoPauzirano = false;
    private volatile boolean kretanjePokrenuto = false;

    private Runnable         onOsvjeziUI;
    private Runnable         onZavrsena;
    private Consumer<String> onDogadaj;

    public SimulacijaEngine(Luka luka) {
        this.luka          = luka;
        this.uvidajManager = new UvidajManager(luka, this);
    }

    // ================================================================
    // POKRETANJE
    // ================================================================


    public void pripremiSimulaciju() {
        zavrsena = false;
        izasliImo.clear();
        uSudaruImo.clear();
        luka.getEvidencijaUlazaka().clear();
        osigurajDrzavnaPlovila();
        LoggerUtil.logInfo("Simulacija pripremljena — plovila na dokovima. Ceka pokretanje kretanja.");
    }

    /**
     * POKRETANJE KRETANJA — bira 15% običnih plovila sa svakog terminala i
     * pokreće ih ka izlazu, pokreće potjeru i nadzornu nit. Zove se na dugme
     * "Pokreni kretanje" nakon što je korisnik dodao željena plovila.
     */
    public void pokreniKretanje() {
        if (kretanjePokrenuto) return;
        kretanjePokrenuto = true;

        for (Terminal terminal : luka.getTerminali()) {
            for (Plovilo p : terminal.getSvaPlovilaD()) {
                if (!luka.getEvidencijaUlazaka().containsKey(p.getImoBroj())) {
                    evidentirUlazak(p);
                }
            }
        }
        for (Terminal terminal : luka.getTerminali()) {
            List<Plovilo> naDokovima = terminal.getSvaPlovilaD();
            if (naDokovima.isEmpty()) continue;

            List<Plovilo> obicna = new ArrayList<>();
            for (Plovilo p : naDokovima)
                if (!(p instanceof DrzavnoPlovilo)) obicna.add(p);
            if (obicna.isEmpty()) continue;

            int koliko = Math.max(1, (int) Math.ceil(obicna.size() * 0.15));
            koliko = Math.min(koliko, obicna.size());

            List<Plovilo> kandidati = new ArrayList<>(obicna);
            Collections.shuffle(kandidati);

            int pokrenuto = 0;
            for (Plovilo p : kandidati) {
                if (pokrenuto >= koliko) break;
                trebaIzaci.add(p.getImoBroj());
                if (!aktivna.contains(p)) aktivna.add(p);
                p.pripremiIzlaz(this, terminal);
                p.start();   // Plovilo JE nit — pokreće se jednom
                pokrenuto++;
                LoggerUtil.logInfo("Krenulo ka izlazu: " + p.getImoBroj()
                        + " T" + terminal.getRedniBroj());
            }

            // POTJERA: ako terminal ima obalsku stražu sa listom potjera i
            // plovilo za kojim je potjera raspisana, oboje se pokreću ka izlazu

            pokreniPotjeruAkoPostoji(terminal);
        }

        LoggerUtil.logInfo("Kretanje pokrenuto. Za izlaz: " + trebaIzaci.size());
        pokreniNadzor();
    }

    /**
     * Provjerava ima li na terminalu obalska straža sa raspisanom potjerom i
     * plovilo za kojim je potjera. Ako da, pokreće oboje ka izlazu (zadnje) i
     * startuje potjeru: traženo plovilo prati OS, kratki uviđaj (3–5s),
     * terminal ostaje prohodan (ne blokira se).
     */
    private void pokreniPotjeruAkoPostoji(Terminal terminal) {
        Plovilo os = null;
        Proganjac osProganjac = null;
        for (Plovilo p : terminal.getSvaPlovilaD()) {
            if (p instanceof Proganjac kandidat
                    && kandidat.getListaPotjera() != null
                    && !kandidat.getListaPotjera().isEmpty()) {
                os = p; osProganjac = kandidat; break;
            }
        }
        if (os == null) return;

        Plovilo trazeno = null;
        for (Plovilo p : terminal.getSvaPlovilaD()) {
            if (p == os) continue;
            if (osProganjac.isPotjeraNa(p.getImoBroj())) { trazeno = p; break; }
        }
        if (trazeno == null) return;

        LoggerUtil.logInfo("[POTJERA] Detektovano na T" + terminal.getRedniBroj()
                + ": OS " + os.getNaziv() + " goni " + trazeno.getNaziv()
                + " [" + trazeno.getImoBroj() + "]");
        fireEvent("POTJERA na terminalu " + terminal.getRedniBroj() + ": "
                + os.getNaziv() + " goni " + trazeno.getNaziv());

        ((DrzavnoPlovilo) os).setRotacija(true);

        final Plovilo fos = os;
        final Plovilo ftrazeno = trazeno;
        trebaIzaci.add(fos.getImoBroj());
        trebaIzaci.add(ftrazeno.getImoBroj());
        if (!aktivna.contains(fos)) aktivna.add(fos);
        if (!aktivna.contains(ftrazeno)) aktivna.add(ftrazeno);

        fos.pripremiIzlaz(this, terminal);
        fos.postaviBezSkracenice(true);
        fos.start();
        ftrazeno.pripremiIzlaz(this, terminal);
        ftrazeno.start();

        Thread uv = new Thread(() ->
                uvidajManager.obradiPotjeru(fos, ftrazeno, terminal),
                "Potjera-T" + terminal.getRedniBroj());
        uv.setDaemon(true);
        uv.start();
    }

    /**
     * Nadzorna nit (failsafe). Ako nijedan uviđaj nije u toku, a neko plovilo
     * je "zombi" (živa nit u stanju IZLAZ koja se nije pomjerila duže vrijeme),
     * forsira mu izlazak da simulacija ne bi visila zbog rijetkog međusobnog
     * blokiranja dva plovila u uskom kanalu.
     */
    private void pokreniNadzor() {
        Thread nadzor = new Thread(() -> {
            // imo -> [najboljaBlizinaIzlazu, brojCiklusaBezNapretka]
            Map<String, int[]> napredak = new HashMap<>();
            while (!zavrsena) {
                try { Thread.sleep(1500); } catch (InterruptedException e) { break; }

                boolean imaBlokada = blokiraniTerminali.values().stream().anyMatch(x -> x);
                if (imaBlokada) { napredak.clear(); continue; } // ne diramo tokom uviđaja

                // Ne diramo plovila ni tokom globalne pauze (korisnik unosi plovilo) —
                // ona stoje namjerno, nisu zombiji.
                if (globalnoPauzirano) { napredak.clear(); continue; }

                // Failsafe: plovilo je u trebaIzaci, nit mu je mrtva, a nije
                // evidentirano kao izašlo -> evidentiraj izlazak da brojač ne visi.
                for (Plovilo p : aktivna) {
                    if (p.getRed() == Plovilo.RED_KAPIJA_ULAZ || p.getRed() == Plovilo.RED_KAPIJA_IZLAZ) continue;
                    if (!trebaIzaci.contains(p.getImoBroj())) continue;
                    if (izasliImo.contains(p.getImoBroj())) continue;
                    if (!p.isAlive()) {
                        LoggerUtil.logInfo("[NADZOR] " + p.getNaziv()
                                + " nit mrtva a nije izaslo — evidentiram izlazak.");
                        forsirajEvidencijuIzlaska(p);
                    }
                }

                for (Plovilo p : aktivna) {
                    if (izasliImo.contains(p.getImoBroj())) continue;
                    if (!p.isAlive()) continue;
                    if (p.getStanje() != Plovilo.Stanje.IZLAZ) continue;

                    // "Blizina izlazu": na desnoj traci (red 2) plovilo je dalje;
                    // na lijevoj traci (red 1) što manja kolona to bliže izlazu (kolona 0).
                    // Manja vrijednost = bliže izlazu.
                    int blizina = (p.getRed() == Plovilo.RED_DESNO)
                            ? 1000 - p.getKolona()   // desna traka: veći put do izlaza
                            : p.getKolona();          // lijeva traka: kolona = udaljenost

                    int[] st = napredak.get(p.getImoBroj());
                    if (st == null) {
                        napredak.put(p.getImoBroj(), new int[]{blizina, 0});
                    } else if (blizina < st[0]) {
                        st[0] = blizina; // napredovao ka izlazu — resetuj
                        st[1] = 0;
                    } else {
                        st[1]++; // nije se približio izlazu ovaj ciklus
                        // ~6s bez stvarnog napretka ka izlazu => zombi
                        if (st[1] >= 4) {
                            LoggerUtil.logInfo("[NADZOR] " + p.getNaziv()
                                    + " ne napreduje ka izlazu [" + p.getRed() + ","
                                    + p.getKolona() + "] — forsiram izlaz.");
                            p.forsirajIzlazIzvana();
                            napredak.remove(p.getImoBroj());
                        }
                    }
                }
            }
        }, "Nadzor");
        nadzor.setDaemon(true);
        nadzor.start();
    }

    // ================================================================
    // DODAVANJE PLOVILA TOKOM SIMULACIJE
    // ================================================================

    public boolean dodajPloviloPotok(Plovilo p, int startTerminalIdx) {
        if (!luka.imaSlobiodnoMjesto()) return false;
        Terminal start = luka.getTerminal(startTerminalIdx);

        // Ako kretanje JOŠ nije pokrenuto, novo plovilo samo sjeda na dok
        // (miruje kao ostala) — pridružiće se kad korisnik pokrene kretanje.
        if (!kretanjePokrenuto) {
            Dok slobodan = start.getFirstSlobodanDonjiDok();
            if (slobodan == null) return false;   // nema slobodnog DONJEG doka
            slobodan.privaziPlovilo(p);
            LoggerUtil.logInfo("Novo plovilo na donji dok (prije kretanja): " + p.getImoBroj()
                    + " -> T" + start.getRedniBroj());
            if (onOsvjeziUI != null) Platform.runLater(onOsvjeziUI);
            return true;
        }
        // Kretanje je u toku — plovilo ulazi kroz kanal kao pokretna nit.
        zaVezivanje.incrementAndGet();
        if (!aktivna.contains(p)) aktivna.add(p);
        p.pripremiUlaz(this, start);
        p.start();
        LoggerUtil.logInfo("Novo plovilo dodato: " + p.getImoBroj()
                + " -> T" + start.getRedniBroj());
        return true;
    }


    // Sprječava dvostruko okidanje iste potjere (jedan target = jedan trigger)
    private final Set<String> uPotjeriImo = ConcurrentHashMap.newKeySet();

    public boolean jeLovljenoNaTerminalu(Plovilo x, Terminal terminal) {
        if (uPotjeriImo.contains(x.getImoBroj())) return false;
        for (Plovilo p : terminal.getSvaPlovilaD()) {
            if (p instanceof Proganjac kandidat && kandidat.isPotjeraNa(x.getImoBroj())) return true;
        }
        return false;
    }

    public boolean provjeriIPokreniPotjeruUlazna(Plovilo x, Terminal terminal, int trenutnaKolona) {
        if (uPotjeriImo.contains(x.getImoBroj())) return false;

        Plovilo os = null;
        Proganjac osProganjac = null;
        int osKolona = -1;
        for (Plovilo p : terminal.getSvaPlovilaD()) {
            if (p instanceof Proganjac kandidat && kandidat.isPotjeraNa(x.getImoBroj())) {
                int[] pos = kandidat.getPozicijaDoka(terminal);
                if (pos != null) { os = p; osProganjac = kandidat; osKolona = pos[1]; break; }
            }
        }
        if (os == null) return false;
        if (trenutnaKolona < osKolona - 1) return false;
        if (!uPotjeriImo.add(x.getImoBroj())) return false;

        final Plovilo fos = os;
        final Proganjac fosProganjac = osProganjac;
        LoggerUtil.logInfo("[POTJERA] " + fos.getNaziv() + " detektuje "
                + x.getNaziv() + " [" + x.getImoBroj() + "] na T"
                + terminal.getRedniBroj() + " (kolona " + osKolona + ")");
        fireEvent("POTJERA: " + fos.getNaziv() + " detektuje " + x.getNaziv()
                + " na terminalu " + terminal.getRedniBroj());

        fosProganjac.zavrsiPotjeru(x.getImoBroj());
        ((DrzavnoPlovilo) fos).setRotacija(true);
        trebaIzaci.add(fos.getImoBroj());
        trebaIzaci.add(x.getImoBroj());
        if (!aktivna.contains(fos)) aktivna.add(fos);
        zaVezivanje.decrementAndGet();

        fos.pripremiIzlaz(this, terminal);
        fos.postaviBezSkracenice(true);
        fos.pauzirajZbogSudara();
        fos.start();

        Thread uv = new Thread(() -> {
            long dl = System.currentTimeMillis() + 5000;
            while (fos.getRed() != Plovilo.RED_DESNO && System.currentTimeMillis() < dl) {
                try { Thread.sleep(50); } catch (InterruptedException ignored) { return; }
            }
            uvidajManager.obradiPotjeru(fos, x, terminal);
            fos.nastaviKaIzlazu();
            x.nastaviKaIzlazu();
        }, "Potjera-T" + terminal.getRedniBroj());
        uv.setDaemon(true);
        uv.start();
        return true;
    }

    // ================================================================
    // SUDAR
    // ================================================================

    /**
     * Prijava sudara. Vraća true ako je sudar PRIHVAĆEN (terminal se blokira i
     * kreće uviđaj), false ako je odbijen ( već blokiran).
     */
    /** Stvarna kolona za racunanje udaljenosti — koristi poziciju doka ako
     * plovilo jos nikad nije pokrenuto (transient 'kolona' je na default 0). */
    private int stvarnaKolonaZaUdaljenost(Plovilo p, Terminal t) {
        if (p.getStanje() == Plovilo.Stanje.IZLAZ) return p.getKolona();
        int[] pos = p.getPozicijaDoka(t);
        return pos != null ? pos[1] : p.getKolona();
    }
    public synchronized boolean prijaviSudar(Plovilo a, Plovilo b, Terminal terminal) {
        int idx = terminal.getRedniBroj() - 1;

        if (blokiraniTerminali.getOrDefault(idx, false)) return false;
        if (uSudaruImo.contains(a.getImoBroj()) || uSudaruImo.contains(b.getImoBroj()))
            return false;

        if (aktivnihOperacija.get() >= PAR_OP) {
            return false;
        }
        aktivnihOperacija.incrementAndGet();

        uSudaruImo.add(a.getImoBroj());
        uSudaruImo.add(b.getImoBroj());
        blokiraniTerminali.put(idx, true);

        int kolonaSudara = b.getKolona();

        LoggerUtil.logInfo("[SUDAR] T" + terminal.getRedniBroj() + " | "
                + a.getNaziv() + " i " + b.getNaziv() + " | kolona " + kolonaSudara);
        fireEvent("Sudar na terminalu " + terminal.getRedniBroj()
                + ": " + a.getNaziv() + " i " + b.getNaziv());

        pauzirajSvaUTerminalu(terminal);

        Thread uv = new Thread(() ->
                uvidajManager.obradiSudar(a, b, terminal, kolonaSudara),
                "Uvidaj-T" + terminal.getRedniBroj());
        uv.setDaemon(true);
        uv.start();
        return true;
    }

    /** Pauzira sva plovila koja se kreću u kanalu datog terminala. */
    private void pauzirajSvaUTerminalu(Terminal terminal) {
        for (Plovilo p : aktivna) {
            if (p.getTrenutniTerminal() != terminal) continue;
            int r = p.getRed();
            if (r == Plovilo.RED_LIJEVO || r == Plovilo.RED_DESNO) {
                p.pauzirajZbogSudara();
            }
        }
    }

    /**
     * Osigurava da luka ima bar po jedno državno plovilo svake vrste
     * (vatrogasci, obalska straža, carina). Ako neki tip nedostaje, dodaje ga
     * na prvi slobodan dok. Državna plovila mogu prelaziti između terminala
     * na uviđaj, pa je dovoljno da postoje bilo gdje u luci.
     */
    private void osigurajDrzavnaPlovila() {
        for (Terminal t : luka.getTerminali()) {
            List<Plovilo> zaUklanjanje = new ArrayList<>();
            for (Plovilo p : t.getSvaPlovilaD()) {
                if (p instanceof DrzavnoPlovilo && p.getState() == Thread.State.TERMINATED) {
                    zaUklanjanje.add(p);
                }
            }
            for (Plovilo p : zaUklanjanje) {
                ukloniSaDoka(t, p);
                LoggerUtil.logInfo("[DRZAVNA] Uklonjeno potroseno drzavno plovilo "
                        + p.getNaziv() + " (nit zavrsena) sa T" + t.getRedniBroj());
            }
        }

        boolean imaV = false, imaOS = false, imaCA = false;

        for (Terminal t : luka.getTerminali()) {
            List<Plovilo> svi = new ArrayList<>(t.getSvaPlovilaD());
            for (Plovilo p : aktivna)
                if (p.getTrenutniTerminal() == t && !svi.contains(p)) svi.add(p);
            for (Plovilo p : svi) {
                if (izasliImo.contains(p.getImoBroj())) continue;
                if (p.getState() == Thread.State.TERMINATED) continue;
                if (p instanceof DrzavnoPlovilo dp) {
                    switch (dp.getTip()) {
                        case VATROGASCI -> imaV = true;
                        case OBALSKA_STRAZA -> imaOS = true;
                        case CARINA -> imaCA = true;
                    }
                }
            }
        }

        LoggerUtil.logInfo("[DRZAVNA] Provjera pri pokretanju: imaV=" + imaV
                + " imaOS=" + imaOS + " imaCA=" + imaCA);

        if (!imaV)  dodajDrzavnoNaDok(new ba.etf.luka.model.drzavna.VatrogasniBrod(
                imeDrzavnog(), imoDrzavnog(), 1 + RND.nextInt(4), regDrzavnog(), null,
                50000 + RND.nextDouble() * 200000));
        if (!imaOS) dodajDrzavnoNaDok(new ba.etf.luka.model.drzavna.ObalskaStrazaTanker(
                imeDrzavnog(), imoDrzavnog(), 1 + RND.nextInt(4), regDrzavnog(), null,
                50000 + RND.nextDouble() * 200000, null));
        if (!imaCA) dodajDrzavnoNaDok(new ba.etf.luka.model.drzavna.CarinaTanker(
                imeDrzavnog(), imoDrzavnog(), 1 + RND.nextInt(4), regDrzavnog(), null,
                50000 + RND.nextDouble() * 200000));
    }
    // Imenovanje dodanih državnih plovila u istom formatu kao ostala plovila
    // (Plovilo-N / IMO....... / REG-N), da se uklapaju u flotu.
    private static final java.util.Random RND = new java.util.Random();
    private int brojacDrzavnih = 30;
    private String imeDrzavnog() { return "Plovilo-" + (++brojacDrzavnih); }
    private String regDrzavnog() { return "REG-" + brojacDrzavnih; }
    private String imoDrzavnog() { return "IMO" + (1000000 + RND.nextInt(9000000)); }

    /** Uklanja plovilo sa doka (gornji ili donji) na datom terminalu. */
    private void ukloniSaDoka(Terminal t, Plovilo p) {
        for (Dok d : t.getGornjiDokovi()) {
            if (!d.isSlobodan() && d.getPlovilo() == p) { d.odvaziPlovilo(); return; }
        }
        for (Dok d : t.getDonjiDokovi()) {
            if (!d.isSlobodan() && d.getPlovilo() == p) { d.odvaziPlovilo(); return; }
        }
    }

    /** Dodaje državno plovilo na prvi slobodan dok u luci. */
    private void dodajDrzavnoNaDok(Plovilo dp) {
        for (Terminal t : luka.getTerminali()) {
            Dok slobodan = t.getFirstSlobodanDok();
            if (slobodan != null) {
                slobodan.privaziPlovilo(dp);
                LoggerUtil.logInfo("[DRZAVNA] Dodano nedostajuce drzavno plovilo "
                        + dp.getNaziv() + " na T" + t.getRedniBroj());
                return;
            }
        }
        LoggerUtil.logInfo("[DRZAVNA] Nema slobodnog doka za " + dp.getNaziv());
    }

    // ================================================================
    // PRONALAZAK DRŽAVNIH PLOVILA
    // ================================================================

    /**
     * Traži po jedno državno plovilo svakog tipa (vatrogasci, obalska straža, carina).
     * Prioritet: plovila koja se VEĆ KREĆU u kanalu (bliža, brža), zatim iz dokova.
     * Redoslijed terminala: prvo sudarni, pa susjedni naizmjenično.
     */
    public List<Plovilo> pronadjiDrzavnaPlovila(Terminal sudarni, int kolonaSudara) {
        Plovilo najV = null, najOS = null, najCA = null;
        Terminal tV = null, tOS = null, tCA = null;   // NOVO
        int dV = Integer.MAX_VALUE, dOS = Integer.MAX_VALUE, dCA = Integer.MAX_VALUE;

        List<Terminal> terminaliRed = redoslijedTerminala(sudarni);
        int terminalTezina = 0;

        for (Terminal t : terminaliRed) {
            List<Plovilo> kandidati = new ArrayList<>();
            kandidati.addAll(drzavnaUKanalu(t));
            kandidati.addAll(drzavnaUDokovima(t));

            for (Plovilo p : kandidati) {
                if (izasliImo.contains(p.getImoBroj())) continue;
                if (uSudaruImo.contains(p.getImoBroj())) continue;
                if (p.getState() == Thread.State.TERMINATED) continue;

                int d = terminalTezina + Math.abs(stvarnaKolonaZaUdaljenost(p, t) - kolonaSudara); // IZMIJENJENO

                if (p instanceof DrzavnoPlovilo dp) {
                    switch (dp.getTip()) {
                        case VATROGASCI -> { if (d < dV) { dV = d; najV = p; } }
                        case OBALSKA_STRAZA -> { if (d < dOS) { dOS = d; najOS = p; } }
                        case CARINA -> { if (d < dCA) { dCA = d; najCA = p; } }
                    }
                }
            }
            terminalTezina += 100;
        }

        List<Plovilo> rezultat = new ArrayList<>();
// KLJUCNO: postavi trenutniTerminal PRIJE nego sto se plovilo pokrene —
// za docked plovilo (transient trenutniTerminal je null) ovo je jedini nacin
// da fazaDolazakNaSudar zna gdje se stvarno nalazi.
        if (najV  != null) { najV.postaviTerminal(tV);   rezultat.add(najV); }
        if (najOS != null) { najOS.postaviTerminal(tOS); rezultat.add(najOS); }
        if (najCA != null) { najCA.postaviTerminal(tCA); rezultat.add(najCA); }
        LoggerUtil.logInfo("[UVIDAJ] Sudar T" + sudarni.getRedniBroj()
                + " | pronadjena drzavna: " + rezultat.stream().map(Plovilo::getNaziv).toList()
                + " (V=" + (najV!=null) + " OS=" + (najOS!=null) + " CA=" + (najCA!=null) + ")");
        return rezultat;
    }

    private List<Terminal> redoslijedTerminala(Terminal sudarni) {
        List<Terminal> svi = luka.getTerminali();
        // Nađi indeks po REDNOM BROJU (ne po identitetu objekta) — nakon
        // deserijalizacije 'sudarni' može biti drugi objekat od onog u listi.
        int idx = -1;
        for (int i = 0; i < svi.size(); i++) {
            if (svi.get(i).getRedniBroj() == sudarni.getRedniBroj()) { idx = i; break; }
        }
        List<Terminal> red = new ArrayList<>();
        if (idx < 0) {
            // Sigurnosni fallback: ako ga nismo našli, pretraži SVE terminale.
            red.addAll(svi);
            return red;
        }
        red.add(svi.get(idx));
        for (int off = 1; off < svi.size(); off++) {
            if (idx - off >= 0)         red.add(svi.get(idx - off));
            if (idx + off < svi.size()) red.add(svi.get(idx + off));
        }
        return red;
    }

    private List<Plovilo> drzavnaUKanalu(Terminal t) {
        List<Plovilo> lista = new ArrayList<>();
        for (Plovilo p : aktivna) {
            if (!(p instanceof DrzavnoPlovilo)) continue;
            if (p.getTrenutniTerminal() != t) continue;
            if (!p.isAktivan()) continue;
            if (p.getStanje() != Plovilo.Stanje.IZLAZ) continue; // ne diramo one koji već idu na sudar
            int r = p.getRed();
            if (r != Plovilo.RED_LIJEVO && r != Plovilo.RED_DESNO) continue;
            lista.add(p);
        }
        return lista;
    }

    private List<Plovilo> drzavnaUDokovima(Terminal t) {
        List<Plovilo> lista = new ArrayList<>();
        for (Plovilo p : t.getSvaPlovilaD()) {
            if (!(p instanceof DrzavnoPlovilo)) continue;
            if (izasliImo.contains(p.getImoBroj())) continue;
            lista.add(p);
        }
        return lista;
    }



    // ================================================================
    // ODBLOKIRANJE NAKON UVIĐAJA
    // ================================================================

    /**
     * Poziva UvidajManager kad uviđaj završi.
     * Sva plovila (sudionici + državna + zaustavljena) nastavljaju ka izlazu.
     */
    public void odblokirajTerminal(Terminal terminal, Plovilo a, Plovilo b,
                                   List<Plovilo> drzavna) {
        int idx = terminal.getRedniBroj() - 1;
        blokiraniTerminali.put(idx, false);

        fireEvent("Uvidjaj zavrsen na terminalu " + terminal.getRedniBroj()
                + " — saobracaj nastavlja.");

        // KLJUČNO: registruj SVA državna plovila za izlaz. Set trebaIzaci je
        // idempotentan — ako je državno bilo izabrano iz kanala (već u setu),
        // dodavanje ne mijenja ništa; ako je iz doka (nije bilo), sad se dodaje.
        for (Plovilo dp : drzavna) {
            if (!izasliImo.contains(dp.getImoBroj())) {
                trebaIzaci.add(dp.getImoBroj());
            }
        }


        Thread pokretac = new Thread(() -> {
            // 1) državna
            for (Plovilo dp : drzavna) {
                pokreniKaIzlazuNakonSudara(dp, terminal);
                spavajTiho(700);
            }
            // 2) sudionici sudara
            pokreniKaIzlazuNakonSudara(a, terminal);
            spavajTiho(500);
            pokreniKaIzlazuNakonSudara(b, terminal);
            spavajTiho(500);
            // 3) ostala zaustavljena (obična) plovila — nastavljaju normalno
            for (Plovilo p : aktivna) {
                if (p == a || p == b || drzavna.contains(p)) continue;
                if (p.getTrenutniTerminal() == terminal) {
                    p.oslobodiNakonUvidaja();
                    spavajTiho(200);
                }
            }
        }, "PokretacNakonSudara-T" + terminal.getRedniBroj());
        pokretac.setDaemon(true);
        pokretac.start();

        uSudaruImo.remove(a.getImoBroj());
        uSudaruImo.remove(b.getImoBroj());
        for (Plovilo dp : drzavna) uSudaruImo.remove(dp.getImoBroj());

        // Oslobodi slot za novu operaciju
        aktivnihOperacija.decrementAndGet();

        osvjeziPrikaz();
        provjeriZavrsetak();
    }

    /** Pauza koja tiho proguta prekid. */
    private void spavajTiho(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /**
     * Prebacuje plovilo u stanje IZLAZ. Plovilo je nit (Thread) koja je već
     * pokrenuta i njena run() petlja čeka (u CEKA_UVIDAJ ili u pauzi zbog sudara).
     * Promjenom stanja ista nit automatski prelazi na fazu izlaza — bez restarta.
     * NAPOMENA: brojač zaIzlaz za državna je već inkrementiran u odblokirajTerminal.
     */
    private void pokreniKaIzlazuNakonSudara(Plovilo p, Terminal terminal) {
        if (izasliImo.contains(p.getImoBroj())) return;

        if (p.isAlive()) {
            // Nit je živa (čeka uviđaj ili pauzirana) — samo promijeni stanje.
            p.nastaviKaIzlazu();
        } else {
            // Rijedak slučaj: nit je već završila. Registruj izlazak da brojač ne zaglavi.
            LoggerUtil.logInfo("[SAFETY] " + p.getNaziv()
                    + " nit vise nije ziva nakon uvidjaja — forsiram evidenciju izlaska.");
            forsirajEvidencijuIzlaska(p);
        }
    }

    /** Uklanja plovilo sa pozicije i evidentira izlazak — sigurnosna mreža. */
    private void forsirajEvidencijuIzlaska(Plovilo p) {
        Terminal t = p.getTrenutniTerminal();
        if (t != null) {
            synchronized (t) {
                int r = p.getRed(), k = p.getKolona();
                if (r >= 0 && k >= 0 && t.getPozicijaKanala(r, k) == p) {
                    t.setPozicijaKanala(r, k, null);
                }
            }
        }
        ploviloIzaslo(p);
        evidentirIzlazak(p);
        osvjeziPrikaz();
    }

    // ================================================================
    // EVIDENCIJA / ZAVRŠETAK
    // ================================================================

    public void evidentirUlazak(Plovilo p) { luka.evidentirUlazak(p); }

    public void evidentirIzlazak(Plovilo p) {
        luka.evidentirIzlazak(p);
        double taksa = luka.naplaciTaksu(p);
        if (taksa > 0)
            LoggerUtil.logInfo("Taksa za " + p.getImoBroj() + ": " + taksa + " KM");
    }

    public synchronized void ploviloIzaslo(Plovilo p) {
        if (izasliImo.contains(p.getImoBroj())) return;
        izasliImo.add(p.getImoBroj());
        trebaIzaci.remove(p.getImoBroj());
        uvidajManager.oslobodiPlovilo(p.getImoBroj());
        LoggerUtil.logInfo("Plovilo izaslo: " + p.getImoBroj()
                + " | Preostalo za izlaz: " + trebaIzaci.size());
        provjeriZavrsetak();
    }

    public synchronized void ploviloPrivezano(Plovilo p) {
        int preostalo = zaVezivanje.decrementAndGet();
        LoggerUtil.logInfo("Privezano: " + p.getImoBroj() + " | Za vezivanje: " + preostalo);
        provjeriZavrsetak();
    }

    public synchronized void ploviloNijePrivezano(Plovilo p) {
        int preostalo = zaVezivanje.decrementAndGet();
        LoggerUtil.logInfo("Nije privezano: " + p.getImoBroj() + " | Za vezivanje: " + preostalo);
        provjeriZavrsetak();
    }

    private void provjeriZavrsetak() {
        if (zavrsena) return;
        boolean imaBlokada = blokiraniTerminali.values().stream().anyMatch(x -> x);
        if (trebaIzaci.isEmpty() && zaVezivanje.get() <= 0 && !imaBlokada) {
            zavrsena = true;
            zavrsiSimulaciju();
        }
    }

    private void zavrsiSimulaciju() {
        // Zaustavi sve niti plovila
        for (Plovilo p : aktivna) p.zaustavi();

        // Očisti kanale prije snimanja: plovila koja su izašla ili se kretala ne
        // smiju ostati kao "duhovi" u kanalu matrici. Na dokovima ostaju samo ona
        // koja se nikad nisu pomjerila (uredno privezana) — to je čisto stanje
        // koje se snima u luka.ser i ispravno učitava sljedeći put.
        luka.ocistiKanale();

        try {
            luka.serijalizuj();
            LoggerUtil.logInfo("Simulacija zavrsena — luka.ser azuriran.");
        } catch (IOException e) {
            LoggerUtil.logGreska("Greska pri serijalizaciji", e);
        }
        Platform.runLater(() -> { if (onZavrsena != null) onZavrsena.run(); });
    }

    // ================================================================
    // UI CALLBACKOVI
    // ================================================================

    public void osvjeziPrikaz() {
        Platform.runLater(() -> { if (onOsvjeziUI != null) onOsvjeziUI.run(); });
    }

    public void fireEvent(String poruka) {
        LoggerUtil.logInfo(poruka);
        Platform.runLater(() -> { if (onDogadaj != null) onDogadaj.accept(poruka); });
    }

    public void setOnDogadaj(Consumer<String> h) {
        this.onDogadaj = h;
        uvidajManager.setOnDogadaj(h);
    }

    // ================================================================
    // GETTERI
    // ================================================================

    public boolean isTerminalBlokiran(int idx) { return blokiraniTerminali.getOrDefault(idx, false); }

    /** Globalna pauza — sva plovila zastanu (npr. dok korisnik unosi novo plovilo). */
    public void pauzirajSimulaciju()  { globalnoPauzirano = true;  LoggerUtil.logInfo("[PAUZA] Simulacija pauzirana."); }
    public void nastaviSimulaciju()   { globalnoPauzirano = false; LoggerUtil.logInfo("[PAUZA] Simulacija nastavljena."); }
    public boolean isGlobalnoPauzirano() { return globalnoPauzirano; }
    public boolean isKretanjePokrenuto() { return kretanjePokrenuto; }
    public UvidajManager getUvidajManager()     { return uvidajManager; }
    public List<Plovilo> getAktivniThreadovi()  { return aktivna; }
    public Set<String>   getIzasliImo()         { return izasliImo; }
    public Luka          getLuka()              { return luka; }
    public void setOnOsvjeziUI(Runnable r)      { this.onOsvjeziUI = r; }
    public void setOnSimulacijaZavrsena(Runnable r) { this.onZavrsena = r; }
}