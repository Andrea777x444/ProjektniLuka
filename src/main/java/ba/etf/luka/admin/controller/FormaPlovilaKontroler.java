package ba.etf.luka.admin.controller;

import ba.etf.luka.model.interfaces.DrzavnoPlovilo;
import ba.etf.luka.model.interfaces.Proganjac;

import ba.etf.luka.admin.AdminAplikacija;
import ba.etf.luka.model.*;
import ba.etf.luka.model.drzavna.*;
import ba.etf.luka.util.LoggerUtil;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class FormaPlovilaKontroler implements Initializable {

    @FXML private Label   lblNaslovForme;
    @FXML private TextField tfNaziv, tfImo, tfBrojMotora,
            tfRegistarski, tfFotografija, tfFajlPotjere;
    @FXML private VBox    vboxDinamicka, vboxDrzavna;
    @FXML private HBox    hboxPotjera;
    @FXML private CheckBox cbRotacija;

    // Dinamički kreirani widgeti za specifična polja
    private TextField tfSpecificno1;  // TEU / broj putnika / zapremina
    private Label     lblSpecificno1;

    private String          tipPlovila;
    private Plovilo         postojecePlovilo;
    private int             terminalIndex;
    private PocetniKontroler roditelj;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Kontekst se postavlja iz roditelja — ništa ovdje
    }

    /**
     * Poziva se iz PocetniKontroler neposredno nakon load()-a.
     */
    public void postaviKontekst(String tip, Plovilo postojece,
                                int termIdx, PocetniKontroler roditelj) {
        this.tipPlovila       = tip;
        this.postojecePlovilo = postojece;
        this.terminalIndex    = termIdx;
        this.roditelj         = roditelj;

        lblNaslovForme.setText(tip);
        kreirajDinamickaPolja(tip);
        prikaziDrzavnaPolja(tip);

        if (postojece != null) popuniPodacima(postojece);
    }

    // --- Dinamičko kreiranje polja ---

    private void kreirajDinamickaPolja(String tip) {
        vboxDinamicka.getChildren().clear();
        if (tip.contains("Kontejnerski") || tip.contains("kontejnerski")) {
            lblSpecificno1 = new Label("Kapacitet (TEU):");
            tfSpecificno1  = new TextField(); tfSpecificno1.setPromptText("npr. 5000");
            dodajRed(lblSpecificno1, tfSpecificno1);
        } else if (tip.contains("Kruzer") || tip.contains("kruzer") || tip.contains("putnički")) {
            lblSpecificno1 = new Label("Broj putnika:");
            tfSpecificno1  = new TextField(); tfSpecificno1.setPromptText("npr. 2000");
            dodajRed(lblSpecificno1, tfSpecificno1);
        } else if (tip.contains("Tanker") || tip.contains("tanker") || tip.contains("Vatrogasni")) {
            lblSpecificno1 = new Label("Zapremina (bareli):");
            tfSpecificno1  = new TextField(); tfSpecificno1.setPromptText("npr. 500000");
            dodajRed(lblSpecificno1, tfSpecificno1);
        }
    }

    private void prikaziDrzavnaPolja(String tip) {
        boolean jeDrzavno = tip.contains("straža") || tip.contains("Carina")
                || tip.contains("Vatrogasni");
        vboxDrzavna.setVisible(jeDrzavno);
        vboxDrzavna.setManaged(jeDrzavno);

        boolean jeObalskaStraza = tip.contains("straža");
        hboxPotjera.setVisible(jeObalskaStraza);
        hboxPotjera.setManaged(jeObalskaStraza);
    }

    private void dodajRed(Label lbl, TextField tf) {
        tf.setPrefWidth(280);
        HBox red = new HBox(12, lbl, tf);
        red.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        lbl.setMinWidth(140);
        vboxDinamicka.getChildren().add(red);
    }

    // --- Popunjavanje podataka pri izmjeni ---

    private void popuniPodacima(Plovilo p) {
        tfNaziv.setText(p.getNaziv());
        tfImo.setText(p.getImoBroj());
        tfBrojMotora.setText(String.valueOf(p.getBrojMotora()));
        tfRegistarski.setText(p.getRegistarskiBroj());
        tfFotografija.setText(p.getPutanjaFotografije() != null
                ? p.getPutanjaFotografije() : "");

        if (tfSpecificno1 != null) {
            if (p instanceof KontejnerskiBrod kb)
                tfSpecificno1.setText(String.valueOf(kb.getKapacitetTEU()));
            else if (p instanceof Kruzer kr)
                tfSpecificno1.setText(String.valueOf(kr.getBrojPutnika()));
            else if (p instanceof Tanker t)
                tfSpecificno1.setText(String.valueOf(t.getZapreminaBarela()));
        }

        if (p instanceof DrzavnoPlovilo dp) {
            cbRotacija.setSelected(dp.isRotacijaUpaljena());
        }
        if (p instanceof Proganjac prog) {
            tfFajlPotjere.setText(
                    prog.getPutanjaFajlaPotjera() != null
                            ? prog.getPutanjaFajlaPotjera() : "");
        }
    }

    // --- FileDialog ---

    @FXML
    private void onOdabieriFotografiju() {
        File f = otvoriFileDialog("Odaberi fotografiju",
                new FileChooser.ExtensionFilter("Slike", "*.png","*.jpg","*.jpeg"));
        if (f != null) tfFotografija.setText(f.getAbsolutePath());
    }

    @FXML
    private void onOdaberiFajlPotjera() {
        File f = otvoriFileDialog("Odaberi fajl potjera",
                new FileChooser.ExtensionFilter("Svi fajlovi", "*.*"));
        if (f != null) tfFajlPotjere.setText(f.getAbsolutePath());
    }

    private File otvoriFileDialog(String naslov,
                                  FileChooser.ExtensionFilter filter) {
        FileChooser fc = new FileChooser();
        fc.setTitle(naslov);
        fc.getExtensionFilters().add(filter);
        return fc.showOpenDialog(tfNaziv.getScene().getWindow());
    }

    // --- Čuvanje plovila ---

    @FXML
    private void onSacuvaj() {
        if (!validiraj()) return;

        try {
            Plovilo novo = kreirajPlovilo();

            if (postojecePlovilo != null) {
                // Izmjena: ukloni staro, dodaj novo
                AdminAplikacija.getLuka()
                        .ukloniPlovilo(postojecePlovilo, terminalIndex);
            }

            boolean dodano = AdminAplikacija.getLuka()
                    .dodajPlovilo(novo, terminalIndex);

            if (!dodano) {
                new Alert(Alert.AlertType.WARNING,
                        "Terminal je popunjen — nema slobodnih vezova.",
                        ButtonType.OK).show();
                return;
            }

            roditelj.osvjeziTabelu();
            LoggerUtil.logInfo("Plovilo sačuvano: " + novo.getImoBroj());
            zatvoriProzor();

        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.ERROR,
                    "Provjerite numeričke vrijednosti.", ButtonType.OK).show();
        } catch (Exception e) {
            LoggerUtil.logGreska("Greška pri čuvanju plovila", e);
        }
    }

    @FXML
    private void onOdustani() { zatvoriProzor(); }

    private void zatvoriProzor() {
        ((Stage) tfNaziv.getScene().getWindow()).close();
    }

    // --- Validacija ---

    private boolean validiraj() {
        if (tfNaziv.getText().isBlank()) {
            greska("Naziv je obavezno polje."); return false;
        }
        if (tfImo.getText().isBlank()) {
            greska("IMO broj je obavezno polje."); return false;
        }
        if (tfBrojMotora.getText().isBlank()) {
            greska("Broj motora je obavezno polje."); return false;
        }
        if (tfRegistarski.getText().isBlank()) {
            greska("Registarski broj je obavezno polje."); return false;
        }
        if (tfSpecificno1 != null && tfSpecificno1.getText().isBlank()) {
            greska("Popunite specifično polje za ovaj tip plovila."); return false;
        }
        return true;
    }

    private void greska(String poruka) {
        new Alert(Alert.AlertType.ERROR, poruka, ButtonType.OK).show();
    }

    // --- Fabrika plovila (Factory metoda) ---

    private Plovilo kreirajPlovilo() {
        String naziv  = tfNaziv.getText().trim();
        String imo    = tfImo.getText().trim();
        int motori    = Integer.parseInt(tfBrojMotora.getText().trim());
        String reg    = tfRegistarski.getText().trim();
        String foto   = tfFotografija.getText().trim();
        String potjeraOrNull = tfFajlPotjere.getText().isBlank() ? null : tfFajlPotjere.getText().trim();

        Plovilo plovilo = switch (tipPlovila) {
            case "Kontejnerski brod" -> new KontejnerskiBrod(naziv, imo, motori, reg, foto,
                    Integer.parseInt(tfSpecificno1.getText().trim()));
            case "Kruzer (putnički)" -> new Kruzer(naziv, imo, motori, reg, foto,
                    Integer.parseInt(tfSpecificno1.getText().trim()));
            case "Tanker" -> new Tanker(naziv, imo, motori, reg, foto,
                    Double.parseDouble(tfSpecificno1.getText().trim()));
            case "Obalska straža — kontejnerski" -> new ObalskaStrazaKontejnerski(naziv, imo, motori, reg, foto,
                    Integer.parseInt(tfSpecificno1.getText().trim()), potjeraOrNull);
            case "Obalska straža — kruzer" -> new ObalskaStrazaKruzer(naziv, imo, motori, reg, foto,
                    Integer.parseInt(tfSpecificno1.getText().trim()), potjeraOrNull);
            case "Obalska straža — tanker" -> new ObalskaStrazaTanker(naziv, imo, motori, reg, foto,
                    Double.parseDouble(tfSpecificno1.getText().trim()), potjeraOrNull);
            case "Carina — kruzer" -> new CarinaKruzer(naziv, imo, motori, reg, foto,
                    Integer.parseInt(tfSpecificno1.getText().trim()));
            case "Carina — tanker" -> new CarinaTanker(naziv, imo, motori, reg, foto,
                    Double.parseDouble(tfSpecificno1.getText().trim()));
            case "Vatrogasni brod" -> new VatrogasniBrod(naziv, imo, motori, reg, foto,
                    Double.parseDouble(tfSpecificno1.getText().trim()));
            default -> throw new IllegalArgumentException("Nepoznat tip plovila: " + tipPlovila);
        };

        if (plovilo instanceof DrzavnoPlovilo dp) dp.setRotacija(cbRotacija.isSelected());
        return plovilo;
    }
}