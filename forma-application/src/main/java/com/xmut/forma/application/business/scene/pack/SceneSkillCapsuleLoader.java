package com.xmut.forma.application.business.scene.pack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.application.business.scene.dto.SceneSkillCapsuleItemDTO;
import com.xmut.forma.common.util.StringUtils;
import com.xmut.lims.pi.agent.skill.Skill;
import com.xmut.lims.pi.agent.skill.SkillPromptBodyLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 从 skill 目录旁 {@code launch.json} 读取快捷栏元数据；无文件则跳过该 skill。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SceneSkillCapsuleLoader {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public List<SceneSkillCapsuleItemDTO> loadFor(List<Skill> skills) {
        if (skills == null || skills.isEmpty()) {
            return Collections.emptyList();
        }
        List<SceneSkillCapsuleItemDTO> items = new ArrayList<SceneSkillCapsuleItemDTO>();
        for (Skill skill : skills) {
            if (skill == null || !StringUtils.hasText(skill.getId())) {
                continue;
            }
            SceneSkillCapsuleItemDTO item = loadOne(skill);
            if (item != null) {
                items.add(item);
            }
        }
        Collections.sort(items, new Comparator<SceneSkillCapsuleItemDTO>() {
            @Override
            public int compare(SceneSkillCapsuleItemDTO a, SceneSkillCapsuleItemDTO b) {
                int byOrder = Integer.compare(a.getSortOrder(), b.getSortOrder());
                if (byOrder != 0) {
                    return byOrder;
                }
                return a.getSkillId().compareTo(b.getSkillId());
            }
        });
        return Collections.unmodifiableList(items);
    }

    private SceneSkillCapsuleItemDTO loadOne(Skill skill) {
        String launchRef = launchLocation(skill.getPromptRef());
        if (!StringUtils.hasText(launchRef)) {
            return null;
        }
        try {
            Resource resource = resourceLoader.getResource(launchRef);
            if (resource == null || !resource.exists() || !resource.isReadable()) {
                return null;
            }
            String json;
            try (InputStream in = resource.getInputStream()) {
                json = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            }
            if (!StringUtils.hasText(json)) {
                return null;
            }
            JsonNode root = objectMapper.readTree(json);
            String label = text(root, "label");
            String examplePrompt = text(root, "examplePrompt");
            if (!StringUtils.hasText(label) || !StringUtils.hasText(examplePrompt)) {
                log.warn("scene launch.json missing label/examplePrompt skillId={} ref={}",
                        skill.getId(), launchRef);
                return null;
            }
            int sortOrder = root.path("sortOrder").isNumber() ? root.path("sortOrder").asInt() : 0;
            return new SceneSkillCapsuleItemDTO(
                    skill.getId().trim(),
                    label.trim(),
                    examplePrompt.trim(),
                    sortOrder);
        } catch (Exception ex) {
            log.warn("scene launch.json unreadable skillId={} ref={} err={}",
                    skill.getId(), launchRef, ex.toString());
            return null;
        }
    }

    /**
     * {@code classpath:scenes/.../SKILL.md} → {@code classpath:scenes/.../launch.json}
     */
    static String launchLocation(String promptRef) {
        if (!StringUtils.hasText(promptRef)) {
            return null;
        }
        String location = SkillPromptBodyLoader.normalizeLocation(promptRef.trim());
        String lower = location.toLowerCase();
        if (!lower.endsWith("skill.md")) {
            return null;
        }
        int cut = location.length() - "SKILL.md".length();
        if (!location.regionMatches(true, cut, "SKILL.md", 0, "SKILL.md".length())) {
            return null;
        }
        return location.substring(0, cut) + "launch.json";
    }

    private static String text(JsonNode root, String field) {
        if (root == null || !root.has(field) || root.get(field).isNull()) {
            return null;
        }
        return root.get(field).asText(null);
    }
}
