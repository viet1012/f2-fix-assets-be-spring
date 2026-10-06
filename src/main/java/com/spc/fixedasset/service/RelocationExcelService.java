package com.spc.fixedasset.service;

import com.spc.fixedasset.auth.HrRepository;
import com.spc.fixedasset.exception.BadRequestException;
import com.spc.fixedasset.exception.NotFoundException;
import com.spc.fixedasset.model.LocationMapRow;
import com.spc.fixedasset.model.RelocationHistoryRow;
import com.spc.fixedasset.repository.LocationRepository;
import com.spc.fixedasset.repository.RelocationRequestRepository;
import com.spc.fixedasset.storage.DrawingStorage;
import com.spc.fixedasset.storage.DrawingStorage.StoredDrawing;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTable;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableStyleInfo;
import com.spc.fixedasset.config.DrawingStorageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Excel export of one relocation request (sheet "Request", table "Table1"), saved next to the drawings as
 * {RequestNo}_{yyMMdd-HHmmss}.xlsx. Built from the stored rows (_BF/_AT snapshots), never from a request body.
 */
@Service
public class RelocationExcelService {

    public static final String SHEET = "Request";
    public static final String TABLE = "Table1";
    static final List<String> COLUMNS = List.of("RequestNo", "MachineCode", "MachineName", "Fac_BF", "Floor_BF", "PositionA_BF",
            "PositionAA_BF", "Fac_AT", "Floor_AT", "PositionA_AT", "PositionAA_AT", "MoveType", "PlannedMoveDate", "PlannedDoneDate",
            "Reason", "RequestedBy", "RequesterName", "CreateDate", "Status", "DrawingFile");
    private static final int MAX_WIDTH = 60;

    private static final Logger log = LoggerFactory.getLogger(RelocationExcelService.class);

    private final RelocationRequestRepository repository;
    private final LocationRepository locations;
    private final HrRepository hr;
    private final DrawingStorage storage;

    public RelocationExcelService(RelocationRequestRepository repository, LocationRepository locations, HrRepository hr,
                                  @Qualifier(DrawingStorageConfig.EXCEL) DrawingStorage storage) {
        this.repository = repository;
        this.locations = locations;
        this.hr = hr;
        this.storage = storage;
    }

    /** Outcome of a best-effort export: fileName + url (webUrl, or the file name without excel base URL), or error. */
    public record Result(String fileName, String url, String error) {}

    /** {RequestNo}_{yyMMdd-HHmmss}.xlsx */
    public static String fileName(String requestNo, LocalDateTime createDate) {
        return RelocationFileNames.baseName(requestNo, createDate) + ".xlsx";
    }

    /** Where the export of this request is (or will be) found: excel base URL + "/" + encoded name, or the file name. */
    public String excelUrl(String requestNo, LocalDateTime createDate) {
        return storage.urlOrName(fileName(requestNo, createDate));
    }

