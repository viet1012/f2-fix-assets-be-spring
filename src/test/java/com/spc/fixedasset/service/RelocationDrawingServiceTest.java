package com.spc.fixedasset.service;

import com.spc.fixedasset.dto.DrawingUploadResponse;
import com.spc.fixedasset.exception.BadRequestException;
import com.spc.fixedasset.exception.NotFoundException;
import com.spc.fixedasset.exception.ServiceUnavailableException;
import com.spc.fixedasset.model.HistoryTableInfo;
import com.spc.fixedasset.model.RelocationHistoryRow;
import com.spc.fixedasset.repository.RelocationRequestRepository;
import com.spc.fixedasset.storage.LocalFolderDrawingStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RelocationDrawingServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};

    @TempDir
    Path dir;

    private final RelocationRequestRepository repo = mock(RelocationRequestRepository.class);
    private final RelocationExcelService excel = mock(RelocationExcelService.class);

    private static RelocationHistoryRow row(String machine) {
        return new RelocationHistoryRow(1L, "RL-2026-0001", machine, "A2", "A2-3", null, "G", "pic",
                "A15", "A15-3", null, "G", "pic", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6),
                LocalDateTime.of(2026, 10, 1, 2, 30, 5), "E001", "r", "REQ_PENDING", null);
    }

    private RelocationDrawingService service(String baseUrl) {
        return new RelocationDrawingService(repo, new LocalFolderDrawingStorage(dir.toString(), baseUrl), excel, ZoneOffset.UTC);
    }

    @BeforeEach
    void setUp() {
        when(repo.findRows(List.of("RL-2026-0001"))).thenReturn(List.of(row("M1"), row("M2")));
        when(repo.findRows(List.of("RL-2026-9999"))).thenReturn(List.of());
        when(repo.tableInfo()).thenReturn(new HistoryTableInfo(Map.of("drawings", 255), true));
    }

    @Test
    void validatesPngMagicAndSize() {
        RelocationDrawingService.validatePng(PNG);
        assertThrows(BadRequestException.class, () -> RelocationDrawingService.validatePng(new byte[0]));
        assertThrows(BadRequestException.class, () -> RelocationDrawingService.validatePng("GIF89a....".getBytes()));
        byte[] big = new byte[(int) RelocationDrawingService.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThrows(BadRequestException.class, () -> RelocationDrawingService.validatePng(big));
    }

    @Test
    void savesFileAndUpdatesDrawingsWithWebUrl() {
        DrawingUploadResponse res = service("https://spc.sharepoint.com/Drawings").upload("RL-2026-0001", PNG);
        assertEquals("RL-2026-0001_261001-093005.png", res.fileName());
        assertEquals("https://spc.sharepoint.com/Drawings/RL-2026-0001_261001-093005.png", res.webUrl());
        assertTrue(Files.exists(dir.resolve(res.fileName())));
        // Excel is rewritten after Drawings is updated, so DrawingFile carries the image name.
        var order = inOrder(repo, excel);
        order.verify(repo).updateDrawings("RL-2026-0001", res.webUrl());
        order.verify(excel).tryExport("RL-2026-0001");
    }

    @Test
    void storesFileNameWithoutBaseUrlOrWhenUrlIsTooLong() {
        DrawingUploadResponse res = service(null).upload("RL-2026-0001", PNG);
        assertNull(res.webUrl());
        verify(repo).updateDrawings("RL-2026-0001", res.fileName());

        when(repo.tableInfo()).thenReturn(new HistoryTableInfo(Map.of("drawings", 40), true));
        DrawingUploadResponse longUrl = service("https://spc.sharepoint.com/sites/very/long/path").upload("RL-2026-0001", PNG);
        assertNotNull(longUrl.webUrl());
        verify(repo, times(2)).updateDrawings("RL-2026-0001", longUrl.fileName());
    }

    @Test
    void reuploadOverwritesTheSameFile() throws IOException {
        RelocationDrawingService s = service(null);
        s.upload("RL-2026-0001", PNG);
        byte[] v2 = PNG.clone();
        v2[PNG.length - 1] = 42;
        DrawingUploadResponse res = s.upload("RL-2026-0001", v2);
        assertArrayEquals(v2, Files.readAllBytes(dir.resolve(res.fileName())));
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void unknownRequestIs404AndBadPngIs400WithoutWriting() throws IOException {
        assertThrows(NotFoundException.class, () -> service(null).upload("RL-2026-9999", PNG));
        assertThrows(BadRequestException.class, () -> service(null).upload("RL-2026-0001", "not a png".getBytes()));
        try (var files = Files.list(dir)) {
            assertEquals(0, files.count());
        }
        verify(repo, never()).updateDrawings(anyString(), anyString());
    }

    @Test
    void missingFolderIs503AndDrawingsUntouched() {
        RelocationDrawingService s = new RelocationDrawingService(repo, new LocalFolderDrawingStorage(null, null), excel, ZoneOffset.UTC);
        assertThrows(ServiceUnavailableException.class, () -> s.upload("RL-2026-0001", PNG));
        verify(repo, never()).updateDrawings(anyString(), anyString());
        verify(excel, never()).tryExport(anyString());
    }
}
