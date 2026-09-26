package com.xmut.ebus.application.business.computer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned Computer preview document (view protocol v1).
 */
public final class ComputerDocument {

    private final int version;
    private final String title;
    private final String status;
    private final List<Map<String, Object>> blocks;

    public ComputerDocument(int version, String title, String status, List<Map<String, Object>> blocks) {
        this.version = version;
        this.title = title;
        this.status = status;
        this.blocks = blocks == null
                ? new ArrayList<Map<String, Object>>()
                : new ArrayList<Map<String, Object>>(blocks);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> doc = new LinkedHashMap<String, Object>();
        doc.put("version", Integer.valueOf(version));
        doc.put("title", title);
        if (status != null) {
            doc.put("status", status);
        }
        doc.put("blocks", new ArrayList<Map<String, Object>>(blocks));
        return doc;
    }
}
