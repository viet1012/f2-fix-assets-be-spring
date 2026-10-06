package com.spc.fixedasset.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Creater of a relocation request: "{account}_{name}" built by the backend from the session (e.g. "22847_Tạ Hoàng Tuấn Việt").
 * Rows written before this format hold the bare account (no "_").
 */
public final class CreaterFormat {

    public static final String UNKNOWN = "Unknown";
    private static final String SEP = "_";

    private CreaterFormat() {}

    /** No length limit. */
    public static String format(String account, String name) {
        return format(account, name, null);
    }

    /**
     * account trimmed; name trimmed with inner whitespace collapsed, "Unknown" when blank. When the result is longer than
     * maxLength (column length; null/negative = no limit) only the name is cut, the account and "_" are kept whole.
     */
    public static String format(String account, String name, Integer maxLength) {
        String acc = account == null ? "" : account.trim();
        String n = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (n.isEmpty()) n = UNKNOWN;
        String prefix = acc + SEP;
        if (maxLength != null && maxLength >= 0 && prefix.length() + n.length() > maxLength) {
            n = n.substring(0, Math.max(0, maxLength - prefix.length())).trim();
        }
        return prefix + n;
    }

    /** Part before the first "_" (trimmed); the whole value for old rows without "_"; null when blank. */
    public static String accountOf(String creater) {
        String c = clean(creater);
        if (c == null) return null;
        int i = c.indexOf(SEP);
        return i < 0 ? c : clean(c.substring(0, i));
    }

    /** Part after the first "_"; null for old rows without "_" and for "Unknown". */
    public static String nameOf(String creater) {
        String c = clean(creater);
        if (c == null) return null;
        int i = c.indexOf(SEP);
        if (i < 0) return null;
        String n = clean(c.substring(i + 1));
        return n == null || n.equalsIgnoreCase(UNKNOWN) ? null : n;
    }

    /** True for "{account}_{name}" values, false for old rows holding only the account. */
    public static boolean hasName(String creater) {
        String c = clean(creater);
        return c != null && c.contains(SEP);
    }

    /** Accounts of the old rows, which still need an F2_HR_Data lookup for their name. */
    public static List<String> legacyAccounts(Collection<String> creaters) {
        return creaters.stream().filter(c -> !hasName(c)).map(CreaterFormat::accountOf).filter(Objects::nonNull).distinct().toList();
    }

    /** Name stored in Creater, or for old rows the HR name of the account (hrNames keyed by account, case-insensitive). */
    public static String requesterName(String creater, Map<String, String> hrNames) {
        if (hasName(creater)) return nameOf(creater);
        String acc = accountOf(creater);
        return acc == null ? null : hrNames.get(acc);
    }

    private static String clean(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
