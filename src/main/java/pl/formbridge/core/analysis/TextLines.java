package pl.formbridge.core.analysis;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TextLines {

    // wykropkowanie do wpisania: min. 4 kropki/podkreślenia (wielokropek zamieniamy wcześniej na 4 kropki)
    static final Pattern LEADER = Pattern.compile("(?:[._][ \\t]?){4,}[ \\t]*");
    static final Pattern CHECKBOX = Pattern.compile("^[\\u25A1\\u25A0\\u25AB\\u25AA\\u25CB\\u25CF\\u25EF\\u2610\\u2611\\u2612"
            + "\\u274F\\u2750\\u2751\\u2752\\u274E\\uF000-\\uF0FF\\u2022\\u25E6\\u2023\\u2043\\u2219]\\s*");

    private TextLines() {
    }

    static List<String> normalize(String text) {
        String t = Normalizer.normalize(text, Normalizer.Form.NFC)
                .replace("\u00AD", "")
                .replace("\uFFFD", "")
                .replace('\u00A0', ' ')
                .replace("…", "....")
                .replace('\t', ' ')
                .replace('\f', '\n');
        List<String> out = new ArrayList<>();
        for (String raw : t.split("\\R")) {
            String line = raw.replaceAll("[ ]{2,}", " ").strip();
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return joinContinuations(out);
    }

    private static List<String> joinContinuations(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (!out.isEmpty()) {
                String prev = out.get(out.size() - 1);
                boolean continuation = Character.isLowerCase(line.codePointAt(0))
                        && !hasLeader(prev) && !hasLeader(line)
                        && !CHECKBOX.matcher(line).find() && !CHECKBOX.matcher(prev).find()
                        && !prev.endsWith(":") && !prev.endsWith(".") && !isHint(line) && !line.endsWith(":")
                        && prev.length() > 40;
                if (continuation) {
                    out.set(out.size() - 1, prev + " " + line);
                    continue;
                }
            }
            out.add(line);
        }
        return out;
    }

    static boolean hasLeader(String line) {
        return LEADER.matcher(line).find();
    }

    static boolean isLeaderOnly(String line) {
        String rest = LEADER.matcher(line).replaceAll(" ").replaceAll("[\\s,;.\\-`']", "");
        return hasLeader(line) && rest.length() <= 3;
    }

    static boolean isHint(String line) {
        String l = line.strip();
        return (l.startsWith("(") && l.endsWith(")")) || (l.startsWith("/") && l.endsWith("/") && l.length() > 2);
    }

    static String stripHint(String line) {
        return line.strip().replaceAll("^[(/]\\s*", "").replaceAll("\\s*[)/]$", "").strip();
    }

    static List<String> segmentsBeforeLeaders(String line) {
        List<String> labels = new ArrayList<>();
        Matcher m = LEADER.matcher(line);
        int last = 0;
        while (m.find()) {
            labels.add(line.substring(last, m.start()).strip());
            last = m.end();
        }
        return labels;
    }

    static String tailAfterLastLeader(String line) {
        Matcher m = LEADER.matcher(line);
        int end = -1;
        while (m.find()) {
            end = m.end();
        }
        return end < 0 ? "" : line.substring(end).strip();
    }

    static String withoutCheckbox(String line) {
        return CHECKBOX.matcher(line).replaceFirst("").strip();
    }
}
