package com.spc.fixedasset.service;

import com.spc.fixedasset.dto.RelocationCreateRequest;
import com.spc.fixedasset.dto.RelocationCreateRequest.Target;
import com.spc.fixedasset.dto.RelocationCreateResponse;
import com.spc.fixedasset.exception.BadRequestException;
import com.spc.fixedasset.exception.ConflictException;
import com.spc.fixedasset.exception.NotFoundException;
import com.spc.fixedasset.model.HistoryTableInfo;
import com.spc.fixedasset.model.LocationMapRow;
import com.spc.fixedasset.model.RelocationAsset;
import com.spc.fixedasset.model.RelocationHistoryRow;
import com.spc.fixedasset.repository.LocationRepository;
import com.spc.fixedasset.repository.RelocationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLException;
import java.time.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RelocationRequestServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private final RelocationRequestRepository repo = mock(RelocationRequestRepository.class);
    private final LocationRepository locations = mock(LocationRepository.class);
    private final RelocationRequestService service = new RelocationRequestService(repo, locations,
            Clock.fixed(LocalDateTime.of(2026, 10, 1, 9, 30).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));

    private static RelocationCreateRequest req(List<String> codes, String a, String aa, LocalDate move, LocalDate done, String reason, String by) {
        return new RelocationCreateRequest(codes, new Target(a, aa), move, done, reason, by);
    }

    private static RelocationCreateRequest ok(String... codes) {
        return req(List.of(codes), "A-15", "A-15-3", TODAY, TODAY.plusDays(2), " Layout ", " E001 ");
    }

    private static RelocationAsset asset(String code, String kind, String a, String aa) {
        return new RelocationAsset(code, kind, "PRESS", "1F", a, aa, " P1 ", " Press ", " pic@spc ");
    }

    @BeforeEach
    void setUp() {
        when(locations.findMapRows()).thenReturn(List.of(
                new LocationMapRow(1, "Fac_A", "PRESS", "1F", "A2", "A2-3", null, null),
                new LocationMapRow(2, "Fac_B", "PRESS", "1F", "A15", "A15-3", null, null),
                new LocationMapRow(3, "Outside", "KVH", "Outside", "OUTSIDE", null, null, null)));
        when(repo.findAssets(anyCollection())).thenReturn(List.of(
                asset("M1", "Machinery", "A2", "A-2-3"), asset("M2", " tools ", "A15", "A15-3"), asset("M3", "Building", "A2", "A2-3")));
        when(repo.findOpenMachineCodes(anyCollection(), anyCollection())).thenReturn(List.of());
        when(repo.tableInfo()).thenReturn(new HistoryTableInfo(Map.of("note", 10, "creater", 50, "requestno", 20), true));
        when(repo.maxRequestSeq(2026)).thenReturn(41);
    }

    @Test
    void validateRejectsBadBodies() {
        assertThrows(BadRequestException.class, () -> RelocationRequestService.validate(ok(), TODAY));
        assertThrows(BadRequestException.class, () -> RelocationRequestService.validate(req(List.of("M1"), "A15", null, TODAY.minusDays(1), TODAY, "r", "E1"), TODAY));
        assertThrows(BadRequestException.class, () -> RelocationRequestService.validate(req(List.of("M1"), "A15", null, TODAY.plusDays(2), TODAY.plusDays(1), "r", "E1"), TODAY));
        assertThrows(BadRequestException.class, () -> RelocationRequestService.validate(req(List.of("M1"), "A15", null, TODAY, TODAY, "  ", "E1"), TODAY));
        assertThrows(BadRequestException.class, () -> RelocationRequestService.validate(req(List.of("M1"), "A15", null, TODAY, TODAY, "r", null), TODAY));
        assertThrows(BadRequestException.class, () -> RelocationRequestService.validate(req(List.of("M1"), " ", null, TODAY, TODAY, "r", "E1"), TODAY));
        assertEquals(List.of("M1", "M2"), RelocationRequestService.validate(req(List.of(" M1", "M2", "M1 "), "A15", null, TODAY, TODAY, "r", "E1"), TODAY));
    }

    @Test
    void destinationMustBeInMapAndNotOutside() {
        assertThrows(BadRequestException.class, () -> service.create(req(List.of("M1"), "Z9", null, TODAY, TODAY, "r", "E1")));
        BadRequestException outside = assertThrows(BadRequestException.class, () -> service.create(req(List.of("M1"), "Outside", null, TODAY, TODAY, "r", "E1")));
        assertTrue(outside.getMessage().contains("Outside"));
        verify(repo, never()).insertAll(anyList(), anyBoolean());
    }

    @Test
    void wrongKindAndUnknownMachineAre400() {
        assertTrue(assertThrows(BadRequestException.class, () -> service.create(ok("M1", "M3"))).getMessage().contains("M3"));
        assertTrue(assertThrows(BadRequestException.class, () -> service.create(ok("M1", "M9"))).getMessage().contains("M9"));
    }

    @Test
    void requestNoIsYearAndNextSequence() {
        assertEquals("RL-2026-0001", RelocationRequestService.requestNo(2026, null));
        assertEquals("RL-2026-0042", RelocationRequestService.requestNo(2026, 41));
        assertEquals("RL-2027-10000", RelocationRequestService.requestNo(2027, 9999));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsOneRowPerMovingMachineAndSkipsThoseAlreadyThere() {
        RelocationCreateResponse res = service.create(ok("M1", "M2"));
        assertEquals("RL-2026-0042", res.requestNo());
        assertEquals("REQ_PENDING_PE", res.status());
        assertEquals(List.of("M2"), res.skipped());
        assertEquals(1, res.items().size());
        assertEquals("A2-3", res.items().get(0).from().positionAA());
        assertEquals("A15-3", res.items().get(0).to().positionAA());
        assertEquals("building", res.items().get(0).moveType());

        ArgumentCaptor<List<RelocationHistoryRow>> rows = ArgumentCaptor.forClass(List.class);
        verify(repo).insertAll(rows.capture(), eq(false));
        RelocationHistoryRow r = rows.getValue().get(0);
        assertNull(r.id());
        assertEquals("A2", r.positionABf());
        assertEquals("P1", r.positionAAABf());
        assertEquals("Press", r.groupAt());
        assertEquals("pic@spc", r.picAt());
        assertEquals("A15", r.positionAAt());
        assertEquals("E001", r.creater());
        assertEquals("Layout", r.note());
        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 30), r.createDate());
    }

    @Test
    @SuppressWarnings("unchecked")
    void idIsMaxPlusOneWhenNotIdentity() {
        when(repo.tableInfo()).thenReturn(new HistoryTableInfo(Map.of(), false));
        when(repo.maxId()).thenReturn(100L);
        when(repo.findAssets(anyCollection())).thenReturn(List.of(asset("M1", "Machinery", "A2", "A2-3"), asset("M4", "Tools", "A2", "A2-3")));
        service.create(ok("M1", "M4"));
        ArgumentCaptor<List<RelocationHistoryRow>> rows = ArgumentCaptor.forClass(List.class);
        verify(repo).insertAll(rows.capture(), eq(true));
        assertEquals(List.of(101L, 102L), rows.getValue().stream().map(RelocationHistoryRow::id).toList());
    }

    @Test
    void allSkippedIs400() {
        assertThrows(BadRequestException.class, () -> service.create(ok("M2")));
        verify(repo, never()).insertAll(anyList(), anyBoolean());
    }

    @Test
    void openRequestIs409() {
        when(repo.findOpenMachineCodes(anyCollection(), anyCollection())).thenReturn(List.of("M1"));
        ConflictException e = assertThrows(ConflictException.class, () -> service.create(ok("M1")));
        assertEquals(List.of("M1"), e.codes());
        verify(repo, never()).maxRequestSeq(anyInt());
    }

    @Test
    void uniqueIndexViolationMapsTo409() {
        doThrow(new DuplicateKeyException("dup", new SQLException("Cannot insert duplicate key row", "23000", 2601)))
                .when(repo).insertAll(anyList(), anyBoolean());
        assertThrows(ConflictException.class, () -> service.create(ok("M1")));
        assertTrue(RelocationRequestService.isUniqueViolation(new RuntimeException(new SQLException("x", "23000", 2627))));
        assertFalse(RelocationRequestService.isUniqueViolation(new RuntimeException(new SQLException("x", "23000", 547))));
    }

    @Test
    void tooLongValueIs400() {
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.create(req(List.of("M1"), "A15", "A15-3", TODAY, TODAY, "a reason longer than ten", "E1")));
        assertTrue(e.getMessage().contains("Note"));
        verify(repo, never()).insertAll(anyList(), anyBoolean());
    }

    @Test
    void getReturnsTheSnapshotPerMachine() {
        RelocationHistoryRow row = new RelocationHistoryRow(1L, "RL-2026-0001", "M1", "A2", "A2-3", null, "G", "P",
                "A15", "A15-3", null, "G2", "P2", TODAY, TODAY.plusDays(3), java.time.LocalDateTime.of(2026, 10, 1, 9, 0),
                "E001", "Layout", "REQ_PENDING_PE", null);
        when(repo.findRows(List.of("RL-2026-0001"))).thenReturn(List.of(row));
        var res = service.get("RL-2026-0001");
        assertEquals("A2-3", res.items().get(0).from().positionAA());
        assertEquals("A15-3", res.items().get(0).to().positionAA());
        assertEquals("E001", res.requestedBy());
        assertEquals(TODAY.plusDays(3), res.plannedDoneDate());
        assertEquals(java.time.LocalDateTime.of(2026, 10, 1, 9, 0), res.createdAt());
    }

    @Test
    void listRejectsForeignStatusAndGetUnknownIs404() {
        assertThrows(BadRequestException.class, () -> service.list("DONE", null, null, null, null));
        assertThrows(BadRequestException.class, () -> service.list(null, null, null, 0, 1000));
        when(repo.findRows(List.of("RL-2026-9999"))).thenReturn(List.of());
        assertThrows(NotFoundException.class, () -> service.get("RL-2026-9999"));
    }
}
