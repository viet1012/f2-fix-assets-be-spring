package com.spc.fixedasset.storage;

import com.spc.fixedasset.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Relocation files (drawings or Excel exports, one instance per folder) in a local folder (normally a synced OneDrive/SharePoint folder). Each file is written to "&lt;name&gt;.tmp"
 * in the same folder and then moved over the target, so the sync client never picks up a half-written file.
 */
public class LocalFolderDrawingStorage implements DrawingStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFolderDrawingStorage.class);
    /** Path separators, Windows/OneDrive reserved characters and control characters. */
    private static final Pattern UNSAFE = Pattern.compile("[\\\\/:*?\"<>|#%\\p{Cntrl}]");
    /** Accepted content types and the extension their file name must end with. */
    private static final Map<String, String> EXTENSIONS = Map.of(PNG, ".png", XLSX, ".xlsx");

    private final Kind kind;
    private final String dir;
    private final String baseUrl;
    private final AtomicBoolean warned = new AtomicBoolean();

    /** What the folder holds: name used in log lines and messages, and the property that configures it. */
    public enum Kind {
        DRAWINGS("Bản vẽ di dời", "bản vẽ", "drawings.dir"),
        EXCEL("Excel di dời", "Excel", "drawings.excel-dir");

        final String label, noun, property;

        Kind(String label, String noun, String property) {
            this.label = label;
            this.noun = noun;
            this.property = property;
        }
    }

    /** Drawings folder: dir = drawings.dir, baseUrl = drawings.base-url. */
    public LocalFolderDrawingStorage(String dir, String baseUrl) {
        this(Kind.DRAWINGS, dir, baseUrl);
    }

    /**
     * dir and baseUrl (optional) may be null. Logs one startup line with the folder and whether it is writable (WARN when
     * unusable, which also counts as the one warning); never the base URL.
     */
    public LocalFolderDrawingStorage(Kind kind, String dir, String baseUrl) {
        this.kind = kind;
        this.dir = dir == null || dir.isBlank() ? null : dir.trim();
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? null : baseUrl.trim().replaceAll("/+$", "");
        String problem = problem();
        if (problem == null) {
            log.info("{}: thư mục {} (ghi được)", kind.label, this.dir);
        } else {
            warned.set(true);
            log.warn("{}: thư mục {} (không ghi được: {})", kind.label, this.dir == null ? "-" : this.dir, problem);
        }
    }

    @Override
    public StoredDrawing save(String fileName, byte[] content, String contentType) {
        if (!isSafeName(fileName, contentType)) throw new IllegalArgumentException("Tên file không hợp lệ: " + fileName);
        Path folder = folder();
        Path target = folder.resolve(fileName).normalize();
        if (!folder.equals(target.getParent())) throw new IllegalArgumentException("Tên file không hợp lệ: " + fileName);
        Path tmp = folder.resolve(fileName + ".tmp");
        try {
            Files.write(tmp, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // best effort
            }
            log.warn("Không ghi được file {} vào thư mục lưu {}: {}", fileName, kind.noun, e.toString());
            throw new ServiceUnavailableException("Không ghi được file vào thư mục lưu trữ.", e);
        }
        return new StoredDrawing(fileName, webUrl(fileName));
    }

    /** One file name, no separators/reserved characters, not "." / ".." / hidden, extension matching the content type. */
    static boolean isSafeName(String name, String contentType) {
        String ext = EXTENSIONS.get(contentType);
        return ext != null && name != null && !name.isBlank() && name.equals(name.trim()) && !name.startsWith(".") && !name.contains("..")
                && !UNSAFE.matcher(name).find() && name.toLowerCase(Locale.ROOT).endsWith(ext);
    }

    @Override
    public String webUrl(String fileName) {
        if (baseUrl == null) return null;
        return baseUrl + "/" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** The configured folder (real path), or 503; only the first failure is logged (the startup line included). */
    private Path folder() {
        String problem = problem();
        if (problem == null) {
            try {
                return Paths.get(dir).toRealPath();
            } catch (IOException e) {
                problem = "Không đọc được thư mục lưu " + kind.noun + ".";
            }
        }
        if (warned.compareAndSet(false, true)) log.warn("Lưu {} không dùng được: {} (thư mục {})", kind.label, problem, dir);
        throw new ServiceUnavailableException(problem);
    }

    /** Why the folder cannot be used, or null when it can. */
    private String problem() {
        if (dir == null) return "Chưa cấu hình " + kind.property + ".";
        Path p;
        try {
            p = Paths.get(dir);
        } catch (InvalidPathException e) {
            return "Đường dẫn " + kind.property + " không hợp lệ.";
        }
        if (!Files.isDirectory(p)) return "Thư mục lưu " + kind.noun + " không tồn tại.";
        if (!Files.isWritable(p)) return "Không ghi được vào thư mục lưu " + kind.noun + ".";
        return null;
    }
}
