package ba.etf.luka.model.interfaces;

import ba.etf.luka.model.Terminal;
import java.util.List;

public interface Proganjac {
    List<String> getListaPotjera();
    void setListaPotjera(List<String> lista);   // NOVO
    boolean isPotjeraNa(String imo);
    void zavrsiPotjeru(String imo);
    void postaviBezSkracenice(boolean b);
    int[] getPozicijaDoka(Terminal terminal);
    String getPutanjaFajlaPotjera();
}