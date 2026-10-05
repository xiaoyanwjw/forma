package com.xmut.forma.extension.output;

import com.xmut.forma.common.output.OutputParseContext;
import com.xmut.forma.common.output.ParsedGenerationOutput;
import com.xmut.forma.common.output.TurnAttachment;
import com.xmut.forma.extension.config.ViewToolsConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.annotation.Order;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceOutputParserTest {

    @TempDir
    Path root;

    @Test
    void order_is_zero() {
        Order order = WorkspaceOutputParser.class.getAnnotation(Order.class);
        assertNotNull(order);
        assertEquals(0, order.value());
    }

    @Test
    void idle_chat_without_paths_does_not_apply() {
        WorkspaceOutputParser parser = new WorkspaceOutputParser();

        assertFalse(parser.appliesTo(ctx(null)));
        assertFalse(parser.appliesTo(null));
    }

    @Test
    void declared_slots_apply_even_when_files_are_missing() {
        WorkspaceOutputParser parser = new WorkspaceOutputParser();

        assertTrue(parser.appliesTo(ctx("view.json")));
        assertTrue(parser.appliesTo(ctx("plan/view.json")));
    }

    @Test
    void unsafe_paths_do_not_apply() {
        WorkspaceOutputParser parser = new WorkspaceOutputParser();

        assertFalse(parser.appliesTo(ctx("../view.json")));
        assertFalse(parser.appliesTo(ctx("/tmp/view.json")));
    }

    @Test
    void output_json_success() throws Exception {
        Files.write(root.resolve("view.json"),
                "{\"version\":2,\"title\":\"选题\",\"format\":\"html\",\"content\":\"<p>x</p>\"}"
                        .getBytes(StandardCharsets.UTF_8));
        WorkspaceOutputReader reader = new WorkspaceOutputReader();

        ParsedGenerationOutput out = reader.read(root, "view.json");

        assertEquals(2, ((Number) out.getRawView().get("version")).intValue());
        assertEquals("html", out.getRawView().get("format"));
        assertEquals("选题", out.getBusinessPayload().get("title"));
    }

    @Test
    void sibling_artifact_is_business_payload() throws Exception {
        Files.write(root.resolve("view.json"),
                "{\"version\":2,\"title\":\"页面标题\",\"format\":\"html\",\"content\":\"<p>x</p>\"}"
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("artifact.json"),
                ("{\"title\":\"摘要标题\",\"source\":\"fetch\",\"excerpts\":"
                        + "[{\"heading\":\"h\",\"quotes\":[\"q\"]}]}")
                        .getBytes(StandardCharsets.UTF_8));
        WorkspaceOutputReader reader = new WorkspaceOutputReader();

        ParsedGenerationOutput out = reader.read(root, "view.json");

        assertEquals("html", out.getRawView().get("format"));
        assertEquals("摘要标题", out.getBusinessPayload().get("title"));
        assertEquals("fetch", out.getBusinessPayload().get("source"));
        assertTrue(out.getBusinessPayload().get("excerpts") instanceof java.util.List);
    }

    @Test
    void missing_view_file_throws() {
        WorkspaceOutputReader reader = new WorkspaceOutputReader();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reader.read(root, "view.json"));

        assertEquals("output file missing: view.json", ex.getMessage());
    }

    @Test
    void empty_view_file_throws() throws Exception {
        Files.write(root.resolve("view.json"), new byte[0]);
        WorkspaceOutputReader reader = new WorkspaceOutputReader();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reader.read(root, "view.json"));

        assertEquals("output file missing: view.json", ex.getMessage());
    }

    @Test
    void rejects_parent_segments() throws Exception {
        WorkspaceOutputReader reader = new WorkspaceOutputReader();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reader.read(root, "../view.json"));

        assertTrue(ex.getMessage().contains("path escapes"));
    }

    @Test
    void declared_paths_missing_on_disk_do_not_fall_through() {
        WorkspaceOutputParser parser = new WorkspaceOutputParser();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(ctx("view.json")));

        assertEquals("output file missing: view.json", ex.getMessage());
    }

    @Test
    void parser_reads_context_paths() throws Exception {
        Files.createDirectories(root.resolve("out"));
        Files.write(root.resolve("out/view.json"),
                "{\"version\":1,\"title\":\"from-disk\",\"status\":\"ready\",\"blocks\":[]}"
                        .getBytes(StandardCharsets.UTF_8));
        WorkspaceOutputParser parser = new WorkspaceOutputParser();

        ParsedGenerationOutput out = parser.parse(OutputParseContext.builder()
                .skillId("demo")
                .attachment(attachmentOf("out/view.json"))
                .finalResponse("{\"output\":\"ignored.json\",\"view\":{\"title\":\"from-text\"}}")
                .workspaceRoot(root)
                .build());

        assertEquals("from-disk", out.getRawView().get("title"));
        assertEquals("from-disk", out.getBusinessPayload().get("title"));
    }

    @Test
    void configuration_registers_parser() {
        assertNotNull(new ViewToolsConfiguration().workspaceOutputParser());
    }

    private OutputParseContext ctx(String output) {
        return OutputParseContext.builder()
                .skillId("demo")
                .attachment(attachmentOf(output))
                .workspaceRoot(root)
                .finalResponse("hello")
                .build();
    }

    private static TurnAttachment attachmentOf(String output) {
        Map<String, Object> raw = new HashMap<String, Object>();
        if (output != null) {
            raw.put(TurnDeliverableKeys.OUTPUT, output);
        }
        return TurnAttachment.of(raw);
    }
}
