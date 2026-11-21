package pl.formbridge.app.web;

import org.springframework.http.ProblemDetail;

import java.util.List;

public final class ProblemJson {

    private ProblemJson() {
    }

    public static String write(ProblemDetail problem) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"type\":").append(quote(problem.getType() == null ? "about:blank" : problem.getType().toString()));
        sb.append(",\"title\":").append(quote(problem.getTitle()));
        sb.append(",\"status\":").append(problem.getStatus());
        sb.append(",\"detail\":").append(quote(problem.getDetail()));
        if (problem.getInstance() != null) {
            sb.append(",\"instance\":").append(quote(problem.getInstance().toString()));
        }
        return sb.append('}').toString();
    }

    public static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    public static String array(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return "[" + String.join(",", values.stream().map(ProblemJson::quote).toList()) + "]";
    }
}
