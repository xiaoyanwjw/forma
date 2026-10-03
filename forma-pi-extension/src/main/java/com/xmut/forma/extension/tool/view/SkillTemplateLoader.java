package com.xmut.forma.extension.tool.view;

/**
 * Loads a Mustache template for the active skill.
 * Production uses the skill catalog; tests can return a fixed string.
 */
public interface SkillTemplateLoader {

    /**
     * @param activeSkillId skill id from {@code ToolContext}
     * @param templateRelativePath path under the skill resource root
     * @return template source
     */
    String load(String activeSkillId, String templateRelativePath);
}
