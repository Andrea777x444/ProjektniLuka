package ba.etf.luka.admin;

import ba.etf.luka.model.Luka;
import ba.etf.luka.util.KonfiguracijaUtil;
import ba.etf.luka.util.LoggerUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class AdminAplikacija extends Application {

    // Singleton instanca luke — dijeli se između kontrolera
    private static Luka luka;

    @Override
    public void start(Stage primaryStage) {
        ucitajLuku();

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/pocetna.fxml"));
            Scene scene = new Scene(loader.load(), 900, 600);
            scene.getStylesheets().add(
                    getClass().getResource("/css/stil.css").toExternalForm());

            primaryStage.setTitle("Luka — Administratorski panel");
            primaryStage.setScene(scene);
            primaryStage.setResizable(true);
            primaryStage.show();
        } catch (Exception e) {
            LoggerUtil.logGreska("Greška pri pokretanju admin aplikacije", e);
        }
    }
    // U AdminAplikacija.java, izmijeni ucitajLuku():

    private void ucitajLuku() {
        if (Luka.postojiSerijalizacija()) {
            try {
                luka = Luka.deserijalizuj();
                // Ocisti kanale — samo dokovi trebaju imati plovila
                luka.ocistiKanale();
                LoggerUtil.logInfo("Luka ucitana iz luka.ser. Terminala: "
                        + luka.getBrojTerminala());
            } catch (Exception e) {
                LoggerUtil.logGreska("Greška pri deserijalizaciji", e);
                luka = kreirajNovuLuku();
            }
        } else {
            luka = kreirajNovuLuku();
        }
    }

    private Luka kreirajNovuLuku() {
        int terminali = KonfiguracijaUtil.getBrojTerminala();
        int dokovi    = KonfiguracijaUtil.getBrojDokovaPoTerminalu();
        LoggerUtil.logInfo("Kreirana nova luka: " + terminali
                + " terminala, " + dokovi + " dokova");
        return new Luka(terminali, dokovi);
    }

    public static Luka getLuka() { return luka; }
    public static void setLuka(Luka l) { luka = l; }

    public static void main(String[] args) {
        launch(args);
    }
}