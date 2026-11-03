package ba.etf.luka.user.controller;

import ba.etf.luka.admin.AdminAplikacija;
import ba.etf.luka.model.*;
import ba.etf.luka.model.drzavna.*;
import ba.etf.luka.model.interfaces.DrzavnoPlovilo;
import ba.etf.luka.model.interfaces.Proganjac;
import ba.etf.luka.util.LoggerUtil;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class SimulacijaStartKontroler implements Initializable {

    @FXML private Spinner<Integer> spMinPlovila;

    private static final Random RANDOM = new Random();
    private static final java.util.concurrent.atomic.AtomicInteger
            GLOBALNI_BROJAC = new java.util.concurrent.atomic.AtomicInteger(0);

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        spMinPlovila.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 5));
    }



    @FXML
    private void onOdustani() {
        ((Stage) spMinPlovila.getScene().getWindow()).close();
    }
    @FXML
    private void onPokreni() {
        int minPlovila = spMinPlovila.getValue();
        Luka luka;
        try {
            // UVIJEK svjez primjerak sa diska — deserijalizacija garantovano daje
            // NOVE Thread objekte (Plovilo) u stanju NEW, cak i ako su originali
            // vec bili TERMINATED iz prethodnog pokretanja simulacije u istoj sesiji.
            luka = Luka.deserijalizuj();
        } catch (Exception e) {
            LoggerUtil.logGreska("Greska pri ucitavanju luka.ser prije simulacije", e);
            luka = AdminAplikacija.getLuka(); // fallback ako fajl ne postoji/je ostecen
        }
        // Ocisti kanale od prethodne simulacije
        luka.ocistiKanale();

        // Korak 1+2: Plovila iz admina su vec na dokovima.
        // Ako ih je manje od minimuma — dodaj na slobodne dokove direktno.
        dopuniNaSlobodneDokove(luka, minPlovila);

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/simulacija.fxml"));
            Stage stage = (Stage) spMinPlovila.getScene().getWindow();
            Scene scene = new Scene(loader.load(), 1100, 700);
            scene.getStylesheets().add(
                    getClass().getResource("/css/stil.css").toExternalForm());

            SimulacijaKontroler kontroler = loader.getController();
            kontroler.inicijalizujSimulaciju(luka, minPlovila);

            stage.setTitle("Luka — Simulacija");
            stage.setScene(scene);
            stage.setMaximized(true);
        } catch (IOException e) {
            LoggerUtil.logGreska("Greška pri otvaranju simulacije", e);
        }
    }

    private void dopuniNaSlobodneDokove(Luka luka, int minPlovila) {
        for (Terminal terminal : luka.getTerminali()) {
            int trenutniBroj = terminal.getSvaPlovilaD().size();
            if (trenutniBroj >= minPlovila) continue;

            int nedostaje = minPlovila - trenutniBroj;
            for (int i = 0; i < nedostaje; i++) {
                Dok slobodanDok = terminal.getSlucajanSlobodanDok(RANDOM);
                if (slobodanDok == null) break;
                // ISPRAVKA: globalno jedinstven redni broj
                Plovilo novo = generisiSlucajnoPlovilo(GLOBALNI_BROJAC.incrementAndGet());
                slobodanDok.privaziPlovilo(novo);
                LoggerUtil.logInfo("Dodano na dok: " + novo.getImoBroj()
                        + " T" + terminal.getRedniBroj()
                        + " dok" + slobodanDok.getRedniBroj());
            }
        }

        osigurајObalskuStrazuZaPotjeru(luka);
        osigurајPotjeruUSimulaciji(luka);
    }
    private void osigurајObalskuStrazuZaPotjeru(Luka luka) {

        Plovilo postojecaOS = null;
        Proganjac postojecaOSProganjac = null;
        List<String> imaULuci = new ArrayList<>();

        for (Terminal t : luka.getTerminali()) {
            for (Plovilo p : t.getSvaPlovilaD()) {
                imaULuci.add(p.getImoBroj());
                if (p instanceof Proganjac os
                        && os.getPutanjaFajlaPotjera() != null
                        && !os.getPutanjaFajlaPotjera().isBlank()) {
                    postojecaOS = p;
                    postojecaOSProganjac = os;
                }
            }
        }

        if (postojecaOS == null) {
            LoggerUtil.logInfo("[TEST] Nema obalske straze sa fajlom potjera.");
            return;
        }

        List<String> potjere = ba.etf.luka.util.BinarniFajlUtil.ucitajListuPotjera(postojecaOSProganjac.getPutanjaFajlaPotjera());
        postojecaOSProganjac.setListaPotjera(potjere);

        if (potjere.isEmpty()) {
            LoggerUtil.logInfo("[TEST] Fajl potjera je prazan.");
            return;
        }


        boolean nekiJeULuci = potjere.stream().anyMatch(imaULuci::contains);

        if (!nekiJeULuci) {
            LoggerUtil.logInfo(
                    "[TEST] Nijedno plovilo iz potjera nije u luci. "
                            + "Dodajem testno plovilo na slobodan dok...");


            String ciljniImo = potjere.get(0);
            for (Terminal t : luka.getTerminali()) {
                Dok slobodan = t.getFirstSlobodanDok();
                if (slobodan != null) {
                    Plovilo cilj = new ba.etf.luka.model.Kruzer(
                            "Osumnjiceni", ciljniImo, 2, "REG-OSU", null, 500);
                    slobodan.privaziPlovilo(cilj);
                    LoggerUtil.logInfo("[TEST] Dodano plovilo za potjeru: "
                            + ciljniImo + " -> T"
                            + t.getRedniBroj());
                    break;
                }
            }
        } else {
            LoggerUtil.logInfo("[TEST] Plovilo iz potjera pronadjeno u luci.");
        }

        LoggerUtil.logInfo("[TEST] Obalska straza: "
                + postojecaOS.getImoBroj()
                + " | Potjere: " + potjere);
    }


    private void osigurајPotjeruUSimulaciji(Luka luka) {
        for (Terminal terminal : luka.getTerminali()) {
            Plovilo os = null;
            Proganjac osProganjac = null;
            List<String> potjere = null;

            for (Plovilo p : terminal.getSvaPlovilaD()) {
                if (p instanceof Proganjac osTmp
                        && osTmp.getPutanjaFajlaPotjera() != null
                        && !osTmp.getPutanjaFajlaPotjera().isBlank()) {
                    List<String> lista = ba.etf.luka.util.BinarniFajlUtil.ucitajListuPotjera(osTmp.getPutanjaFajlaPotjera());
                    if (!lista.isEmpty()) {
                        os = p;
                        osProganjac = osTmp;
                        potjere = lista;
                        osProganjac.setListaPotjera(lista);
                        break;
                    }
                }

            }
            if (os == null || potjere == null) continue;

            // Pronađi osumnjičenog na istom terminalu
            Plovilo osumnjiceni = null;
            for (Plovilo p : terminal.getSvaPlovilaD()) {
                if (potjere.contains(p.getImoBroj())) {
                    osumnjiceni = p;
                    break;
                }
            }

            // Ako osumnjičeni nije na ovom terminalu — pretraži sve
            if (osumnjiceni == null) {
                outer:
                for (Terminal t : luka.getTerminali()) {
                    for (Plovilo p : t.getSvaPlovilaD()) {
                        if (potjere.contains(p.getImoBroj())) {
                            osumnjiceni = p;
                            break outer;
                        }
                    }
                }
            }

            if (osumnjiceni == null) {
                LoggerUtil.logInfo("[POTJERA-TEST] Osumnjičeni nije u luci.");
                continue;
            }

            LoggerUtil.logInfo("[POTJERA-TEST] OS: " + os.getImoBroj()
                    + " | Osumnjičeni: " + osumnjiceni.getImoBroj()
                    + " | Terminal: " + terminal.getRedniBroj());

            // Osiguraj da su oba na dokovima na ISTOM terminalu
            // (premjesti osumnjičenog ako je na drugom terminalu)
            Terminal osumTerminal = pronadjiTerminalPlovila(osumnjiceni, luka);
            if (osumTerminal != terminal) {
                // Premjesti — ukloni sa starog, dodaj na ovaj terminal
                luka.ukloniPlovilo(osumnjiceni,
                        luka.getTerminali().indexOf(osumTerminal));
                boolean dodano = luka.dodajPlovilo(osumnjiceni,
                        luka.getTerminali().indexOf(terminal));
                if (!dodano) {
                    LoggerUtil.logInfo("[POTJERA-TEST] Nema mjesta na T"
                            + terminal.getRedniBroj()
                            + " za osumnjičenog.");
                }
            }
        }
    }

    private Terminal pronadjiTerminalPlovila(Plovilo p, Luka luka) {
        for (Terminal t : luka.getTerminali()) {
            for (Plovilo dp : t.getSvaPlovilaD()) {
                if (dp == p) return t;
            }
        }
        return null;
    }


    /**
     * 90% komercijalna, 10% drzavna plovila.
     */
    public static Plovilo generisiSlucajnoPlovilo(int redni) {
        String imo   = "IMO" + (1000000 + RANDOM.nextInt(9000000));
        String naziv = "Plovilo-" + redni;
        String reg   = "REG-" + redni;
        int motori   = 1 + RANDOM.nextInt(4);

        boolean jeDrzavno = RANDOM.nextDouble() < 0.03;
        return jeDrzavno
                ? generisiDrzavno(naziv, imo, motori, reg)
                : generisiKomercijalno(naziv, imo, motori, reg);
    }

    private static Plovilo generisiKomercijalno(String naziv, String imo,
                                                int motori, String reg) {
        return switch (RANDOM.nextInt(3)) {
            case 0 -> new KontejnerskiBrod(naziv, imo, motori, reg, null,
                    1000 + RANDOM.nextInt(20000));
            case 1 -> new Kruzer(naziv, imo, motori, reg, null,
                    100 + RANDOM.nextInt(5000));
            default -> new Tanker(naziv, imo, motori, reg, null,
                    10000 + RANDOM.nextDouble() * 500000);
        };
    }

    private static Plovilo generisiDrzavno(String naziv, String imo, int motori, String reg) {
        boolean rotacija = RANDOM.nextBoolean();
        Plovilo p = switch (RANDOM.nextInt(3)) {
            case 0 -> switch (RANDOM.nextInt(3)) {
                case 0 -> new ObalskaStrazaKontejnerski(naziv, imo, motori, reg, null,
                        1000 + RANDOM.nextInt(20000), null);
                case 1 -> new ObalskaStrazaKruzer(naziv, imo, motori, reg, null,
                        100 + RANDOM.nextInt(5000), null);
                default -> new ObalskaStrazaTanker(naziv, imo, motori, reg, null,
                        10000 + RANDOM.nextDouble() * 500000, null);
            };
            case 1 -> RANDOM.nextBoolean()
                    ? new CarinaKruzer(naziv, imo, motori, reg, null, 100 + RANDOM.nextInt(5000))
                    : new CarinaTanker(naziv, imo, motori, reg, null, 10000 + RANDOM.nextDouble() * 500000);
            default -> new VatrogasniBrod(naziv, imo, motori, reg, null,
                    10000 + RANDOM.nextDouble() * 500000);
        };
        ((DrzavnoPlovilo) p).setRotacija(rotacija);
        return p;
    }
    public static int sljedeciBroj() {
        return GLOBALNI_BROJAC.incrementAndGet();
    }

}