package com.xmut.forma.application.business.scene.pack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 场景元数据（{@code scenes/{sceneCode}/pack.yaml}）。
 * 功能描述：声明必选 Skill 与空跑默认 Skill，供 Loader 校验，避免 Java 按 scene 写死。
 */
public final class SceneMeta {

    private final String sceneCode;
    private final Set<String> requiredSkills;
    private final String defaultSkill;

    public SceneMeta(String sceneCode, List<String> requiredSkills, String defaultSkill) {
        if (sceneCode == null || sceneCode.trim().isEmpty()) {
            throw new IllegalArgumentException("sceneCode required");
        }
        this.sceneCode = sceneCode.trim();
        LinkedHashSet<String> required = new LinkedHashSet<String>();
        if (requiredSkills != null) {
            for (String id : requiredSkills) {
                if (id != null && !id.trim().isEmpty()) {
                    required.add(id.trim());
                }
            }
        }
        this.requiredSkills = Collections.unmodifiableSet(required);
        this.defaultSkill = defaultSkill == null || defaultSkill.trim().isEmpty()
                ? null
                : defaultSkill.trim();
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public Set<String> getRequiredSkills() {
        return requiredSkills;
    }

    public String getDefaultSkill() {
        return defaultSkill;
    }

    public List<String> getRequiredSkillsList() {
        return Collections.unmodifiableList(new ArrayList<String>(requiredSkills));
    }
}
