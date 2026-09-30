package com.spc.fixedasset.service;

import com.spc.fixedasset.model.ParsedWorkbook;
import com.spc.fixedasset.repository.FixedAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportPersistenceService {

    private final FixedAssetRepository repository;

    public ImportPersistenceService(FixedAssetRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public long saveSuccessfulImport(String sourceType, String sourceName, ParsedWorkbook parsed) {
        long importId = repository.insertImportHistory(
                sourceType, sourceName, parsed.sheetName(), parsed.rows().size(), "success", null, null);
        repository.upsertAssets(parsed.rows(), null);
        return importId;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailedImport(String sourceType, String sourceName, String sheetName, String errorMessage) {
        repository.insertImportHistory(sourceType, sourceName, sheetName, 0, "error", errorMessage, null);
    }
}
