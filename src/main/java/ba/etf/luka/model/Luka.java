package ba.etf.luka.model;

import ba.etf.luka.model.interfaces.DrzavnoPlovilo;
import ba.etf.luka.util.LoggerUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Luka implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final String PUTANJA_SERIJALIZACIJE = "luka.ser";

    private final List<Terminal> terminali;
    // IMO → EvidencijaUlaska (LinkedHashMap čuva redosljed unosa)
    private final Map<String, EvidencijaUlaska> evidencijaUlazaka;

    public Luka(int brojTerminala, int dokokvaPoTerminalu) {
        this.terminali = new ArrayList<>();
        this.evidencijaUlazaka = new LinkedHashMap<>();

        for (int i = 0; i < brojTerminala; i++) {
            terminali.add(new Terminal(i + 1, dokokvaPoTerminalu));
        }
    }

    // --- Upravljanje terminalima ---

    public List<Terminal> getTerminali() { return terminali; }

    public Terminal getTerminal(int index) {
        if (index < 0 || index >= terminali.size()) {
            throw new IndexOutOfBoundsException("Terminal " + index + " ne postoji.");
        }
        return terminali.get(index);
    }

    public int getBrojTerminala() { return terminali.size(); }

    public boolean imaSlobiodnoMjesto() {
        return terminali.stream().anyMatch(Terminal::imaSlobiodanDok);
    }

    // --- Dodavanje plovila na terminal (admin aplikacija) ---

    public boolean dodajPlovilo(Plovilo plovilo, int indexTerminala) {
        Terminal terminal = getTerminal(indexTerminala);
        Dok slobodanDok = terminal.getFirstSlobodanDok();
        if (slobodanDok == null) return false;

        slobodanDok.privaziPlovilo(plovilo);
        return true;
    }

    public boolean ukloniPlovilo(Plovilo plovilo, int indexTerminala) {
        Terminal terminal = getTerminal(indexTerminala);
        for (Dok d : terminal.getGornjiDokovi()) {
            if (!d.isSlobodan() && d.getPlovilo().getImoBroj().equals(plovilo.getImoBroj())) {
                d.odvaziPlovilo();
                return true;
            }
        }
        for (Dok d : terminal.getDonjiDokovi()) {
            if (!d.isSlobodan() && d.getPlovilo().getImoBroj().equals(plovilo.getImoBroj())) {
                d.odvaziPlovilo();
                return true;
            }
        }
        return false;
    }

    // --- Evidencija ulazaka/izlazaka ---

    public void evidentirUlazak(Plovilo plovilo) {
        evidencijaUlazaka.put(plovilo.getImoBroj(), new EvidencijaUlaska(plovilo));
    }

    public void evidentirIzlazak(Plovilo plovilo) {
        EvidencijaUlaska ev = evidencijaUlazaka.get(plovilo.getImoBroj());
        if (ev != null) ev.evidentirIzlazak();
    }

    /**
     * Naplaćuje lučke takse — državna plovila su izuzeta.
     */
    public double naplaciTaksu(Plovilo plovilo) {
        if (plovilo instanceof DrzavnoPlovilo) return 0.0;

        EvidencijaUlaska ev = evidencijaUlazaka.get(plovilo.getImoBroj());
        return ev != null ? ev.izracunajTaksu() : 0.0;
    }

    public Map<String, EvidencijaUlaska> getEvidencijaUlazaka() {
        return evidencijaUlazaka;
    }

    // --- CSV izvoz taksi ---

    public void izvesiTakseUCsv(String putanja) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(putanja))) {
            writer.write("IMO broj,Naziv plovila,Ulazak,Izlazak,Taksa");
            writer.newLine();
            for (EvidencijaUlaska ev : evidencijaUlazaka.values()) {
                if (ev.getVrijemeIzlaska() != null) {
                    writer.write(ev.toCsvRed());
                    writer.newLine();
                }
            }
        }
    }

    // --- Serializacija / Deserializacija ---

    public void serijalizuj() throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(PUTANJA_SERIJALIZACIJE))) {
            oos.writeObject(this);
        }
    }

    public static Luka deserijalizuj() throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(PUTANJA_SERIJALIZACIJE))) {
            return (Luka) ois.readObject();
        }
    }


    /**
     * Poziva se pri svakom pokretanju simulacije.
     * Cistiti kanal — plovila su samo na dokovima, ne u kanalu.
     * Sprjecava zaostale pozicije iz prethodne simulacije.
     */
    public void ocistiKanale() {
        for (Terminal t : terminali) {
            t.ocistiKanal();
        }
        LoggerUtil.logInfo("Kanali terminala ocisceni.");
    }



    public static boolean postojiSerijalizacija() {
        return new File(PUTANJA_SERIJALIZACIJE).exists();
    }

    @Override
    public String toString() {
        return "Luka (" + terminali.size() + " terminala)";
    }
}