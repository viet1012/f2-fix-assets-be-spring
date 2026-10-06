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
                    DrawingStorage.StoredDrawing d = ctx.getBean(DrawingStorageConfig.DRAWINGS, DrawingStorage.class).save("a b.png", PNG);
                    assertEquals("https://spc.sharepoint.com/Drawings/a%20b.png", d.webUrl());
                    assertTrue(Files.exists(dir.resolve("a b.png")));
                });
    }

    @Test
    void emptyValuesStartAndAnswer503() {
        runner.withPropertyValues("drawings.dir=", "drawings.base-url=")
                .run(ctx -> {
                    assertNull(ctx.getStartupFailure());
                    assertThrows(ServiceUnavailableException.class, () -> ctx.getBean(DrawingStorageConfig.DRAWINGS, DrawingStorage.class).save("a.png", PNG));
                });
    }

    @Test
    void emptyBaseUrlGivesNullWebUrl() {
        runner.withPropertyValues("drawings.dir=" + dir, "drawings.base-url=")
                .run(ctx -> assertNull(ctx.getBean(DrawingStorageConfig.DRAWINGS, DrawingStorage.class).save("a.png", PNG).webUrl()));
    }

    @TempDir
    Path excelDir;

    @Test
    void excelHasItsOwnFolderAndBaseUrl() {
        runner.withPropertyValues("drawings.dir=" + dir, "drawings.base-url=https://x/Drawings",
                        "drawings.excel-dir=" + excelDir, "drawings.excel-base-url=https://x/Excel/")
                .run(ctx -> {
                    DrawingStorage excel = ctx.getBean(DrawingStorageConfig.EXCEL, DrawingStorage.class);
                    assertEquals("https://x/Excel/R0001_261006-091432.xlsx", excel.save("R0001_261006-091432.xlsx", new byte[]{1}, DrawingStorage.XLSX).webUrl());
                    assertTrue(Files.exists(excelDir.resolve("R0001_261006-091432.xlsx")));
                    assertFalse(Files.exists(dir.resolve("R0001_261006-091432.xlsx")));
                });
    }

    @Test
    void emptyExcelDirOnlyBreaksExcel() {
        runner.withPropertyValues("drawings.dir=" + dir, "drawings.excel-dir=")
                .run(ctx -> {
                    assertNull(ctx.getStartupFailure());
                    assertNotNull(ctx.getBean(DrawingStorageConfig.DRAWINGS, DrawingStorage.class).save("a.png", PNG));
                    ServiceUnavailableException e = assertThrows(ServiceUnavailableException.class,
                            () -> ctx.getBean(DrawingStorageConfig.EXCEL, DrawingStorage.class).save("a.xlsx", new byte[]{1}, DrawingStorage.XLSX));
                    assertEquals("Chưa cấu hình drawings.excel-dir.", e.getMessage());
                });
    }
}
