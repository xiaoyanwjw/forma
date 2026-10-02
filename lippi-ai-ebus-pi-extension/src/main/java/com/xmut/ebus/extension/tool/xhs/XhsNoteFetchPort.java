package com.xmut.ebus.extension.tool.xhs;

/**
 * Read-only Xiaohongshu note detail fetch (Mock / Apify). Not a recall pipeline.
 */
public interface XhsNoteFetchPort {

    XhsNoteFetchHit fetch(String noteRef);
}
