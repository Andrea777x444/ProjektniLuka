package ba.etf.luka.util;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

/**
 * Sve greške idu u error.log, info poruke na konzolu.
 */
public class LoggerUtil {

    private static final Logger LOGGER =
            Logger.getLogger("LukaApp");
    private static final String PUTANJA_LOGA = "error.log";
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static {
        try {

            LOGGER.setUseParentHandlers(false);

            FileHandler fileHandler = new FileHandler(
                    PUTANJA_LOGA, true);
            fileHandler.setLevel(Level.WARNING);
            fileHandler.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format("[%s] [%s] %s%n",
                            LocalDateTime.now().format(FORMAT),
                            record.getLevel(),
                            record.getMessage()
                                    + (record.getThrown() != null
                                    ? " | " + record.getThrown().getMessage()
                                    : ""));
                }
            });


            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.ALL);
            consoleHandler.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format("[%s] %s%n",
                            record.getLevel().getName(),
                            record.getMessage());
                }
            });

            LOGGER.addHandler(fileHandler);
            LOGGER.addHandler(consoleHandler);
            LOGGER.setLevel(Level.ALL);

        } catch (IOException e) {
            System.err.println("Logger nije inicijalizovan: "
                    + e.getMessage());
        }
    }

    private LoggerUtil() {}

    public static void logGreska(String poruka, Throwable t) {
        LOGGER.log(Level.SEVERE, poruka, t);
    }

    public static void logUpozorenje(String poruka) {
        LOGGER.log(Level.WARNING, poruka);
    }

    public static void logInfo(String poruka) {
        LOGGER.log(Level.INFO, poruka);
    }

    public static void logDebug(String poruka) {
        LOGGER.log(Level.FINE, poruka);
    }

    public static String getPutanjaLoga() { return PUTANJA_LOGA; }
}