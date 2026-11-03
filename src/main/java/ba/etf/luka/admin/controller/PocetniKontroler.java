package ba.etf.luka.admin.controller;

import ba.etf.luka.admin.AdminAplikacija;
import ba.etf.luka.model.*;
import ba.etf.luka.model.drzavna.*;
import ba.etf.luka.model.interfaces.DrzavnoPlovilo;
import ba.etf.luka.util.LoggerUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import static ba.etf.luka.model.interfaces.TipDrzavnog.VATROGASCI;

public class PocetniKontroler implements Initializable {

    @FXML private ComboBox<String> cbTerminal;
    @FXML private ComboBox<String> cbTipPlovila;
    @FXML private TableView<Plovilo> tabelaPlovila;
    @FXML private TableColumn<Plovilo, String> kolNaziv;
    @FXML private TableColumn<Plovilo, String> kolImo;
    @FXML private TableColumn<Plovilo, String> kolTip;
    @FXML private TableColumn<Plovilo, String> kolReg;
    @FXML private TableColumn<Plovilo, String> kolMotori;
    @FXML private TableColumn<Plovilo, String> kolDetalji;
    @FXML private Label lblBrojSlobodnihVezova;

    // Svi tipovi plovila dostupni za dodavanje
    private static final List<String> TIPOVI_PLOVILA = List.of(
            "Kontejnerski brod",
            "Kruzer (putnički)",
            "Tanker",
            "Obalska straža — kontejnerski",
            "Obalska straža — kruzer",
            "Obalska straža — tanker",
            "Carina — kruzer",
            "Carina — tanker",
            "Vatrogasni brod"
    );

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        postaviKoloneTabele();
        popuniCbTerminal();
        cbTipPlovila.setItems(FXCollections.observableArrayList(TIPOVI_PLOVILA));
        cbTipPlovila.getSelectionModel().selectFirst();

