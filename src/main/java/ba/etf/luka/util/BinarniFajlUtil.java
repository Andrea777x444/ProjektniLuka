package ba.etf.luka.util;

import ba.etf.luka.model.Uvidaj;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class BinarniFajlUtil {

    private static final String FOLDER =
            System.getProperty("user.home")
                    + File.separator + "luka_uvidaji";

    private BinarniFajlUtil() {}

    public static void inicijalizuj() {
        File folder = new File(FOLDER);
        if (!folder.exists()) {
            boolean kreiran = folder.mkdirs();
            if (kreiran) {
                LoggerUtil.logInfo("Kreiran folder za uviđaje: " + FOLDER);
            }
        }
    }

    public static void sacuvajUvidaj(Uvidaj uvidaj) {
        inicijalizuj();
        String putanja = FOLDER + File.separator
                + "uvidaj_" + uvidaj.getIdUvidaja() + ".bin";

        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(putanja)))) {
            oos.writeObject(uvidaj);
            LoggerUtil.logInfo("Uviđaj sačuvan: " + putanja);
        } catch (IOException e) {
            LoggerUtil.logGreska("Greška pri čuvanju uviđaja: " + putanja, e);
        }
    }

    public static Uvidaj ucitajUvidaj(String putanja) throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(putanja)))) {
            return (Uvidaj) ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Nepoznata klasa u fajlu: " + putanja, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<String> ucitajListuPotjera(String putanja) {
        if (putanja == null || putanja.isBlank()) return new ArrayList<>();

        File fajl = new File(putanja);
        if (!fajl.exists()) return new ArrayList<>();

        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(putanja)))) {
            return (List<String>) ois.readObject();
        } catch (Exception binarnoNijeUspjelo) {

            try {
                List<String> lista = new ArrayList<>();
                for (String linija : Files.readAllLines(fajl.toPath())) {
                    String trim = linija.trim();
                    if (!trim.isEmpty()) lista.add(trim);
                }
                LoggerUtil.logInfo("Lista potjera učitana kao tekstualni fajl: "
                        + putanja + " (" + lista.size() + " IMO brojeva)");
                return lista;
            } catch (IOException tekstNijeUspio) {
                LoggerUtil.logGreska("Greška pri čitanju liste potjera: " + putanja,
                        tekstNijeUspio);
                return new ArrayList<>();
            }
        }
    }

    public static String getFolder() { return FOLDER; }
}