package com.xmut.forma.application.business.scene.pack;

import com.xmut.forma.pi.agent.skill.Skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 场景能力包：按 sceneCode 绑定的已注册 skill 快照（正文由 Pi ResourceLoader 加载，本类不解析）。
 */
public final class SceneCapabilityPack {

    private final String sceneCode;
    private final Map<String, Skill> skillsById;

    public SceneCapabilityPack(String sceneCode, List<Skill> skills) {
        if (sceneCode == null || sceneCode.trim().isEmpty()) {
            throw new IllegalArgumentException("sceneCode required");
        }
        this.sceneCode = sceneCode.trim();
        Map<String, Skill> map = new LinkedHashMap<String, Skill>();
        if (skills != null) {
            for (Skill skill : skills) {
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

    public List<Skill> getSkills() {
        return Collections.unmodifiableList(new ArrayList<Skill>(skillsById.values()));
    }

    public Optional<Skill> findSkill(String skillId) {
        if (skillId == null || skillId.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(skillsById.get(skillId.trim()));
    }

    public boolean hasSkill(String skillId) {
        return findSkill(skillId).isPresent();
    }
}
