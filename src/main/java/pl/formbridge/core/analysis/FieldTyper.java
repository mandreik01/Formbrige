package pl.formbridge.core.analysis;

import pl.formbridge.core.model.FieldSpec;
import pl.formbridge.core.model.FieldSpec.Type;

import java.util.Locale;
import java.util.regex.Pattern;

final class FieldTyper {

    private static final Pattern DATE = Pattern.compile("(?U)\\b(data|dnia|dzień|termin|od dnia|do dnia|rrrr)\\b");
    private static final Pattern NUMBER = Pattern.compile(
            "(?U)\\b(kwota|kwot[ay]|wysokość|ilość|liczba|liczb[ay]|powierzchnia|długość|szerokość|razem|m2|m²|zł|ha|"
                    + "hektar\\w*|procent|wiek|metraż|stawka|wartość|suma)\\b");
    private static final Pattern LONG_TEXT = Pattern.compile(
            "(?U)\\b(uzasadnienie|uzasadnij|opis|opisz|uwagi|wyjaśnienie|okoliczności|cel|pochodzenie|informacje dodatkowe|osiągnięcia|"
                    + "przebieg|charakterystyka)\\b");
    private static final Pattern FILE = Pattern.compile("(?U)\\b(załącz\\w*|kopi[aę]|skan|plik|odpis|zaświadczenie lekarskie)\\b");

    private FieldTyper() {
    }

    static Type typeFor(String label, int leaderLines) {
        String l = label.toLowerCase(Locale.ROOT);
        if (DATE.matcher(l).find() && !l.contains("miejsce") && !l.contains("numer")) {
            return Type.DATEPICKER;
        }
        if (LONG_TEXT.matcher(l).find() || leaderLines >= 3) {
            return Type.TEXTAREA;
        }
        if (NUMBER.matcher(l).find() && !l.contains("numer") && !l.contains("nr ")) {
            return Type.NUMBER;
        }
        return Type.TEXT_FIELD;
    }

    static boolean looksLikeAttachment(String label) {
        return FILE.matcher(label.toLowerCase(Locale.ROOT)).find();
    }

    static void addValidators(FieldSpec f) {
        String l = f.title.toLowerCase(Locale.ROOT);
        if (l.contains("pesel")) {
            f.validate("PESEL");
        }
        if (l.matches(".*\\bnip\\b.*")) {
            f.validate("NIP");
        }
        if (l.contains("regon")) {
            f.validate("REGON");
        }
        if (l.contains("e-mail") || l.contains("email") || l.contains("poczty elektronicznej")) {
            f.validate("EMAIL");
        }
        if (l.contains("kod pocztowy")) {
            f.validate("ZIP_CODE");
        }
        if (l.contains("epuap")) {
            f.validate("EPUAP");
        }
        if (l.contains("e-doręcze") || l.contains("edoręcze") || l.contains("doręczeń")) {
            f.validate("ADE");
        }
        if (l.contains("dowodu osobistego") || l.contains("seria i numer dowodu")) {
            f.validate("SERIAL_NUMBER_ID_CARD");
        }
    }

    static boolean isOptional(String text) {
        String l = text.toLowerCase(Locale.ROOT);
        return l.contains("opcjonaln") || l.contains("nieobowiązkow") || l.contains("jeśli ") || l.contains("jeżeli ")
                || l.contains("w przypadku") || l.contains("**");
    }
}
