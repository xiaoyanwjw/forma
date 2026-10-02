package com.xmut.ebus.application.business.scene.dto;

/**
 * 工作台快捷栏一项（仅 UI 元数据；不含 skill 正文）。
 */
public class SceneSkillCapsuleItemDTO {

    private String skillId;
    private String label;
    private String examplePrompt;
    private int sortOrder;

    public SceneSkillCapsuleItemDTO() {
    }

    public SceneSkillCapsuleItemDTO(String skillId, String label, String examplePrompt, int sortOrder) {
        this.skillId = skillId;
        this.label = label;
        this.examplePrompt = examplePrompt;
        this.sortOrder = sortOrder;
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getExamplePrompt() {
        return examplePrompt;
    }

    public void setExamplePrompt(String examplePrompt) {
        this.examplePrompt = examplePrompt;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
