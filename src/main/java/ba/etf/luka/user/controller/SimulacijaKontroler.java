package ba.etf.luka.user.controller;

import ba.etf.luka.admin.AdminAplikacija;
import ba.etf.luka.model.*;
import ba.etf.luka.model.drzavna.*;
import ba.etf.luka.model.interfaces.DrzavnoPlovilo;
import ba.etf.luka.model.interfaces.Proganjac;
import ba.etf.luka.user.SimulacijaEngine;
import ba.etf.luka.user.UvidajManager;
import ba.etf.luka.util.LoggerUtil;
import ba.etf.luka.util.TakseUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.scene.layout.HBox;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

public class SimulacijaKontroler implements Initializable {

    @FXML private ComboBox<String> cbTerminal;
    @FXML private TextArea taKanal;
    @FXML private TextArea taLog;
    @FXML private Label lblStatus;
    @FXML private Label lblSlobodniVezovi;
    @FXML private Label lblBlokiran;
    @FXML private Button btnPokreniKretanje;

    private SimulacijaEngine engine;
    private Luka luka;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void initialize(URL url, ResourceBundle rb) {}

    // ---------------------------------------------------------------
    // INICIJALIZACIJA
    // ---------------------------------------------------------------

    public void inicijalizujSimulaciju(Luka luka, int minPlovila) {
        this.luka   = luka;
        this.engine = new SimulacijaEngine(luka);

        popuniCbTerminal();

        // Poveži engine callbackove s UI-om
        engine.setOnOsvjeziUI(this::osvjeziPrikaz);
        engine.setOnSimulacijaZavrsena(this::onZavrsetak);
        engine.setOnDogadaj(poruka -> dodajLog("[DOGAĐAJ] " + poruka));

        // Samo PRIPREMI — plovila se postave na dokove i prikažu, ali se NE
        // kreću. Korisnik može dodati plovila, pa pritisnuti "Pokreni kretanje".
        engine.pripremiSimulaciju();
        osvjeziPrikaz();
        dodajLog("Plovila postavljena na dokove. Dodajte plovila po želji, "
                + "pa pritisnite 'Pokreni kretanje'.");
    }


    @FXML
    private void onPokreniKretanje() {
        if (engine == null) return;
        if (engine.isKretanjePokrenuto()) {
            upozorenje("Kretanje je već pokrenuto.");
            return;
        }
        engine.pokreniKretanje();
        dodajLog("Kretanje pokrenuto — 15% plovila kreće ka izlazu.");
        if (lblStatus != null) lblStatus.setText("Simulacija u toku...");
        // Sakrij dugme — kretanje se pokreće samo jednom
        if (btnPokreniKretanje != null) {
            btnPokreniKretanje.setVisible(false);
            btnPokreniKretanje.setManaged(false);
        }
    }

    private void popuniCbTerminal() {
        var stavke = FXCollections.<String>observableArrayList();
        for (int i = 1; i <= luka.getBrojTerminala(); i++) {
            stavke.add("Terminal " + i);
        }
        cbTerminal.setItems(stavke);
        cbTerminal.getSelectionModel().selectFirst();
    }
    @FXML
    private void onPrikaziUvidaje() {
        List<Uvidaj> lista = engine.getUvidajManager().getEvidencijaUvidaja();

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Uviđaji obalske straže");
        alert.setHeaderText("Ukupno evidentiranih uviđaja: " + lista.size());

        if (lista.isEmpty()) {
            alert.setContentText("Nema evidentiranih uviđaja.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (Uvidaj u : lista) {
                sb.append(u.toString()).append("\n\n");
            }
            TextArea ta = new TextArea(sb.toString());
            ta.setEditable(false);
            ta.setWrapText(true);
            ta.setPrefSize(560, 320);
            alert.getDialogPane().setContent(ta);
        }
        alert.show();
    }

    // ---------------------------------------------------------------
    // EVENT HANDLERI
    // ---------------------------------------------------------------

    @FXML
    private void onTerminalChanged() {
        osvjeziPrikaz();
    }

