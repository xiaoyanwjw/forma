package com.xmut.ebus.application.business.scene.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 场景工作台 skill 胶囊栏（label + 示例句；不含 skill 正文）。
 */
public class SceneSkillCapsuleDTO {

    private String sceneCode;
    private List<SceneSkillCapsuleItemDTO> skills = Collections.emptyList();

    public SceneSkillCapsuleDTO() {
    }

    public SceneSkillCapsuleDTO(String sceneCode, List<SceneSkillCapsuleItemDTO> skills) {
        this.sceneCode = sceneCode;
        this.skills = skills == null
                ? Collections.<SceneSkillCapsuleItemDTO>emptyList()
                : Collections.unmodifiableList(new ArrayList<SceneSkillCapsuleItemDTO>(skills));
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public void setSceneCode(String sceneCode) {
        this.sceneCode = sceneCode;
    }

    public List<SceneSkillCapsuleItemDTO> getSkills() {
        return skills;
    }

    public void setSkills(List<SceneSkillCapsuleItemDTO> skills) {
        this.skills = skills == null
                ? Collections.<SceneSkillCapsuleItemDTO>emptyList()
                : Collections.unmodifiableList(new ArrayList<SceneSkillCapsuleItemDTO>(skills));
    }
}
