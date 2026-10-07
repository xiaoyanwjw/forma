package com.xmut.forma.extension.tool.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewRenderHelpersTest {

    @Test
    void scoreTone_helpers() {
        assertEquals("高", ViewRenderHelpers.scoreLabel("高｜接口"));
        assertEquals("ok", ViewRenderHelpers.scoreTone("demand", "中高"));
        assertEquals("warn", ViewRenderHelpers.scoreTone("competition", "偏高"));
        assertEquals("bad", ViewRenderHelpers.scoreTone("risk", "高"));
    }
}
