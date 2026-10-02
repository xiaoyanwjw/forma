package com.xmut.ebus.extension.tool.xhs;

import com.xmut.lims.pi.agent.tool.ToolDefinition;
import com.xmut.lims.pi.agent.tool.ToolDefinitionJsonLoader;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import static org.junit.jupiter.api.Assertions.assertEquals;

class XhsToolJsonTest {

    @Test
    void xhs_tool_json_handlerClasses_are_extension_fqcns() throws Exception {
        List<ToolDefinition> defs = ToolDefinitionJsonLoader.load(new PathMatchingResourcePatternResolver());
        assertEquals(
                "com.xmut.ebus.extension.tool.xhs.SearchXhsNoteToolHandler",
                handlerClassOf(defs, "search_xhs_note"));
        assertEquals(
                "com.xmut.ebus.extension.tool.xhs.FetchXhsNoteToolHandler",
                handlerClassOf(defs, "fetch_xhs_note"));
    }

    private static String handlerClassOf(List<ToolDefinition> defs, String id) {
        return defs.stream()
                .filter(t -> id.equals(t.getId()))
                .findFirst()
                .get()
                .getHandlerClass();
    }
}
