package com.spc.fixedasset.storage;

/** Where relocation drawings are kept; a Microsoft Graph implementation can replace the local folder one. */
public interface DrawingStorage {

    /**
     * Saves (or overwrites) the file.
     *
     * @throws com.spc.fixedasset.exception.ServiceUnavailableException when the storage is not configured or not writable
     * @throws IllegalArgumentException when the file name is not safe
     */
    StoredDrawing save(String fileName, byte[] png);

    /** webUrl is null when the storage has no web address for its files. */
    record StoredDrawing(String fileName, String webUrl) {}
}
