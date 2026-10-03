package com.xmut.forma.extension.tool.xhs.port;

import java.util.List;

/**
 * Read-only Xiaohongshu note keyword search (Mock / Apify).
 */
public interface XhsNoteSearchPort {

    List<XhsNoteSearchHit> search(String query, int pageSize);
}
