package com.xmut.ebus.application.business.agent.tool.xhs;

/**
 * Read-only Xiaohongshu note detail fetch (Mock / Apify). Not a recall pipeline.
 */
public interface XhsNoteFetchPort {

    XhsNoteFetchHit fetch(String noteRef);
}
