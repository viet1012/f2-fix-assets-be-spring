package com.spc.fixedasset.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Shared base name of the files of one relocation request (drawing .png, export .xlsx). */
public final class RelocationFileNames {

    static final ZoneId FILE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyMMdd-HHmmss");

    private RelocationFileNames() {}

    /** {RequestNo}_{yyMMdd-HHmmss}; CreateDate is written in the JVM zone (LocalDateTime.now()). */
    public static String baseName(String requestNo, LocalDateTime createDate) {
        return baseName(requestNo, createDate, ZoneId.systemDefault());
    }

    /** Same, with the zone CreateDate was written in; the stamp is always Asia/Ho_Chi_Minh time. */
    static String baseName(String requestNo, LocalDateTime createDate, ZoneId createDateZone) {
        String stamp = createDate == null ? "NA" : createDate.atZone(createDateZone).withZoneSameInstant(FILE_ZONE).format(STAMP);
        return part(requestNo) + "_" + stamp;
    }

    /** Windows/OneDrive reserved characters (\ / : * ? " < > | # %), controls and whitespace become "-"; no leading/trailing dots. */
    static String part(String v) {
        if (v == null || v.isBlank()) return "NA";
        String s = v.trim().replaceAll("[\\\\/:*?\"<>|#%\\p{Cntrl}\\s]", "-").replaceAll("^\\.+|\\.+$", "");
        return s.isEmpty() ? "NA" : s;
    }
}
