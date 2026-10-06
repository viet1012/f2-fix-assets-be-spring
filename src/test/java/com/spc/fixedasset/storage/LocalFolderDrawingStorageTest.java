package com.spc.fixedasset.storage;

import com.spc.fixedasset.exception.ServiceUnavailableException;
import com.spc.fixedasset.storage.DrawingStorage.StoredDrawing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class LocalFolderDrawingStorageTest {

    @TempDir
    Path root;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};

    private Path folderWithSpace() throws IOException {
        return Files.createDirectory(root.resolve("OneDrive - SPC Drawings"));
    }

    private static long files(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    @Test
    void writesAtomicallyAndOverwritesWithoutLeavingTmp() throws IOException {
        Path dir = folderWithSpace();
        LocalFolderDrawingStorage storage = new LocalFolderDrawingStorage(dir.toString(), "https://spc.sharepoint.com/sites/F2/Drawings/");
        StoredDrawing first = storage.save("261001-093000_RL-2026-0001_Fac A_A15-3.png", PNG);
        assertEquals("https://spc.sharepoint.com/sites/F2/Drawings/261001-093000_RL-2026-0001_Fac%20A_A15-3.png", first.webUrl());

        byte[] second = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 9};
        storage.save("261001-093000_RL-2026-0001_Fac A_A15-3.png", second);
        assertArrayEquals(second, Files.readAllBytes(dir.resolve("261001-093000_RL-2026-0001_Fac A_A15-3.png")));
        assertFalse(Files.exists(dir.resolve("261001-093000_RL-2026-0001_Fac A_A15-3.png.tmp")));
        assertEquals(1, files(dir));
    }

    @Test
    void webUrlIsNullWithoutBaseUrl() throws IOException {
        assertNull(new LocalFolderDrawingStorage(folderWithSpace().toString(), " ").save("a.png", PNG).webUrl());
    }

    @Test
    void rejectsPathTraversalAndUnsafeNames() throws IOException {
        Path dir = folderWithSpace();
        LocalFolderDrawingStorage storage = new LocalFolderDrawingStorage(dir.toString(), null);
        for (String bad : new String[]{"../evil.png", "..\\evil.png", "sub/evil.png", "C:evil.png", ".hidden.png", "a..png", "evil.exe", "a#b.png", " a.png", null}) {
            assertThrows(IllegalArgumentException.class, () -> storage.save(bad, PNG), String.valueOf(bad));
        }
        assertEquals(0, files(dir));
        assertEquals(1, files(root));
    }

    @Test
    void savesXlsxOnlyWithMatchingContentType() throws IOException {
        Path dir = folderWithSpace();
        LocalFolderDrawingStorage storage = new LocalFolderDrawingStorage(dir.toString(), null);
        assertEquals("R0001_261006-091432.xlsx", storage.save("R0001_261006-091432.xlsx", new byte[]{1}, DrawingStorage.XLSX).fileName());
        assertThrows(IllegalArgumentException.class, () -> storage.save("R0001.png", new byte[]{1}, DrawingStorage.XLSX));
        assertThrows(IllegalArgumentException.class, () -> storage.save("R0001.xlsx", PNG));
        assertThrows(IllegalArgumentException.class, () -> storage.save("R0001.xlsx", new byte[]{1}, "text/plain"));
        assertEquals(1, files(dir));
    }

    @Test
    void missingOrUnusableFolderIs503() {
        assertThrows(ServiceUnavailableException.class, () -> new LocalFolderDrawingStorage(null, null).save("a.png", PNG));
        assertThrows(ServiceUnavailableException.class, () -> new LocalFolderDrawingStorage(root.resolve("nope").toString(), null).save("a.png", PNG));
    }
}
