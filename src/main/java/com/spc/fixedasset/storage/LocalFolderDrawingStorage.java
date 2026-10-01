package com.spc.fixedasset.storage;

import com.spc.fixedasset.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Drawings in a local folder (normally a synced OneDrive/SharePoint folder). Each file is written to "&lt;name&gt;.tmp"
 * in the same folder and then moved over the target, so the sync client never picks up a half-written file.
 */
public class LocalFolderDrawingStorage implements DrawingStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFolderDrawingStorage.class);
    /** Path separators, Windows/OneDrive reserved characters and control characters. */
    private static final Pattern UNSAFE = Pattern.compile("[\\\\/:*?\"<>|#%\\p{Cntrl}]");

    private final String dir;
    private final String baseUrl;
    private final AtomicBoolean warned = new AtomicBoolean();

    /**
     * dir = drawings.dir, baseUrl = drawings.base-url (optional); both may be null. Logs one startup line with the folder
     * and whether it is writable (WARN when unusable, which also counts as the one warning).
     */
    public LocalFolderDrawingStorage(String dir, String baseUrl) {
        this.dir = dir == null || dir.isBlank() ? null : dir.trim();
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? null : baseUrl.trim().replaceAll("/+$", "");
        String problem = problem();
        if (problem == null) {
            log.info("Bản vẽ di dời: thư mục {} (ghi được)", this.dir);
        } else {
            warned.set(true);
            log.warn("Bản vẽ di dời: thư mục {} (không ghi được: {})", this.dir == null ? "-" : this.dir, problem);
        }
    }

    @Override
    public StoredDrawing save(String fileName, byte[] png) {
        if (!isSafeName(fileName)) throw new IllegalArgumentException("Tên file không hợp lệ: " + fileName);
        Path folder = folder();
        Path target = folder.resolve(fileName).normalize();
        if (!folder.equals(target.getParent())) throw new IllegalArgumentException("Tên file không hợp lệ: " + fileName);
        Path tmp = folder.resolve(fileName + ".tmp");
        try {
            Files.write(tmp, png, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
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
            log.warn("Không ghi được bản vẽ {} vào thư mục lưu bản vẽ: {}", fileName, e.toString());
            throw new ServiceUnavailableException("Không ghi được bản vẽ vào thư mục lưu trữ.", e);
        }
        return new StoredDrawing(fileName, webUrl(fileName));
    }

    /** One file name, no separators/reserved characters, not "." / ".." / hidden, ends with ".png". */
    static boolean isSafeName(String name) {
        return name != null && !name.isBlank() && name.equals(name.trim()) && !name.startsWith(".") && !name.contains("..")
                && !UNSAFE.matcher(name).find() && name.toLowerCase().endsWith(".png");
    }

    String webUrl(String fileName) {
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
                problem = "Không đọc được thư mục lưu bản vẽ.";
            }
        }
        if (warned.compareAndSet(false, true)) log.warn("Lưu bản vẽ di dời không dùng được: {} (thư mục {})", problem, dir);
        throw new ServiceUnavailableException(problem);
    }

    /** Why the folder cannot be used, or null when it can. */
    private String problem() {
        if (dir == null) return "Chưa cấu hình drawings.dir.";
        Path p;
        try {
            p = Paths.get(dir);
        } catch (InvalidPathException e) {
            return "Đường dẫn drawings.dir không hợp lệ.";
        }
        if (!Files.isDirectory(p)) return "Thư mục lưu bản vẽ không tồn tại.";
        if (!Files.isWritable(p)) return "Không ghi được vào thư mục lưu bản vẽ.";
        return null;
    }
}
