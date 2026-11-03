package ba.etf.luka.model.drzavna;
import ba.etf.luka.model.Tanker;
import ba.etf.luka.model.interfaces.*;

public class VatrogasniBrod extends Tanker implements DrzavnoPlovilo, Rotacija {
    private final DrzavniPodaci drzavniPodaci = new DrzavniPodaci(TipDrzavnog.VATROGASCI, 3);

    public VatrogasniBrod(String naziv, String imoBroj, int brojMotora,
                          String registarskiBroj, String putanjaFotografije,
                          double zapreminaBarela) {
        super(naziv, imoBroj, brojMotora, registarskiBroj, putanjaFotografije, zapreminaBarela);
    }

    @Override public TipDrzavnog getTip()         { return drzavniPodaci.getTip(); }
    @Override public int getPrioritet()           { return drzavniPodaci.getPrioritet(); }
    @Override public boolean isRotacijaUpaljena()  { return drzavniPodaci.isRotacijaUpaljena(); }
    @Override public void setRotacija(boolean b)   { drzavniPodaci.setRotacija(b); }
    @Override public String getOznaka() { return "V"; }
}