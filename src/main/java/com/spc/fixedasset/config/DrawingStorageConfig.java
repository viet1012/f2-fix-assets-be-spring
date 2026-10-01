package com.spc.fixedasset.config;

import com.spc.fixedasset.storage.DrawingStorage;
import com.spc.fixedasset.storage.LocalFolderDrawingStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A missing or unusable folder never blocks startup (the endpoint answers 503). */
@Configuration
@EnableConfigurationProperties(DrawingsProperties.class)
public class DrawingStorageConfig {

    @Bean
    public DrawingStorage drawingStorage(DrawingsProperties properties) {
        return new LocalFolderDrawingStorage(properties.dir(), properties.baseUrl());
    }
}