    /** (Re)writes the file; 404 unknown request, 503 storage unusable. */
    public StoredDrawing export(String requestNo) {
        List<RelocationHistoryRow> rows = repository.findRows(List.of(requestNo));
        if (rows.isEmpty()) throw new NotFoundException("Không tìm thấy yêu cầu di dời: " + requestNo);
        List<String> codes = rows.stream().map(RelocationHistoryRow::machineCode).distinct().toList();
        byte[] content = build(rows, repository.findMachineNames(codes), requesterNames(rows), locations.findMapRows());
        String name = fileName(requestNo, rows.get(0).createDate());
        try {
            return storage.save(name, content, DrawingStorage.XLSX);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    /** Never throws: a failure is logged as one WARN line and returned as the error text (the request itself stays). */
    public Result tryExport(String requestNo) {
        try {
            StoredDrawing stored = export(requestNo);
            return new Result(stored.fileName(), stored.webUrl() != null ? stored.webUrl() : stored.fileName(), null);
        } catch (RuntimeException e) {
            log.warn("Không xuất được Excel cho yêu cầu {}: {}", requestNo, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return new Result(null, null, e.getMessage() == null ? "Không xuất được file Excel." : e.getMessage());
        }
    }

    /** One table row per machine; RequestedBy/RequesterName split from Creater (requesterNames: HR names of old rows); Fac/Floor/MoveType come from F2_FIXED_ASSET_MAP for the stored positions. */
    static byte[] build(List<RelocationHistoryRow> rows, Map<String, String> machineNames, Map<String, String> requesterNames,
                        List<LocationMapRow> mapRows) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet(SHEET);
            CreationHelper helper = wb.getCreationHelper();
            CellStyle date = wb.createCellStyle();
            date.setDataFormat(helper.createDataFormat().getFormat("yyyy-MM-dd"));
            CellStyle dateTime = wb.createCellStyle();
            dateTime.setDataFormat(helper.createDataFormat().getFormat("yyyy-MM-dd HH:mm"));

            int[] widths = new int[COLUMNS.size()];
            Row header = sheet.createRow(0);
            for (int c = 0; c < COLUMNS.size(); c++) {
                header.createCell(c).setCellValue(COLUMNS.get(c));
                widths[c] = COLUMNS.get(c).length();
            }
            for (int i = 0; i < rows.size(); i++) {
                RelocationHistoryRow r = rows.get(i);
                LocationMapRow from = LocationMatcher.match(r.positionABf(), r.positionAABf(), null, mapRows).row();
                LocationMapRow to = RelocationRequestService.destinationRow(r.positionAAt(), r.positionAAAt(), mapRows);
                List<Object> values = Arrays.asList(r.requestNo(), r.machineCode(), machineNames.get(r.machineCode()),
                        from == null ? null : from.fac(), from == null ? null : from.floor(), r.positionABf(), r.positionAABf(),
                        to == null ? null : to.fac(), to == null ? null : to.floor(), r.positionAAt(), r.positionAAAt(),
                        to == null ? null : RelocationRequestService.moveType(from, to), r.plannedMoveDate(), r.plannedDoneDate(),
                        r.note(), CreaterFormat.accountOf(r.creater()), CreaterFormat.requesterName(r.creater(), requesterNames), r.createDate(), r.status(),
                        drawingFile(r.drawings()));
                Row row = sheet.createRow(i + 1);
                for (int c = 0; c < values.size(); c++) {
                    Cell cell = row.createCell(c);
                    Object v = values.get(c);
                    if (v instanceof LocalDateTime t) {
                        cell.setCellValue(t);
                        cell.setCellStyle(dateTime);
                        widths[c] = Math.max(widths[c], 16);
                    } else if (v instanceof LocalDate d) {
                        cell.setCellValue(d);
                        cell.setCellStyle(date);
                        widths[c] = Math.max(widths[c], 10);
                    } else if (v != null) {
                        cell.setCellValue(v.toString());
                        widths[c] = Math.max(widths[c], v.toString().length());
                    }
                }
            }

            // A table needs at least one data row.
            int lastRow = Math.max(rows.size(), 1);
            if (rows.isEmpty()) sheet.createRow(1);
            AreaReference area = new AreaReference(new CellReference(0, 0), new CellReference(lastRow, COLUMNS.size() - 1),
                    SpreadsheetVersion.EXCEL2007);
            XSSFTable table = sheet.createTable(area);
            table.setName(TABLE);
            table.setDisplayName(TABLE);
            CTTable ct = table.getCTTable();
            CTTableStyleInfo style = ct.isSetTableStyleInfo() ? ct.getTableStyleInfo() : ct.addNewTableStyleInfo();
            style.setName("TableStyleMedium2");
            style.setShowRowStripes(true);
            style.setShowColumnStripes(false);
            (ct.isSetAutoFilter() ? ct.getAutoFilter() : ct.addNewAutoFilter()).setRef(area.formatAsString());
            for (int c = 0; c < COLUMNS.size(); c++) {
                table.getCTTable().getTableColumns().getTableColumnArray(c).setName(COLUMNS.get(c));
                // +2 characters of padding, +2 more for the header filter button.
                sheet.setColumnWidth(c, Math.min(widths[c] + 4, MAX_WIDTH) * 256);
            }
            sheet.createFreezePane(0, 1);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** File name of the stored Drawings value (webUrl or plain file name). */
    static String drawingFile(String drawings) {
        String v = clean(drawings);
        if (v == null) return null;
        int slash = v.lastIndexOf('/');
        String name = slash >= 0 ? v.substring(slash + 1) : v;
        try {
            return URLDecoder.decode(name.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return name;
        }
    }

    /** HR names of old (account-only) Creaters; an HR failure only leaves them empty. */
    private Map<String, String> requesterNames(List<RelocationHistoryRow> rows) {
        List<String> legacy = CreaterFormat.legacyAccounts(rows.stream().map(RelocationHistoryRow::creater).toList());
        if (legacy.isEmpty()) return Map.of();
        try {
            return hr.findNames(legacy);
        } catch (DataAccessException e) {
            log.warn("Không tra được tên người yêu cầu trong F2_HR_Data ({})", e.getClass().getSimpleName());
            return Map.of();
        }
    }

    private static String clean(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
