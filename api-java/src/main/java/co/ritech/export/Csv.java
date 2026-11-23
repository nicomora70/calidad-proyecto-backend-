package co.ritech.export;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Minimal RFC 4180 CSV builder. Output starts with a UTF-8 BOM so Excel opens accents
 * correctly. Free-text cells that start with = + - @ are prefixed with ' to prevent
 * formula injection when the file is opened in a spreadsheet (OWASP CSV injection).
 */
final class Csv {

    private static final String BOM = "﻿";

    private final StringBuilder out = new StringBuilder(BOM);

    Csv(List<String> header) {
        row(header);
    }

    Csv row(List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(cells.get(i)));
        }
        out.append("\r\n");
        return this;
    }

    byte[] bytes() {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Marks a free-text value so it is neutralized if it looks like a formula. */
    static Text text(String value) {
        return new Text(value);
    }

    record Text(String value) {}

    private static String cell(Object value) {
        String s;
        if (value instanceof Text t) {
            s = t.value() == null ? "" : t.value();
            if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
                s = "'" + s;
            }
        } else {
            s = Objects.toString(value, "");
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