        // Automatski učitaj prvi terminal
        if (!cbTerminal.getItems().isEmpty()) {
            cbTerminal.getSelectionModel().selectFirst();
            osvjeziTabelu();
        }
    }

    // --- Inicijalizacija ---

    private void postaviKoloneTabele() {
        kolNaziv.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getNaziv()));
        kolImo.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getImoBroj()));
        kolTip.setCellValueFactory(c ->
                new SimpleStringProperty(getTipPlovila(c.getValue())));
        kolReg.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getRegistarskiBroj()));
        kolMotori.setCellValueFactory(c ->
                new SimpleStringProperty(
                        String.valueOf(c.getValue().getBrojMotora())));
        kolDetalji.setCellValueFactory(c ->
                new SimpleStringProperty(getDetalji(c.getValue())));
    }

    private void popuniCbTerminal() {
        ObservableList<String> stavke = FXCollections.observableArrayList();
        int n = AdminAplikacija.getLuka().getBrojTerminala();
        for (int i = 1; i <= n; i++) stavke.add("Terminal " + i);
        cbTerminal.setItems(stavke);
    }

    // --- Event handleri ---

    @FXML
    private void onTerminalChanged() {
        osvjeziTabelu();
    }

    @FXML
    private void onDodajPlovilo() {
        String tip = cbTipPlovila.getSelectionModel().getSelectedItem();
        if (tip == null) return;
        otvoriFormuPlovila(tip, null);
    }

    @FXML
    private void onIzmijeniPlovilo() {
        Plovilo odabrano = tabelaPlovila.getSelectionModel().getSelectedItem();
        if (odabrano == null) {
            prikaziUpozorenje("Odaberite plovilo za izmjenu.");
            return;
        }
        String tip = getTipPlovila(odabrano);
        otvoriFormuPlovila(tip, odabrano);
    }

    @FXML
    private void onObrisiPlovilo() {
        Plovilo odabrano = tabelaPlovila.getSelectionModel().getSelectedItem();
        if (odabrano == null) {
            prikaziUpozorenje("Odaberite plovilo za brisanje.");
            return;
        }

        Alert potvrda = new Alert(Alert.AlertType.CONFIRMATION);
        potvrda.setTitle("Brisanje plovila");
        potvrda.setHeaderText("Potvrda brisanja");
        potvrda.setContentText("Obrisati plovilo \"" + odabrano.getNaziv() + "\"?");

        potvrda.showAndWait().ifPresent(odgovor -> {
            if (odgovor == ButtonType.OK) {
                int idx = getOdabraniTerminalIndex();
                AdminAplikacija.getLuka().ukloniPlovilo(odabrano, idx);
                osvjeziTabelu();
                LoggerUtil.logInfo("Obrisano plovilo: " + odabrano.getImoBroj());
            }
        });
    }

    @FXML
    private void onSacuvaj() {
        try {
            AdminAplikacija.getLuka().serijalizuj();
            prikaziInfo("Stanje luke uspješno sačuvano.");
            LoggerUtil.logInfo("Luka serijalizovana.");
        } catch (IOException e) {
            LoggerUtil.logGreska("Greška pri serijalizaciji", e);
            prikaziGresku("Greška pri čuvanju stanja: " + e.getMessage());
        }
    }

    @FXML
    private void onPokreniSimulaciju() {
        // Sačuvaj prije pokretanja
        try {
            AdminAplikacija.getLuka().serijalizuj();
        } catch (IOException e) {
            LoggerUtil.logGreska("Greška pri serijalizaciji prije simulacije", e);
            prikaziGresku("Nije moguće pokrenuti simulaciju — greška pri čuvanju.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/simulacija_start.fxml"));
            Stage stage = new Stage();
            stage.setTitle("Pokretanje simulacije");
            stage.setScene(new Scene(loader.load(), 500, 340));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();
        } catch (IOException e) {
            LoggerUtil.logGreska("Greška pri otvaranju simulacije", e);
            prikaziGresku("Greška pri pokretanju korisničke aplikacije.");
        }
    }

    // --- Forma za dodavanje / izmjenu plovila ---

    private void otvoriFormuPlovila(String tip, Plovilo postojece) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/forma_plovila.fxml"));
            Stage stage = new Stage();
            stage.setTitle(postojece == null
                    ? "Dodaj plovilo — " + tip
                    : "Izmijeni plovilo — " + tip);
            stage.setScene(new Scene(loader.load(), 520, 580));
            stage.initModality(Modality.APPLICATION_MODAL);

            FormaPlovilaKontroler kontroler = loader.getController();
            kontroler.postaviKontekst(tip, postojece,
                    getOdabraniTerminalIndex(), this);

            stage.show();
        } catch (IOException e) {
            LoggerUtil.logGreska("Greška pri otvaranju forme plovila", e);
        }
    }

    // --- Pomoćne metode ---

    public void osvjeziTabelu() {
        int idx = getOdabraniTerminalIndex();
        if (idx < 0) return;

        Terminal terminal = AdminAplikacija.getLuka().getTerminal(idx);
        List<Plovilo> plovila = terminal.getSvaPlovilaD();

        tabelaPlovila.setItems(FXCollections.observableArrayList(plovila));

        int slobodni = terminal.getBrojSlobodnihVezova();
        lblBrojSlobodnihVezova.setText("Slobodni vezovi: " + slobodni);
    }

    private int getOdabraniTerminalIndex() {
        int sel = cbTerminal.getSelectionModel().getSelectedIndex();
        return sel; // index u listi terminala (0-based)
    }

    private String getTipPlovila(Plovilo p) {
        if (p instanceof DrzavnoPlovilo dp) {
            return switch (dp.getTip()) {
                case OBALSKA_STRAZA -> "Obalska straža";
                case CARINA         -> "Carina";
                case VATROGASCI     -> "Vatrogasci";
            };
        }
        if (p instanceof KontejnerskiBrod) return "Kontejnerski brod";
        if (p instanceof Kruzer)           return "Kruzer";
        if (p instanceof Tanker)           return "Tanker";
        return "Nepoznato";
    }

    private String getDetalji(Plovilo p) {
        if (p instanceof KontejnerskiBrod kb)
            return kb.getKapacitetTEU() + " TEU";
        if (p instanceof Kruzer kr)
            return kr.getBrojPutnika() + " putnika";
        if (p instanceof Tanker t)
            return t.getZapreminaBarela() + " barela";
        return "-";
    }

    private void prikaziUpozorenje(String poruka) {
        new Alert(Alert.AlertType.WARNING, poruka, ButtonType.OK).show();
    }

    private void prikaziInfo(String poruka) {
        new Alert(Alert.AlertType.INFORMATION, poruka, ButtonType.OK).show();
    }

    private void prikaziGresku(String poruka) {
        new Alert(Alert.AlertType.ERROR, poruka, ButtonType.OK).show();
    }
}