// ObalskaStrazaTanker.java
package ba.etf.luka.model.drzavna;
import ba.etf.luka.model.Tanker;
import ba.etf.luka.model.interfaces.*;
import java.util.List;

public class ObalskaStrazaTanker extends Tanker
        implements DrzavnoPlovilo, Proganjac, Rotacija {
    private final DrzavniPodaci drzavniPodaci = new DrzavniPodaci(TipDrzavnog.OBALSKA_STRAZA, 2);
    private final PotjeraPodaci potjeraPodaci;

    public ObalskaStrazaTanker(String naziv, String imoBroj, int brojMotora,
                               String registarskiBroj, String putanjaFotografije,
                               double zapreminaBarela, String putanjaFajlaPotjera) {
        super(naziv, imoBroj, brojMotora, registarskiBroj, putanjaFotografije, zapreminaBarela);
        this.potjeraPodaci = new PotjeraPodaci(putanjaFajlaPotjera);
    }

    @Override public TipDrzavnog getTip()           { return drzavniPodaci.getTip(); }
    @Override public int getPrioritet()             { return drzavniPodaci.getPrioritet(); }
    @Override public boolean isRotacijaUpaljena()    { return drzavniPodaci.isRotacijaUpaljena(); }
    @Override public void setRotacija(boolean b)     { drzavniPodaci.setRotacija(b); }
    @Override public List<String> getListaPotjera()  { return potjeraPodaci.getListaPotjera(); }
    @Override public void setListaPotjera(List<String> l) { potjeraPodaci.setListaPotjera(l); }
    @Override public boolean isPotjeraNa(String imo) { return potjeraPodaci.isPotjeraNa(imo); }
    @Override public void zavrsiPotjeru(String imo)  { potjeraPodaci.zavrsiPotjeru(imo); }
    public void dodajPotjeru(String imo)             { potjeraPodaci.dodajPotjeru(imo); }
    @Override public String getPutanjaFajlaPotjera() { return potjeraPodaci.getPutanjaFajlaPotjera(); }
    @Override public String getOznaka() { return "OS"; }
}