    @FXML
    private void onDodajPlovilo() {
        // Uzmi indeks trenutno odabranog terminala iz comboboxa
        int odabraniIdx = cbTerminal.getSelectionModel().getSelectedIndex();
        if (odabraniIdx < 0) odabraniIdx = 0;

        Terminal odabraniTerminal = luka.getTerminal(odabraniIdx);

        // Provjeri ima li slobodnog doka na odabranom terminalu
        if (!odabraniTerminal.imaSlobiodanDok()) {
            upozorenje("Terminal " + (odabraniIdx + 1)
                    + " je popunjen — nema slobodnih vezova.");
            return;
        }

        // Zadatak: "korisnik može da dodaje nova plovila u luku sa svim
        // parametrima" — otvaramo dijalog za RUČNI unos svih parametara.
        Plovilo novo = otvoriFormuUnosaPlovila();
        if (novo == null) return; // korisnik odustao ili nevalidan unos

        boolean dodano = engine.dodajPloviloPotok(novo, odabraniIdx);
        if (dodano) {
            dodajLog("Dodano plovilo: " + novo.getNaziv()
                    + " [" + novo.getImoBroj() + "]"
                    + " -> Terminal " + (odabraniIdx + 1));
        } else {
            upozorenje("Nije moguće dodati plovilo u ovom trenutku.");
        }
    }

