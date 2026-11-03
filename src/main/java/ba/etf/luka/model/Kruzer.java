package ba.etf.luka.model;

public class Kruzer extends Plovilo {
    private int brojPutnika;

    public Kruzer(String naziv, String imoBroj, int brojMotora,
                  String registarskiBroj, String putanjaFotografije,
                  int brojPutnika) {
        super(naziv, imoBroj, brojMotora, registarskiBroj, putanjaFotografije);
        this.brojPutnika = brojPutnika;
    }

    public int getBrojPutnika() { return brojPutnika; }
    public void setBrojPutnika(int brojPutnika) { this.brojPutnika = brojPutnika; }

    @Override
    public String getOznaka() { return "P"; }
}