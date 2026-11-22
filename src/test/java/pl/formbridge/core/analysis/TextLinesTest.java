package pl.formbridge.core.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextLinesTest {

    @Test
    void ocrGarbageInsteadOfDotsBecomesLeader() {
        List<String> lines = TextLines.normalize("Szerokość Eeeeeeeeeee« m\nDługość cccccccccc\nAdres ,.,.,.,.,.,.");

        assertEquals(3, lines.size());
        lines.forEach(l -> assertTrue(TextLines.LEADER.matcher(l).find(), l));
        assertTrue(lines.get(0).startsWith("Szerokość"));
    }

    @Test
    void ocrNoiseFromCheckboxesIsDropped() {
        assertEquals(List.of("Korekta"), TextLines.normalize("Korekta ĄZKO›ŻOHGJ:›"));
    }

    @Test
    void ocrLetterQInsteadOfCheckboxBecomesOptions() {
        assertEquals(List.of("Płeć:", "\u2751 pies", "\u2751 suka"), TextLines.normalize("Płeć: Q pies Q suka"));
    }

    @Test
    void ordinaryWordsStayUntouched() {
        assertEquals(List.of("Kooperacja w biurze, e-mail: a@b.pl"), TextLines.normalize("Kooperacja w biurze, e-mail: a@b.pl"));
    }
}
