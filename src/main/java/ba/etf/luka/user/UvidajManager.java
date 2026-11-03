package ba.etf.luka.user;

import ba.etf.luka.model.*;
import ba.etf.luka.model.drzavna.ObalskaStrazaKontejnerski;
import ba.etf.luka.model.interfaces.DrzavnoPlovilo;
import ba.etf.luka.model.interfaces.Proganjac;
import ba.etf.luka.util.BinarniFajlUtil;
import ba.etf.luka.util.LoggerUtil;
import javafx.application.Platform;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Upravlja uviđajima nakon sudara.
 */
public class UvidajManager {

    private final Luka luka;
    private final SimulacijaEngine engine;
    private final List<Uvidaj> evidencija = new CopyOnWriteArrayList<>();
    private final Set<String> uVidajuImo = ConcurrentHashMap.newKeySet();

    private Consumer<String> onDogadaj;
    private static final Random RANDOM = new Random();

    public UvidajManager(Luka luka, SimulacijaEngine engine) {
        this.luka   = luka;
        this.engine = engine;
        BinarniFajlUtil.inicijalizuj();
    }

    // ================================================================
    // OBRADA SUDARA
    // ================================================================

    public void obradiSudar(Plovilo a, Plovilo b, Terminal terminal, int kolonaSudara) {
        fireEvent("SUDAR na terminalu " + terminal.getRedniBroj()
                + " (kolona " + kolonaSudara + "): " + a.getNaziv() + " i " + b.getNaziv());

        uVidajuImo.add(a.getImoBroj());
        uVidajuImo.add(b.getImoBroj());

        // Pronađi i rezerviši državna plovila
        List<Plovilo> drzavna = engine.pronadjiDrzavnaPlovila(terminal, kolonaSudara);
        drzavna.removeIf(p -> uVidajuImo.contains(p.getImoBroj()));
        for (Plovilo dp : drzavna) uVidajuImo.add(dp.getImoBroj());

        if (drzavna.isEmpty()) {
            LoggerUtil.logInfo("[UVIDAJ] Nema dostupnih drzavnih plovila — uvidjaj bez dolaska.");
            izvrsiUvidaj(a, b, terminal, kolonaSudara, Collections.emptyList());
            return;
        }

        // Pošalji svako državno ka sudaru
        List<Plovilo> poslata = new ArrayList<>();
        for (Plovilo dp : drzavna) {
            if (engine.getIzasliImo().contains(dp.getImoBroj())) {
                uVidajuImo.remove(dp.getImoBroj());
                continue;
            }

            Thread.State st = dp.getState();

            if (st == Thread.State.TERMINATED) {

                uVidajuImo.remove(dp.getImoBroj());
                LoggerUtil.logInfo("[UVIDAJ] " + dp.getNaziv()
                        + " nit vec zavrsena — preskacem.");
                continue;
            }

            if (dp instanceof DrzavnoPlovilo dpi) dpi.setRotacija(true);
            poslata.add(dp);
            if (!engine.getAktivniThreadovi().contains(dp)) engine.getAktivniThreadovi().add(dp);

            if (st == Thread.State.NEW) {

                dp.pripremiDolazakNaSudar(engine, terminal, kolonaSudara);
                dp.start();
            } else {

                dp.preusmjeriNaSudar(engine, terminal, kolonaSudara);
            }

            fireEvent(dp.getNaziv() + " krece ka sudaru na T" + terminal.getRedniBroj());
        }

        cekajDaStignu(poslata);
        izvrsiUvidaj(a, b, terminal, kolonaSudara, poslata);
    }

    /** Čeka da sva državna stignu (stigaoNaSudar) ili do timeout-a (20s). */
    private void cekajDaStignu(List<Plovilo> drzavna) {
        if (drzavna.isEmpty()) return;
        int cekano = 0, maxMs = 20_000;
        while (cekano < maxMs) {
            if (drzavna.stream().allMatch(Plovilo::jeStigaoNaSudar)) {
                LoggerUtil.logInfo("[UVIDAJ] Sva drzavna plovila stigla — pocinje uvidjaj.");
                return;
            }
            try { Thread.sleep(200); } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); return;
            }
            cekano += 200;
        }
        LoggerUtil.logInfo("[UVIDAJ] Timeout cekanja na drzavna plovila — nastavljam.");
    }

    // ================================================================
    // IZVRŠAVANJE UVIĐAJA
    // ================================================================

    private void izvrsiUvidaj(Plovilo a, Plovilo b, Terminal terminal,
                              int kolonaSudara, List<Plovilo> drzavna) {
        int trajanje = 3000 + RANDOM.nextInt(7001); // 3–10 s

        fireEvent("Uvidjaj u toku na terminalu " + terminal.getRedniBroj()
                + " (trajanje: " + (trajanje / 1000) + "s)");

        // Evidentiraj uviđaj u binarni fajl
        List<Plovilo> ucesnici = new ArrayList<>(List.of(a, b));
        ucesnici.addAll(drzavna);
        Uvidaj uvidaj = new Uvidaj(Uvidaj.TipUvidaja.SUDAR, terminal.getRedniBroj(), ucesnici);
        evidencija.add(uvidaj);
        BinarniFajlUtil.sacuvajUvidaj(uvidaj);

        try { Thread.sleep(trajanje); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        fireEvent("Uvidjaj zavrsen na terminalu " + terminal.getRedniBroj()
                + " — saobracaj se nastavlja.");

        uVidajuImo.remove(a.getImoBroj());
        uVidajuImo.remove(b.getImoBroj());
        for (Plovilo dp : drzavna) uVidajuImo.remove(dp.getImoBroj());

        engine.odblokirajTerminal(terminal, a, b, drzavna);
    }


    public void obradiPotjeru(Plovilo os, Plovilo trazeno, Terminal terminal) {
        if (os instanceof Proganjac osProganjac) {
            osProganjac.zavrsiPotjeru(trazeno.getImoBroj());
        }
        int trajanje = 3000 + RANDOM.nextInt(2001);
        fireEvent("POTJERA u toku na terminalu " + terminal.getRedniBroj()
                + " (" + (trajanje / 1000) + "s): " + os.getNaziv()
                + " prati " + trazeno.getNaziv() + " ka izlazu.");

        Uvidaj uvidaj = new Uvidaj(Uvidaj.TipUvidaja.POTJERA, terminal.getRedniBroj(), List.of(os, trazeno));
        evidencija.add(uvidaj);
        BinarniFajlUtil.sacuvajUvidaj(uvidaj);
        LoggerUtil.logInfo("[POTJERA] Evidentirana u binarni fajl: OS " + os.getNaziv()
                + " goni " + trazeno.getImoBroj() + " na T" + terminal.getRedniBroj());

        try { Thread.sleep(trajanje); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        fireEvent("Potjera zavrsena na terminalu " + terminal.getRedniBroj()
                + " — " + os.getNaziv() + " i " + trazeno.getNaziv() + " napuštaju luku.");
    }

    // ================================================================
    // POMOĆNE
    // ================================================================

    public void oslobodiPlovilo(String imoBroj) { uVidajuImo.remove(imoBroj); }
    public boolean jeUVidaju(String imoBroj)    { return uVidajuImo.contains(imoBroj); }

    private void fireEvent(String poruka) {
        LoggerUtil.logInfo(poruka);
        Platform.runLater(() -> { if (onDogadaj != null) onDogadaj.accept(poruka); });
    }

    public List<Uvidaj> getEvidencijaUvidaja() { return evidencija; }
    public void setOnDogadaj(Consumer<String> h) { this.onDogadaj = h; }
}