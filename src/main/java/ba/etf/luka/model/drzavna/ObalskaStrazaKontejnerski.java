package ba.etf.luka.model.drzavna;

import ba.etf.luka.model.KontejnerskiBrod;
import ba.etf.luka.model.interfaces.DrzavniPodaci;
import ba.etf.luka.model.interfaces.DrzavnoPlovilo;
import ba.etf.luka.model.interfaces.PotjeraPodaci;
import ba.etf.luka.model.interfaces.Proganjac;
import ba.etf.luka.model.interfaces.Rotacija;
import ba.etf.luka.model.interfaces.TipDrzavnog;

import java.util.List;

public class ObalskaStrazaKontejnerski extends KontejnerskiBrod
        implements DrzavnoPlovilo, Proganjac, Rotacija {

    private final DrzavniPodaci drzavniPodaci = new DrzavniPodaci(TipDrzavnog.OBALSKA_STRAZA, 2);
    private final PotjeraPodaci potjeraPodaci;

    public ObalskaStrazaKontejnerski(String naziv, String imoBroj, int brojMotora,
                                     String registarskiBroj, String putanjaFotografije,
                                     int kapacitetTEU, String putanjaFajlaPotjera) {
        super(naziv, imoBroj, brojMotora, registarskiBroj, putanjaFotografije, kapacitetTEU);
        this.potjeraPodaci = new PotjeraPodaci(putanjaFajlaPotjera);
    }

    @Override public TipDrzavnog getTip()                  { return drzavniPodaci.getTip(); }
    @Override public int getPrioritet()                    { return drzavniPodaci.getPrioritet(); }
    @Override public boolean isRotacijaUpaljena()           { return drzavniPodaci.isRotacijaUpaljena(); }
    @Override public void setRotacija(boolean b)            { drzavniPodaci.setRotacija(b); }

    @Override public List<String> getListaPotjera()         { return potjeraPodaci.getListaPotjera(); }
    @Override public void setListaPotjera(List<String> l)   { potjeraPodaci.setListaPotjera(l); }
    @Override public boolean isPotjeraNa(String imo)        { return potjeraPodaci.isPotjeraNa(imo); }
    @Override public void zavrsiPotjeru(String imo)         { potjeraPodaci.zavrsiPotjeru(imo); }
    public void dodajPotjeru(String imo)                    { potjeraPodaci.dodajPotjeru(imo); }
    @Override public String getPutanjaFajlaPotjera()        { return potjeraPodaci.getPutanjaFajlaPotjera(); }

    @Override public String getOznaka() { return "OS"; }
}