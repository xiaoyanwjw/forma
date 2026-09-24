package com.xmut.lims.pi.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import com.xmut.lims.pi.agent.tool.ToolLevel;

/**
 * {@code *.skill.json} → {@link SkillManifest}（classpath / 文件共用）。
 */
public final class SkillManifestJsonLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SkillManifestJsonLoader() {}

    public static SkillManifest load(InputStream in) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("skill json input is null");
        }
        JsonNode n = MAPPER.readTree(in);
        // 缺省 → empty（合法）；显式 null → 留给 validate 拒绝
        List<String> whitelist = Collections.emptyList();
        if (n.has("toolWhitelist")) {
            JsonNode w = n.get("toolWhitelist");
            if (w == null || w.isNull()) {
                whitelist = null;
            } else if (w.isArray()) {
                whitelist = MAPPER.convertValue(w,
                        MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
            } else {
                throw new IllegalArgumentException("skill json toolWhitelist must be array or null");
            }
        }
        return SkillManifest.builder()
                .id(textOrNull(n, "id"))
                .version(textOrNull(n, "version"))
                .displayName(textOrNull(n, "displayName"))
                .description(textOrNull(n, "description"))
                .skillsPrompt(textOrNull(n, "skillsPrompt"))
                .promptRef(textOrNull(n, "promptRef"))
                .toolWhitelist(whitelist)
                .maxToolLevel(ToolLevel.valueOf(requiredText(n, "maxToolLevel")))
                .graphTopology(SkillGraphTopology.valueOf(requiredText(n, "graphTopology")))
                .modelUseCase(textOrNull(n, "modelUseCase"))
                .contentHash(textOrNull(n, "contentHash"))
                .build();
    }

    private static String requiredText(JsonNode n, String field) {
        String t = textOrNull(n, field);
        if (t == null) {
            throw new IllegalArgumentException("skill json missing field: " + field);
        }
        return t;
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String t = v.asText();
        return t != null && !t.isEmpty() ? t : null;
    }
}