    /**
     * Otvara modalni dijalog za ručni unos svih parametara novog plovila
     */
    private Plovilo otvoriFormuUnosaPlovila() {
        Dialog<Plovilo> dijalog = new Dialog<>();
        dijalog.setTitle("Dodaj plovilo");
        dijalog.setHeaderText("Unesite parametre novog plovila");
        ButtonType btnDodaj = new ButtonType("Dodaj", ButtonBar.ButtonData.OK_DONE);
        dijalog.getDialogPane().getButtonTypes().addAll(btnDodaj, ButtonType.CANCEL);

        int brojac = SimulacijaStartKontroler.sljedeciBroj();
        TextField tfNaziv = new TextField("Plovilo-" + brojac);
        TextField tfImo   = new TextField("IMO" + (1000000 + new java.util.Random().nextInt(9000000)));
        TextField tfMotori= new TextField("2");
        TextField tfReg   = new TextField("REG-" + brojac);

        ComboBox<String> cbTip = new ComboBox<>(FXCollections.observableArrayList(
                "Kontejnerski brod", "Kruzer (putnički)", "Tanker",
                "Obalska straža — kontejnerski", "Obalska straža — kruzer",
                "Obalska straža — tanker", "Carina — kruzer", "Carina — tanker",
                "Vatrogasni brod"));
        cbTip.getSelectionModel().selectFirst();

        // Specifično polje (TEU / putnici / bareli) — mijenja se prema tipu
        Label lblSpec = new Label("Kapacitet (TEU):");
        TextField tfSpec = new TextField("5000");
        // Državne opcije
        CheckBox cbRotacija = new CheckBox("Rotacija upaljena");
        TextField tfFajlPotjere = new TextField();
        tfFajlPotjere.setPromptText("(samo obalska straža)");
        tfFajlPotjere.setEditable(false);          // korisnik ne kuca ručno, samo bira fajl
        tfFajlPotjere.setPrefWidth(220);

        Button btnOdaberiFajl = new Button("Odaberi...");
        btnOdaberiFajl.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Odaberi fajl potjera");
            fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Tekstualni fajlovi", "*.txt"));
            File f = fc.showOpenDialog(dijalog.getDialogPane().getScene().getWindow());
            if (f != null) tfFajlPotjere.setText(f.getAbsolutePath());
        });

        HBox hboxFajlPotjere = new HBox(6, tfFajlPotjere, btnOdaberiFajl);

        Runnable osvjeziPolja = () -> {
            String tip = cbTip.getValue();
            if (tip.contains("Tanker") || tip.contains("tanker") || tip.contains("Vatrogasni")) {
                lblSpec.setText("Zapremina (bareli):"); tfSpec.setDisable(false); tfSpec.setText("500000");
            } else if (tip.contains("Kruzer") || tip.contains("kruzer")) {
                lblSpec.setText("Broj putnika:"); tfSpec.setDisable(false); tfSpec.setText("2000");
            } else {
                lblSpec.setText("Kapacitet (TEU):"); tfSpec.setDisable(false); tfSpec.setText("5000");
            }
            boolean drzavno = tip.contains("straža") || tip.contains("Carina") || tip.contains("Vatrogasni");
            cbRotacija.setDisable(!drzavno);
            tfFajlPotjere.setDisable(!tip.contains("straža"));
            btnOdaberiFajl.setDisable(!tip.contains("straža"));
        };
        cbTip.setOnAction(e -> osvjeziPolja.run());
        osvjeziPolja.run();

        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(10); g.setVgap(8);
        g.addRow(0, new Label("Naziv:"), tfNaziv);
        g.addRow(1, new Label("IMO broj:"), tfImo);
        g.addRow(2, new Label("Broj motora:"), tfMotori);
        g.addRow(3, new Label("Registarski broj:"), tfReg);
        g.addRow(4, new Label("Tip plovila:"), cbTip);
        g.addRow(5, lblSpec, tfSpec);
        g.addRow(6, new Label("Državno:"), cbRotacija);
        g.addRow(7, new Label("Fajl potjere:"), hboxFajlPotjere);
        dijalog.getDialogPane().setContent(g);

        dijalog.setResultConverter(dugme -> {
            if (dugme != btnDodaj) return null;
            try {
                return kreirajPloviloIzUnosa(
                        tfNaziv.getText().trim(), tfImo.getText().trim(),
                        tfMotori.getText().trim(), tfReg.getText().trim(),
                        cbTip.getValue(), tfSpec.getText().trim(),
                        cbRotacija.isSelected(), tfFajlPotjere.getText().trim());
            } catch (Exception ex) {
                greska("Neispravan unos: " + ex.getMessage());
                return null;
            }
        });

        return dijalog.showAndWait().orElse(null);
    }

    /** Kreira plovilo odgovarajućeg tipa iz unesenih parametara (validira unos). */
    private Plovilo kreirajPloviloIzUnosa(String naziv, String imo, String motoriStr,
                                          String reg, String tip, String spec,
                                          boolean rotacija, String fajlPotjere) {
        if (naziv.isBlank() || imo.isBlank() || motoriStr.isBlank() || reg.isBlank())
            throw new IllegalArgumentException("naziv, IMO, motori i registarski broj su obavezni.");
        int motori = Integer.parseInt(motoriStr);
        String potjeraOrNull = fajlPotjere.isBlank() ? null : fajlPotjere;

        Plovilo plovilo = switch (tip) {
            case "Kontejnerski brod" -> new KontejnerskiBrod(naziv, imo, motori, reg, null, Integer.parseInt(spec));
            case "Kruzer (putnički)" -> new Kruzer(naziv, imo, motori, reg, null, Integer.parseInt(spec));
            case "Tanker" -> new Tanker(naziv, imo, motori, reg, null, Double.parseDouble(spec));
            case "Obalska straža — kontejnerski" -> new ObalskaStrazaKontejnerski(naziv, imo, motori, reg, null,
                    Integer.parseInt(spec), potjeraOrNull);
            case "Obalska straža — kruzer" -> new ObalskaStrazaKruzer(naziv, imo, motori, reg, null,
                    Integer.parseInt(spec), potjeraOrNull);
            case "Obalska straža — tanker" -> new ObalskaStrazaTanker(naziv, imo, motori, reg, null,
                    Double.parseDouble(spec), potjeraOrNull);
            case "Carina — kruzer" -> new CarinaKruzer(naziv, imo, motori, reg, null, Integer.parseInt(spec));
            case "Carina — tanker" -> new CarinaTanker(naziv, imo, motori, reg, null, Double.parseDouble(spec));
            case "Vatrogasni brod" -> new VatrogasniBrod(naziv, imo, motori, reg, null, Double.parseDouble(spec));
            default -> throw new IllegalArgumentException("nepoznat tip: " + tip);
        };

        if (plovilo instanceof DrzavnoPlovilo dp) {
            dp.setRotacija(rotacija);
            if (plovilo instanceof Proganjac prog && potjeraOrNull != null) {
                List<String> lista = ba.etf.luka.util.BinarniFajlUtil.ucitajListuPotjera(potjeraOrNull);
                prog.setListaPotjera(lista);
                dodajLog("Učitana lista potjera (" + lista.size() + " IMO brojeva) iz: " + potjeraOrNull);
            }
        }
        return plovilo;
    }

    @FXML
    private void onPrikaziTakse() {
        String sazetak = TakseUtil.getSazetakNaplate(luka);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Lučke takse");
        alert.setHeaderText("Pregled naplaćenih lučkih taksi");

        TextArea ta = new TextArea(sazetak);
        ta.setEditable(false);
        ta.setStyle("-fx-font-family: monospace; -fx-font-size: 13px;");
        ta.setPrefSize(500, 360);
        alert.getDialogPane().setContent(ta);
        alert.show();
    }

    @FXML
    private void onIzvesiCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Izvoz evidencije taksi");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV fajlovi", "*.csv"));
        fc.setInitialFileName("takse_luke.csv");

        File f = fc.showSaveDialog(taKanal.getScene().getWindow());
        if (f == null) return;

        try {
            TakseUtil.izvesiUCsv(luka, f.getAbsolutePath());
            dodajLog("CSV izvezen: " + f.getName());
            info("Takse izvezene u:\n" + f.getAbsolutePath());
        } catch (IOException e) {
            LoggerUtil.logGreska("Greška pri izvozu CSV-a", e);
            greska("Greška pri izvozu CSV-a: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // PRIKAZ STANJA TERMINALA
    // ---------------------------------------------------------------

    private void osvjeziPrikaz() {
        int idx = cbTerminal.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= luka.getBrojTerminala()) return;

        Terminal terminal = luka.getTerminal(idx);
        boolean blokiran  = engine.isTerminalBlokiran(idx);

        taKanal.setText(formatirajTerminal(terminal, idx, blokiran));

        lblSlobodniVezovi.setText(
                "Slobodni vezovi: " + terminal.getBrojSlobodnihVezova()
                        + " / " + (terminal.getGornjiDokovi().size()
                        + terminal.getDonjiDokovi().size()));

        lblBlokiran.setVisible(blokiran);
        lblBlokiran.setManaged(blokiran);
        lblBlokiran.setText("⚠ TERMINAL BLOKIRAN — UVIĐAJ U TOKU");
    }

    /**
     * Formatira terminal prema prikazu iz zadatka:
     */
    private String formatirajTerminal(Terminal terminal,
                                      int idx, boolean blokiran) {
        StringBuilder sb = new StringBuilder();
        if (blokiran) sb.append("*** BLOKIRAN — UVIĐAJ ***\n\n");

        String[][] matrica  = terminal.getMatricaPrikaza();
        int        brKolona = terminal.getDuzinaKanala(); // Tacno broj dokova

        for (int redBr = 0; redBr < 4; redBr++) {
            String prefiks = switch (redBr) {
                case 2  -> terminal.getUlaznaKapijaOznaka()  + "   ";
                case 1  -> "  " + terminal.getIzlaznaKapijaOznaka() + " ";
                default -> "     ";
            };
            sb.append(prefiks);
            for (int k = 0; k < brKolona; k++) {
                String polje = matrica[redBr][k];
                if (polje == null || polje.isBlank()) {
                    polje = switch (redBr) {
                        case 0, 3 -> "*"; // Slobodan dok
                        default   -> " "; // Prazan kanal
                    };
                }
                sb.append(String.format("%-5s", polje));
            }
            sb.append("\n");
        }

        sb.append("\nSlobodni vezovi: ")
                .append(terminal.getBrojSlobodnihVezova());
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // LOG I ZAVRŠETAK
    // ---------------------------------------------------------------

    private void dodajLog(String poruka) {
        String linija = "[" + LocalTime.now().format(FORMATTER)
                + "] " + poruka;
        taLog.appendText(linija + "\n");
    }

    private void onZavrsetak() {
        AdminAplikacija.setLuka(luka);
        lblStatus.setText("✔ Simulacija završena");
        lblStatus.setStyle(
                "-fx-text-fill:#ffd700;-fx-font-weight:bold;");
        dodajLog("=== SIMULACIJA ZAVRŠENA — luka.ser ažuriran ===");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Simulacija završena");
        alert.setHeaderText("Simulacija je uspješno završena!");
        alert.setContentText(
                "Stanje luke je sačuvano u luka.ser.\n"
                        + "Možete izvesti CSV evidenciju taksi.");
        alert.show();
    }

    // ---------------------------------------------------------------
    // POMOĆNE METODE ZA DIJALOGE
    // ---------------------------------------------------------------

    private void upozorenje(String poruka) {
        new Alert(Alert.AlertType.WARNING, poruka, ButtonType.OK).show();
    }

    private void info(String poruka) {
        new Alert(Alert.AlertType.INFORMATION,
                poruka, ButtonType.OK).show();
    }

    private void greska(String poruka) {
        new Alert(Alert.AlertType.ERROR, poruka, ButtonType.OK).show();
    }
}