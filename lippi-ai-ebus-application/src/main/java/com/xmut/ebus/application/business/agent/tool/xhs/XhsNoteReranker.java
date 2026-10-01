package com.xmut.ebus.application.business.agent.tool.xhs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orders candidate ids for {@link XhsNoteSearcher#rerank(String, List)}.
 */
public interface XhsNoteReranker {

    List<String> orderIds(String intent, List<XhsNoteCandidate> pool);

    static XhsNoteReranker identity() {
        return new XhsNoteReranker() {
            @Override
            public List<String> orderIds(String intent, List<XhsNoteCandidate> pool) {
                if (pool == null || pool.isEmpty()) {
                    return Collections.emptyList();
                }
                List<String> ids = new ArrayList<String>(pool.size());
                for (XhsNoteCandidate c : pool) {
                    ids.add(c.getId());
                }
                return ids;
            }
        };
    }
}
