package ba.etf.luka.model;

public class KontejnerskiBrod extends Plovilo {
    private int kapacitetTEU;  // Twenty-foot Equivalent Unit

    public KontejnerskiBrod(String naziv, String imoBroj, int brojMotora,
                            String registarskiBroj, String putanjaFotografije,
                            int kapacitetTEU) {
        super(naziv, imoBroj, brojMotora, registarskiBroj, putanjaFotografije);
        this.kapacitetTEU = kapacitetTEU;
    }

    public int getKapacitetTEU() { return kapacitetTEU; }
    public void setKapacitetTEU(int kapacitetTEU) { this.kapacitetTEU = kapacitetTEU; }

    @Override
    public String getOznaka() { return "K"; }
}