package com.xmut.ebus.application.business.scene.pack;

import com.xmut.lims.pi.agent.skill.SkillManifest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 场景能力包：按 sceneCode 绑定的 skill 清单（正文在 classpath 资源，不进库）。
 */
public final class SceneCapabilityPack {

    private final String sceneCode;
    private final Map<String, SkillManifest> skillsById;

    public SceneCapabilityPack(String sceneCode, List<SkillManifest> skills) {
        if (sceneCode == null || sceneCode.trim().isEmpty()) {
            throw new IllegalArgumentException("sceneCode required");
        }
        this.sceneCode = sceneCode.trim();
        Map<String, SkillManifest> map = new LinkedHashMap<String, SkillManifest>();
        if (skills != null) {
            for (SkillManifest skill : skills) {
                if (skill == null || skill.getId() == null) {
                    continue;
                }
                map.put(skill.getId(), skill);
            }
        }
        this.skillsById = Collections.unmodifiableMap(map);
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public List<SkillManifest> getSkills() {
        return Collections.unmodifiableList(new ArrayList<SkillManifest>(skillsById.values()));
    }

    public Optional<SkillManifest> findSkill(String skillId) {
        if (skillId == null || skillId.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(skillsById.get(skillId.trim()));
    }

    public boolean hasSkill(String skillId) {
        return findSkill(skillId).isPresent();
    }
}
