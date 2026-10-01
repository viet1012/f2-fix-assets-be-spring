package com.spc.fixedasset.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** drawings.dir: target folder (e.g. a synced OneDrive folder); drawings.base-url: its SharePoint web link (optional). */
@ConfigurationProperties(prefix = "drawings")
public record DrawingsProperties(String dir, String baseUrl) {}
