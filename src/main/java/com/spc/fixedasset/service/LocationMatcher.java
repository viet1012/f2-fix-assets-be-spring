package com.spc.fixedasset.service;

import com.spc.fixedasset.model.LocationMapRow;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Pure-Java zone normalization and MAP row selection (no Spring, no DB). */
public final class LocationMatcher {

    public enum MatchLevel { SUB, MAJOR, NONE }

    public record Match(LocationMapRow row, MatchLevel level) {
        static final Match NONE = new Match(null, MatchLevel.NONE);
    }

    /** "A-15" -> "A15", "A-15-3" -> "A15-3": drop only the hyphen between the leading letters and the first digit. */
    private static final Pattern PREFIX_HYPHEN = Pattern.compile("^([A-Z]+)-(?=\\d)");

    private LocationMatcher() {}

    public static String normalize(String zone) {
        if (zone == null) return null;
        String v = zone.trim().toUpperCase(Locale.ROOT);
        if (v.isEmpty()) return null;
        return PREFIX_HYPHEN.matcher(v).replaceFirst("$1");
    }

    /** Normalized AA, or null when it is blank or repeats A (e.g. "A9"/"A9"). */
    public static String normalizeSub(String a, String aa) {
        String na = normalize(a), naa = normalize(aa);
        return naa != null && naa.equals(na) ? null : naa;
    }

    /** Sub-zone first, then the major zone row (AA empty); same Div wins, then lowest Id. Floor is never used. */
    public static Match match(String positionA, String positionAA, String div, List<LocationMapRow> rows) {
        String a = normalize(positionA);
        if (a == null) return Match.NONE;
        String aa = normalizeSub(positionA, positionAA);
        if (aa != null) {
            Optional<LocationMapRow> sub = best(rows, div, r -> a.equals(normalize(r.a())) && aa.equals(normalizeSub(r.a(), r.aa())));
            if (sub.isPresent()) return new Match(sub.get(), MatchLevel.SUB);
        }
        return best(rows, div, r -> a.equals(normalize(r.a())) && normalizeSub(r.a(), r.aa()) == null)
                .map(r -> new Match(r, MatchLevel.MAJOR))
                .orElse(Match.NONE);
    }

    /** Zone the asset is currently in: the matched zone, otherwise its own normalized AA/A. */
    public static String currentZone(String positionA, String positionAA, Match match) {
        return switch (match.level()) {
            case SUB -> normalizeSub(match.row().a(), match.row().aa());
            case MAJOR -> normalize(match.row().a());
            case NONE -> {
                String aa = normalizeSub(positionA, positionAA);
                yield aa != null ? aa : normalize(positionA);
            }
        };
    }

    /** True only when both floors are known and differ (trim/case-insensitive). */
    public static boolean floorMismatch(String assetFloor, Match match) {
        if (match.row() == null) return false;
        String f1 = clean(assetFloor), f2 = clean(match.row().floor());
        return f1 != null && f2 != null && !f1.equalsIgnoreCase(f2);
    }

    /** Number when the stored position parses as one, otherwise the original string. */
    public static Object parsePos(String raw) {
        String v = clean(raw);
        if (v == null) return null;
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    public static boolean sameText(String x, String y) {
        String a = clean(x), b = clean(y);
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }

    private static Optional<LocationMapRow> best(List<LocationMapRow> rows, String div, java.util.function.Predicate<LocationMapRow> filter) {
        return rows.stream().filter(filter)
                .min(Comparator.comparingInt((LocationMapRow r) -> sameText(r.div(), div) ? 0 : 1)
                        .thenComparingLong(LocationMapRow::id));
    }

    private static String clean(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
