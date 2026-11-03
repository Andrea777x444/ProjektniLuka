package ba.etf.luka.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class KonfiguracijaUtil {
    private static final String NAZIV_FAJLA = "konfiguracija.properties";
    private static final Properties props = new Properties();

    static {
        try (InputStream is = KonfiguracijaUtil.class
                .getClassLoader().getResourceAsStream(NAZIV_FAJLA)) {
            if (is != null) {
                props.load(is);
            } else {
                throw new RuntimeException("Nije pronađen fajl: " + NAZIV_FAJLA);
            }
        } catch (IOException e) {
            throw new RuntimeException("Greška pri čitanju konfiguracije.", e);
        }
    }

    private KonfiguracijaUtil() {

    }

    public static int getBrojTerminala() {
        return Integer.parseInt(props.getProperty("luka.terminali", "3"));
    }

    public static int getBrojDokovaPoTerminalu() {
        return Integer.parseInt(props.getProperty("luka.dokokvi.po.terminalu", "15"));
    }

    public static String getProperty(String kljuc) {
        return props.getProperty(kljuc);
    }
}