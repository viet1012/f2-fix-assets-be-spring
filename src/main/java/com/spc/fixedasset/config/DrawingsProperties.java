package com.spc.fixedasset.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * drawings.dir / drawings.base-url: folder of the PNG drawings and its SharePoint web link (optional);
 * drawings.excel-dir / drawings.excel-base-url: the same for the Excel exports.
 */
@ConfigurationProperties(prefix = "drawings")
public record DrawingsProperties(String dir, String baseUrl, String excelDir, String excelBaseUrl) {}
