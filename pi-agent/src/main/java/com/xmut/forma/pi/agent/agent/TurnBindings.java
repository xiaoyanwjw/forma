package com.xmut.forma.pi.agent.agent;

import com.xmut.forma.pi.agent.graph.StateKeys;
import com.xmut.forma.pi.ai.model.ToolSchema;
import com.xmut.forma.pi.agent.skill.Skill;
import lombok.Builder;
import lombok.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 本轮入图装载快照。
 * 功能描述：对称携带本轮 Skill 与 Tool 相关字段。
 */
@Value
@Builder
public class TurnBindings {

    /**
     * → PromptBuilder skills 段（入图前编进 SYSTEM_PROMPT，不写 Graph state）
     */
    String skillsText;

    /**
     * → PromptBuilder tools 段（与 skillsText 对称；入图前编进 SYSTEM_PROMPT）
     */
    String toolsText;

    /**
     * → {@link StateKeys#AVAILABLE_SKILLS}；null = 不写；empty = 本轮无 skill。
     */
    List<Skill> availableSkills;

    /**
     * → {@link StateKeys#AVAILABLE_TOOLS}；null = 不写；empty = 显式无 tools。
     */
    List<ToolSchema> availableTools;

    /**
     * → {@link StateKeys#ACTIVE_SKILL_ID}（可由 availableSkills 推导；便于测）
     */
    String activeSkillId;

    /**
     * → {@link StateKeys#MODEL_USE_CASE}（ActiveSkill.modelUseCase；可空）
     */
    String modelUseCase;

    /**
     * → {@link StateKeys#ACTIVE_TOOLS}；null = 不写（不按名限制）。
     */
    List<String> activeTools;

    /**
     * 写入入图 state（chat 轴键由 Loop 另写）。
     */
    public void applyTo(Map<String, Object> state) {
        if (state == null) {
            return;
        }
        if (StringUtils.hasText(activeSkillId)) {
            state.put(StateKeys.ACTIVE_SKILL_ID, activeSkillId);
        }
        if (!CollectionUtils.isEmpty(activeTools)) {
            state.put(StateKeys.ACTIVE_TOOLS, Collections.unmodifiableList(new ArrayList<>(activeTools)));
        }
        if (StringUtils.hasText(modelUseCase)) {
            state.put(StateKeys.MODEL_USE_CASE, modelUseCase);
        }
        applyList(state, StateKeys.AVAILABLE_SKILLS, availableSkills);
        applyList(state, StateKeys.AVAILABLE_TOOLS, availableTools);
    }

    private static void applyList(Map<String, Object> state, String key, List<?> list) {
        if (list == null) {
            return;
        }
        if (list.isEmpty()) {
            state.remove(key);
        } else {
            state.put(key, Collections.unmodifiableList(new ArrayList<>(list)));
        }
    }

    public static TurnBindings empty() {
        return TurnBindings.builder().build();
    }
}
