package ba.etf.luka.util;

import ba.etf.luka.model.EvidencijaUlaska;
import ba.etf.luka.model.Luka;
import ba.etf.luka.model.interfaces.DrzavnoPlovilo;
import ba.etf.luka.model.Plovilo;

import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class TakseUtil {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private TakseUtil() {}


    public static void izvesiUCsv(Luka luka, String putanja)
            throws IOException {
        try (BufferedWriter bw = new BufferedWriter(
                new FileWriter(putanja, false))) {

            // Zaglavlje
            bw.write("IMO broj,Naziv plovila,Ulazak,Izlazak,"
                    + "Trajanje (h),Taksa (KM)");
            bw.newLine();

            for (Map.Entry<String, EvidencijaUlaska> entry
                    : luka.getEvidencijaUlazaka().entrySet()) {

                EvidencijaUlaska ev = entry.getValue();
                if (ev.getVrijemeIzlaska() == null) continue; // samo plovila koja su stvarno izašla

                long minuta = java.time.Duration
                        .between(ev.getVrijemeUlaska(), ev.getVrijemeIzlaska())
                        .toMinutes();
                double sati = minuta / 60.0;

                bw.write(String.join(",",
                        esc(ev.getImoBroj()),
                        esc(ev.getNazivPlovila()),
                        esc(ev.getVrijemeUlaska().format(FORMAT)),
                        esc(ev.getVrijemeIzlaska().format(FORMAT)),
                        String.format(Locale.US, "%.2f", sati),
                        String.format(Locale.US, "%.2f", ev.izracunajTaksu())
                ));
                bw.newLine();
            }

            // Ukupan red na dnu
            double ukupno = luka.getEvidencijaUlazaka().values().stream()
                    .filter(ev -> ev.getVrijemeIzlaska() != null)
                    .mapToDouble(EvidencijaUlaska::izracunajTaksu)
                    .sum();

            bw.write(",,,,UKUPNO:,"
                    + String.format(Locale.US, "%.2f", ukupno));
            bw.newLine();
        }

        LoggerUtil.logInfo("CSV taksi izvezen: " + putanja);
    }

    public static String getSazetakNaplate(Luka luka) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Sažetak lučkih taksi ===\n\n");

        double ukupno = 0;
        int    brPlovila = 0;

        for (EvidencijaUlaska ev : luka.getEvidencijaUlazaka().values()) {
            if (ev.getVrijemeIzlaska() == null) continue; // samo plovila koja su stvarno izašla
            double taksa = ev.izracunajTaksu();
            if (taksa <= 0) continue; // izuzima državna

            sb.append(String.format("%-30s %8.2f KM%n",
                    ev.getNazivPlovila() + " [" + ev.getImoBroj() + "]",
                    taksa));
            ukupno += taksa;
            brPlovila++;
        }
        sb.append("\n").append("─".repeat(42)).append("\n");
        sb.append(String.format("%-30s %8.2f KM%n",
                "UKUPNO (" + brPlovila + " plovila):", ukupno));
        return sb.toString();
    }
    private static String esc(String s) {
        if (s == null) return "";
        return s.contains(",") ? "\"" + s + "\"" : s;
    }
}