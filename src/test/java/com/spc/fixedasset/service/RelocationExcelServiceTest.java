package com.spc.fixedasset.service;

import com.spc.fixedasset.auth.HrRepository;
import com.spc.fixedasset.exception.ServiceUnavailableException;
import com.spc.fixedasset.model.LocationMapRow;
import com.spc.fixedasset.model.RelocationHistoryRow;
import com.spc.fixedasset.repository.LocationRepository;
import com.spc.fixedasset.repository.RelocationRequestRepository;
import com.spc.fixedasset.storage.DrawingStorage;
import com.spc.fixedasset.storage.LocalFolderDrawingStorage;
import com.spc.fixedasset.storage.LocalFolderDrawingStorage.Kind;
import com.spc.fixedasset.dto.DrawingUploadResponse;
import com.spc.fixedasset.model.HistoryTableInfo;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RelocationExcelServiceTest {

    @TempDir
    Path dir;

    private final RelocationRequestRepository repo = mock(RelocationRequestRepository.class);
    private final LocationRepository locations = mock(LocationRepository.class);
    private final HrRepository hr = mock(HrRepository.class);

    private static final List<LocationMapRow> MAP = List.of(
            new LocationMapRow(1, "Fac_A", "PRESS", "1F", "A2", "A2-3", null, null),
            new LocationMapRow(2, "Fac_B", "PRESS", "2F", "A15", "A15-3", null, null));

    private static RelocationHistoryRow row(String machine, String drawings) {
        return new RelocationHistoryRow(1L, "R0001", machine, "A2", "A2-3", null, "G", "pic",
                "A15", "A15-3", null, "G", "pic", LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 9),
                LocalDateTime.of(2026, 10, 6, 9, 14, 32), " E001 ", "Thay đổi bố trí", "REQ_PENDING", drawings);
    }

    private static RelocationHistoryRow withCreater(RelocationHistoryRow r, String creater) {
        return new RelocationHistoryRow(r.id(), r.requestNo(), r.machineCode(), r.positionABf(), r.positionAABf(), r.positionAAABf(),
                r.groupBf(), r.picBf(), r.positionAAt(), r.positionAAAt(), r.positionAAAAt(), r.groupAt(), r.picAt(),
                r.plannedMoveDate(), r.plannedDoneDate(), r.createDate(), creater, r.note(), r.status(), r.drawings());
    }

    @BeforeEach
    void setUp() {
        when(repo.findRows(List.of("R0001"))).thenReturn(List.of(row("M1", null), row("M2", null)));
        when(repo.findMachineNames(anyCollection())).thenReturn(Map.of("M1", "Máy ép 1"));
        when(locations.findMapRows()).thenReturn(MAP);
        when(hr.findNames(anyCollection())).thenReturn(Map.of("E001", "Nguyễn Văn A"));
    }

    @Test
    void workbookHasOneTable1WithOneRowPerMachineAndRealDates() throws IOException {
        byte[] bytes = RelocationExcelService.build(
                List.of(row("M1", "https://spc.sharepoint.com/Drawings/R0001_261006-091432.png"), row("M2", null), withCreater(row("M3", null), "22847_Tạ Hoàng Tuấn Việt")),
                Map.of("M1", "Máy ép 1"), Map.of("E001", "Nguyễn Văn A"), MAP);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = wb.getSheet("Request");
            assertNotNull(sheet);
            assertEquals(1, sheet.getTables().size());
            XSSFTable table = sheet.getTables().get(0);
            assertEquals("Table1", table.getName());
            assertEquals("Table1", table.getDisplayName());
            assertEquals("TableStyleMedium2", table.getCTTable().getTableStyleInfo().getName());
            assertNotNull(table.getCTTable().getAutoFilter());
            assertEquals("A1:T4", table.getArea().formatAsString().replace("$", "").replaceAll("^.*!", ""));
            assertEquals(RelocationExcelService.COLUMNS.size(), table.getColumnCount());
            assertEquals(3, sheet.getLastRowNum());
            assertNotNull(sheet.getPaneInformation());
            assertTrue(sheet.getPaneInformation().isFreezePane());

            Row header = sheet.getRow(0);
            assertEquals("RequestNo", header.getCell(0).getStringCellValue());
            assertEquals("DrawingFile", header.getCell(19).getStringCellValue());

            Row first = sheet.getRow(1);
            assertEquals("R0001", first.getCell(0).getStringCellValue());
            assertEquals("Máy ép 1", first.getCell(2).getStringCellValue());
            assertEquals("Fac_A", first.getCell(3).getStringCellValue());
            assertEquals("1F", first.getCell(4).getStringCellValue());
            assertEquals("Fac_B", first.getCell(7).getStringCellValue());
            assertEquals("2F", first.getCell(8).getStringCellValue());
            assertEquals("building", first.getCell(11).getStringCellValue());
            assertEquals("Thay đổi bố trí", first.getCell(14).getStringCellValue());
            assertEquals("E001", first.getCell(15).getStringCellValue());
            assertEquals("Nguyễn Văn A", first.getCell(16).getStringCellValue());
            assertEquals("R0001_261006-091432.png", first.getCell(19).getStringCellValue());
            assertEquals("22847", sheet.getRow(3).getCell(15).getStringCellValue());
            assertEquals("Tạ Hoàng Tuấn Việt", sheet.getRow(3).getCell(16).getStringCellValue());

            for (int c : new int[]{12, 13, 17}) {
                Cell cell = first.getCell(c);
                assertEquals(CellType.NUMERIC, cell.getCellType(), "column " + c);
                assertTrue(DateUtil.isCellDateFormatted(cell), "column " + c);
            }
            assertEquals(LocalDateTime.of(2026, 10, 7, 0, 0), first.getCell(12).getLocalDateTimeCellValue());
            assertEquals("yyyy-MM-dd", first.getCell(12).getCellStyle().getDataFormatString());
            assertEquals(LocalDateTime.of(2026, 10, 6, 9, 14, 32), first.getCell(17).getLocalDateTimeCellValue());
            assertEquals("yyyy-MM-dd HH:mm", first.getCell(17).getCellStyle().getDataFormatString());
        }
    }

    @Test
    void exportWritesXlsxNextToTheDrawings() throws IOException {
        RelocationExcelService s = new RelocationExcelService(repo, locations, hr, new LocalFolderDrawingStorage(dir.toString(), null));
        String expected = RelocationFileNames.baseName("R0001", LocalDateTime.of(2026, 10, 6, 9, 14, 32)) + ".xlsx";
        assertEquals(new RelocationExcelService.Result(expected, expected, null), s.tryExport("R0001"));
        try (XSSFWorkbook wb = new XSSFWorkbook(Files.newInputStream(dir.resolve(expected)))) {
            assertEquals(2, wb.getSheet("Request").getLastRowNum());
        }
    }

    @Test
    void writeFailureIsReturnedNotThrown() {
        DrawingStorage broken = mock(DrawingStorage.class);
        when(broken.save(anyString(), any(), anyString())).thenThrow(new ServiceUnavailableException("Không ghi được file vào thư mục lưu trữ."));
        RelocationExcelService s = new RelocationExcelService(repo, locations, hr, broken);
        RelocationExcelService.Result r = assertDoesNotThrow(() -> s.tryExport("R0001"));
        assertNull(r.fileName());
        assertEquals("Không ghi được file vào thư mục lưu trữ.", r.error());

        // Unknown request: also only an error for the best-effort path.
        assertNotNull(s.tryExport("R9999").error());
    }

    @Test
    void drawingFileIsTheNameOfTheStoredValue() {
        assertEquals("R0001_261006-091432.png", RelocationExcelService.drawingFile("https://x/sites/F2/Drawings/R0001_261006-091432.png"));
        assertEquals("a b.png", RelocationExcelService.drawingFile("https://x/D/a%20b.png"));
        assertEquals("R0001_261006-091432.png", RelocationExcelService.drawingFile("R0001_261006-091432.png"));
        assertNull(RelocationExcelService.drawingFile(" "));
    }

    @TempDir
    Path excelDir;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};

    @Test
    void pngGoesToDirAndXlsxToExcelDir() throws IOException {
        when(repo.tableInfo()).thenReturn(new HistoryTableInfo(Map.of("drawings", 255), true));
        RelocationExcelService excel = new RelocationExcelService(repo, locations, hr,
                new LocalFolderDrawingStorage(Kind.EXCEL, excelDir.toString(), "https://x/Excel"));
        RelocationDrawingService drawings = new RelocationDrawingService(repo, new LocalFolderDrawingStorage(Kind.DRAWINGS, dir.toString(), null), excel);

        DrawingUploadResponse res = drawings.upload("R0001", PNG);
        String base = RelocationFileNames.baseName("R0001", LocalDateTime.of(2026, 10, 6, 9, 14, 32));
        assertEquals(base + ".png", res.fileName());
        assertEquals(List.of(base + ".png"), names(dir));
        assertEquals(List.of(base + ".xlsx"), names(excelDir));
        assertEquals(new RelocationExcelService.Result(base + ".xlsx", "https://x/Excel/" + base + ".xlsx", null), excel.tryExport("R0001"));
    }

    @Test
    void brokenExcelDirOnlyAffectsExcel() throws IOException {
        when(repo.tableInfo()).thenReturn(new HistoryTableInfo(Map.of("drawings", 255), true));
        RelocationExcelService excel = new RelocationExcelService(repo, locations, hr,
                new LocalFolderDrawingStorage(Kind.EXCEL, excelDir.resolve("missing").toString(), null));
        RelocationDrawingService drawings = new RelocationDrawingService(repo, new LocalFolderDrawingStorage(Kind.DRAWINGS, dir.toString(), null), excel);

        assertNotNull(drawings.upload("R0001", PNG).fileName());
        assertEquals(1, names(dir).size());
        verify(repo).updateDrawings(eq("R0001"), anyString());
        // Regenerate endpoint: 503; create path: excelError.
        assertThrows(ServiceUnavailableException.class, () -> excel.export("R0001"));
        assertEquals("Thư mục lưu Excel không tồn tại.", excel.tryExport("R0001").error());

        RelocationExcelService unset = new RelocationExcelService(repo, locations, hr, new LocalFolderDrawingStorage(Kind.EXCEL, " ", null));
        assertEquals("Chưa cấu hình drawings.excel-dir.", unset.tryExport("R0001").error());
    }

    private static List<String> names(Path folder) throws IOException {
        try (var s = Files.list(folder)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }
}
