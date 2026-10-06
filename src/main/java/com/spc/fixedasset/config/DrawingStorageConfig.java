package com.spc.fixedasset.config;

import com.spc.fixedasset.storage.DrawingStorage;
import com.spc.fixedasset.storage.LocalFolderDrawingStorage;
import com.spc.fixedasset.storage.LocalFolderDrawingStorage.Kind;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Two folders: drawingStorage (.png) and excelStorage (.xlsx); inject them by @Qualifier. A missing or unusable folder
 * never blocks startup (only the features using it answer 503).
 */
@Configuration
@EnableConfigurationProperties(DrawingsProperties.class)
public class DrawingStorageConfig {

    public static final String DRAWINGS = "drawingStorage";
    public static final String EXCEL = "excelStorage";

    @Bean(DRAWINGS)
    public DrawingStorage drawingStorage(DrawingsProperties properties) {
        return new LocalFolderDrawingStorage(Kind.DRAWINGS, properties.dir(), properties.baseUrl());
    }

    @Bean(EXCEL)
    public DrawingStorage excelStorage(DrawingsProperties properties) {
        return new LocalFolderDrawingStorage(Kind.EXCEL, properties.excelDir(), properties.excelBaseUrl());
    }
}
