package ba.etf.luka.user;

import ba.etf.luka.model.Luka;
import ba.etf.luka.util.LoggerUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class KorisnickaAplikacija extends Application {

    private static Luka luka;

    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/simulacija_start.fxml"));
            Scene scene = new Scene(loader.load(), 500, 340);
            scene.getStylesheets().add(
                    getClass().getResource("/css/stil.css").toExternalForm());
            primaryStage.setTitle("Luka — Pokretanje simulacije");
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception e) {
            LoggerUtil.logGreska("Greška pri pokretanju korisničke aplikacije", e);
        }
    }

    public static Luka getLuka() { return luka; }
    public static void setLuka(Luka l) { luka = l; }

    public static void main(String[] args) { launch(args); }
}