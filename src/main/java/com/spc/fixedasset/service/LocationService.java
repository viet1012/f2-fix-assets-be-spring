package com.spc.fixedasset.service;

import com.spc.fixedasset.dto.AssetLocationResponse;
import com.spc.fixedasset.dto.LocationResponse;
import com.spc.fixedasset.exception.NotFoundException;
import com.spc.fixedasset.model.LocationAsset;
import com.spc.fixedasset.model.LocationMapRow;
import com.spc.fixedasset.repository.LocationRepository;
import com.spc.fixedasset.service.LocationMatcher.Match;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.spc.fixedasset.service.LocationMatcher.*;

@Service
public class LocationService {

    private final LocationRepository repository;

    public LocationService(LocationRepository repository) {
        this.repository = repository;
    }

    /** MAP rows filtered by fac/div/floor; assetCount of a major zone includes the assets matched to its sub-zones. */
    @Transactional(readOnly = true)
    public List<LocationResponse> getLocations(String fac, String div, String floor, String assetFactory) {
        List<LocationMapRow> rows = repository.findMapRows();
        Map<Long, Integer> direct = new HashMap<>();
        for (LocationAsset a : repository.findAssets(blankToNull(assetFactory), null)) {
            Match m = match(a.positionA(), a.positionAA(), a.div(), rows);
            if (m.row() != null) direct.merge(m.row().id(), 1, Integer::sum);
        }
        return rows.stream()
                .filter(r -> matchesFilter(r.fac(), fac) && matchesFilter(r.div(), div) && matchesFilter(r.floor(), floor))
                .map(r -> new LocationResponse(r.id(), r.fac(), r.div(), r.floor(),
                        normalize(r.a()), normalizeSub(r.a(), r.aa()), parsePos(r.aPos()), parsePos(r.aaPos()),
                        assetCount(r, rows, direct)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssetLocationResponse> getAssetsWithLocation(String factory, String div) {
        List<LocationMapRow> rows = repository.findMapRows();
        return repository.findAssets(blankToNull(factory), blankToNull(div)).stream()
                .map(a -> toResponse(a, rows))
                .toList();
    }

    @Transactional(readOnly = true)
    public AssetLocationResponse getAssetLocation(String code) {
        LocationAsset asset = repository.findAssetByCode(code);
        if (asset == null) throw new NotFoundException("Không tìm thấy máy: " + code);
        return toResponse(asset, repository.findMapRows());
    }

    static AssetLocationResponse toResponse(LocationAsset a, List<LocationMapRow> rows) {
        Match m = match(a.positionA(), a.positionAA(), a.div(), rows);
        LocationMapRow r = m.row();
        return new AssetLocationResponse(a.code(), a.name(), a.kind(), a.faType(), a.status(), a.div(), a.factory(), a.floor(),
                normalize(a.positionA()), normalizeSub(a.positionA(), a.positionAA()),
                currentZone(a.positionA(), a.positionAA(), m),
                r == null ? null : r.id(), r == null ? null : r.fac(), r == null ? null : r.floor(),
                r == null ? null : parsePos(r.aPos()), r == null ? null : parsePos(r.aaPos()),
                m.level(), floorMismatch(a.floor(), m));
    }

    private static int assetCount(LocationMapRow row, List<LocationMapRow> rows, Map<Long, Integer> direct) {
        if (normalizeSub(row.a(), row.aa()) != null) return direct.getOrDefault(row.id(), 0);
        String a = normalize(row.a());
        return rows.stream()
                .filter(r -> Objects.equals(a, normalize(r.a())) && sameText(r.fac(), row.fac()))
                .mapToInt(r -> direct.getOrDefault(r.id(), 0))
                .sum();
    }

    private static boolean matchesFilter(String value, String filter) {
        String f = blankToNull(filter);
        return f == null || sameText(value, f);
    }

    private static String blankToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
