package com.spc.fixedasset.storage;

/** Where relocation files (drawings, Excel exports) are kept; a Microsoft Graph implementation can replace the local folder one. */
public interface DrawingStorage {

    String PNG = "image/png";
    String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * Saves (or overwrites) the file.
     *
     * @throws com.spc.fixedasset.exception.ServiceUnavailableException when the storage is not configured or not writable
     * @throws IllegalArgumentException when the file name is not safe or does not match the content type
     */
    StoredDrawing save(String fileName, byte[] content, String contentType);

    /** A PNG drawing. */
    default StoredDrawing save(String fileName, byte[] png) {
        return save(fileName, png, PNG);
    }

    /** Web address of a file in this storage (base URL + "/" + URL-encoded name), or null when it has no base URL. */
    String webUrl(String fileName);

    /** webUrl, or the file name itself when the storage has no web address. */
    default String urlOrName(String fileName) {
        String url = webUrl(fileName);
        return url != null ? url : fileName;
    }

    /** webUrl is null when the storage has no web address for its files. */
    record StoredDrawing(String fileName, String webUrl) {}
}
