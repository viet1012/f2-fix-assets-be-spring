package com.spc.fixedasset.service;

import com.spc.fixedasset.dto.*;
import com.spc.fixedasset.exception.ImportException;
import com.spc.fixedasset.model.FixedAsset;
import com.spc.fixedasset.model.ParsedWorkbook;
import com.spc.fixedasset.repository.FixedAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

@Service
public class FixedAssetService {

    private static final int MAX_DOWNLOAD_BYTES = 50 * 1024 * 1024;

    private final FixedAssetRepository repository;
    private final ExcelImportService excelImportService;
    private final ImportPersistenceService persistenceService;
    private final HttpClient httpClient;

    public FixedAssetService(FixedAssetRepository repository, ExcelImportService excelImportService, ImportPersistenceService persistenceService) {
        this.repository = repository;
        this.excelImportService = excelImportService;
        this.persistenceService = persistenceService;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Transactional(readOnly = true)
    public AssetsResponse getAssets() {
        List<FixedAsset> rows = repository.findAll();
        return new AssetsResponse(rows, buildFilters(rows), repository.findLastSuccessfulImport());
    }

    @Transactional(readOnly = true)
    public List<ImportHistoryResponse> getImportHistory() {
        return repository.findImportHistory(50);
    }

    public HealthResponse health() {
        try {
            return repository.ping() ? HealthResponse.connected() : HealthResponse.unavailable("Unexpected database response");
        } catch (Exception e) {
            return HealthResponse.unavailable(sanitizedDatabaseError(e));
        }
    }

    public UploadResponse importUpload(String sourceName, byte[] bytes) {
        return processImport("upload", sourceName, () -> bytes);
    }

    public UploadResponse importUrl(String originalUrl) {
        return processImport("url", originalUrl, () -> download(originalUrl));
    }

    private UploadResponse processImport(String sourceType, String sourceName, Supplier<byte[]> bufferSupplier) {
        ParsedWorkbook parsed;
        try {
            byte[] bytes = bufferSupplier.get();
            parsed = excelImportService.parse(bytes);
        } catch (Exception e) {
            String message = rootMessage(e);
            safeLogFailedImport(sourceType, sourceName, "", message);
            if (e instanceof ImportException ie) throw ie;
            throw new ImportException(message, e);
        }

        try {
            long importId = persistenceService.saveSuccessfulImport(sourceType, sourceName, parsed);
            return new UploadResponse(true, parsed.sheetName(), parsed.rows().size(), importId, sourceName);
        } catch (Exception e) {
            String message = sanitizedDatabaseError(e);
            safeLogFailedImport(sourceType, sourceName, parsed.sheetName(), message);
            throw new ImportException("Import could not be saved.", e);
        }
    }

    private void safeLogFailedImport(String sourceType, String sourceName, String sheetName, String errorMessage) {
        try {
            persistenceService.logFailedImport(sourceType, sourceName, sheetName, errorMessage);
        } catch (Exception ignored) {
            // Best-effort audit logging, matching the legacy Node behavior.
        }
    }

    private byte[] download(String rawUrl) {
        try {
            URI uri = normalizeDownloadUri(rawUrl);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "FixedAssetImporter/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ImportException("HTTP " + response.statusCode() + " khi tải file từ URL.");
            }
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                throw new ImportException("File tải từ URL rỗng.");
            }
            if (body.length > MAX_DOWNLOAD_BYTES) {
                throw new ImportException("File tải từ URL vượt quá giới hạn 50MB.");
            }
            return body;
        } catch (ImportException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new ImportException("URL không hợp lệ.", e);
        } catch (IOException e) {
            throw new ImportException("Không thể tải file từ URL: " + rootMessage(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImportException("Tải file từ URL bị gián đoạn.", e);
        }
    }

    private URI normalizeDownloadUri(String rawUrl) {
        URI uri = URI.create(rawUrl);
        String host = uri.getHost();
        if (host != null && host.toLowerCase(Locale.ROOT).endsWith("sharepoint.com")) {
            String query = uri.getRawQuery();
            if (query == null || !query.toLowerCase(Locale.ROOT).contains("download=")) {
                String separator = (query == null || query.isBlank()) ? "?" : "&";
                return URI.create(rawUrl + separator + "download=1");
            }
        }
        return uri;
    }

    private FixedAssetFiltersResponse buildFilters(List<FixedAsset> rows) {
        return new FixedAssetFiltersResponse(
                unique(rows.stream().map(FixedAsset::assetGroup).toList()),
                unique(rows.stream().map(FixedAsset::pic).toList()),
                unique(rows.stream().map(FixedAsset::picApproved).toList()),
                unique(rows.stream().map(FixedAsset::div).toList()),
                unique(rows.stream().map(FixedAsset::factory).toList()),
                unique(rows.stream().map(FixedAsset::floor).toList()),
                unique(rows.stream().map(FixedAsset::kind).toList()),
                unique(rows.stream().map(FixedAsset::status).toList())
        );
    }

    private List<String> unique(List<String> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isEmpty() && !v.equals("N/A"))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) cur = cur.getCause();
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

    private String sanitizedDatabaseError(Throwable t) {
        for (Throwable current = t; current != null; current = current.getCause()) {
            if (current instanceof java.sql.SQLException) {
                return "Database operation failed";
            }
        }
        String message = rootMessage(t);
        if (message.matches("(?i).*?(password|jdbc:sqlserver|user(name)?=).*")) {
            return "Database connection failed";
        }
        return message;
    }
}
