package com.spc.fixedasset.config;

import com.spc.fixedasset.exception.ServiceUnavailableException;
import com.spc.fixedasset.storage.DrawingStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DrawingStorageConfigTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(DrawingStorageConfig.class);

    @TempDir
    Path dir;

    @Test
    void bindsDirAndBaseUrl() {
        runner.withPropertyValues("drawings.dir=" + dir, "drawings.base-url=https://spc.sharepoint.com/Drawings/")
                .run(ctx -> {
                    DrawingStorage.StoredDrawing d = ctx.getBean(DrawingStorage.class).save("a b.png", PNG);
                    assertEquals("https://spc.sharepoint.com/Drawings/a%20b.png", d.webUrl());
                    assertTrue(Files.exists(dir.resolve("a b.png")));
                });
    }

    @Test
    void emptyValuesStartAndAnswer503() {
        runner.withPropertyValues("drawings.dir=", "drawings.base-url=")
                .run(ctx -> {
                    assertNull(ctx.getStartupFailure());
                    assertThrows(ServiceUnavailableException.class, () -> ctx.getBean(DrawingStorage.class).save("a.png", PNG));
                });
    }

    @Test
    void emptyBaseUrlGivesNullWebUrl() {
        runner.withPropertyValues("drawings.dir=" + dir, "drawings.base-url=")
                .run(ctx -> assertNull(ctx.getBean(DrawingStorage.class).save("a.png", PNG).webUrl()));
    }
}
