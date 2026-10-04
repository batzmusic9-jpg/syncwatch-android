package com.batz.syncwatch;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shift only cue timing lines; subtitle text and VTT cue settings remain intact. */
public final class SubtitleTiming {
    private static final Pattern CUE = Pattern.compile("(?m)^(\\d{1,3}:\\d{2}:\\d{2}[,.]\\d{3}|\\d{2}:\\d{2}\\.\\d{3})([ \\t]+-->[ \\t]+)(\\d{1,3}:\\d{2}:\\d{2}[,.]\\d{3}|\\d{2}:\\d{2}\\.\\d{3})");
    private SubtitleTiming() { }
    public static String shift(String source, long offset) {
        Matcher matcher = CUE.matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String start = time(matcher.group(1), offset);
            String end = time(matcher.group(3), offset);
            matcher.appendReplacement(result, Matcher.quoteReplacement(start + matcher.group(2) + end));
        }
        matcher.appendTail(result);
        return result.toString();
    }
    private static String time(String value, long offset) {
        String[] parts = value.replace(',', '.').split("[:.]");
        int hours = parts.length == 4 ? Integer.parseInt(parts[0]) : 0;
        int index = parts.length == 4 ? 1 : 0;
        long millis = Math.max(0, (hours * 3600L + Integer.parseInt(parts[index]) * 60L
                + Integer.parseInt(parts[index + 1])) * 1000 + Integer.parseInt(parts[index + 2]) + offset);
        return String.format(Locale.ROOT, "%02d:%02d:%02d%c%03d", millis / 3600000,
                millis / 60000 % 60, millis / 1000 % 60, value.contains(",") ? ',' : '.', millis % 1000);
    }
}
