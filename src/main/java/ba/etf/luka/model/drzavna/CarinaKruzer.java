// CarinaKruzer.java
package ba.etf.luka.model.drzavna;
import ba.etf.luka.model.Kruzer;
import ba.etf.luka.model.interfaces.*;

public class CarinaKruzer extends Kruzer implements DrzavnoPlovilo, Rotacija {
    private final DrzavniPodaci drzavniPodaci = new DrzavniPodaci(TipDrzavnog.CARINA, 1);

    public CarinaKruzer(String naziv, String imoBroj, int brojMotora,
                        String registarskiBroj, String putanjaFotografije, int brojPutnika) {
        super(naziv, imoBroj, brojMotora, registarskiBroj, putanjaFotografije, brojPutnika);
    }

    @Override public TipDrzavnog getTip()         { return drzavniPodaci.getTip(); }
    @Override public int getPrioritet()           { return drzavniPodaci.getPrioritet(); }
    @Override public boolean isRotacijaUpaljena()  { return drzavniPodaci.isRotacijaUpaljena(); }
    @Override public void setRotacija(boolean b)   { drzavniPodaci.setRotacija(b); }
    @Override public String getOznaka() { return "CA"; }
}