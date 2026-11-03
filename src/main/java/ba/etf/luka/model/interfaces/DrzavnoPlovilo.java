package ba.etf.luka.model.interfaces;

public interface DrzavnoPlovilo {
    int getPrioritet();
    TipDrzavnog getTip();

    boolean isRotacijaUpaljena();

    void setRotacija(boolean b);
}