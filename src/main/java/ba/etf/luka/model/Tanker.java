package ba.etf.luka.model;

public class Tanker extends Plovilo {
    private double zapreminaBarela;

    public Tanker(String naziv, String imoBroj, int brojMotora,
                  String registarskiBroj, String putanjaFotografije,
                  double zapreminaBarela) {
        super(naziv, imoBroj, brojMotora, registarskiBroj, putanjaFotografije);
        this.zapreminaBarela = zapreminaBarela;
    }

    public double getZapreminaBarela() { return zapreminaBarela; }
    public void setZapreminaBarela(double zapreminaBarela) { this.zapreminaBarela = zapreminaBarela; }

    @Override
    public String getOznaka() { return "T"; }
}