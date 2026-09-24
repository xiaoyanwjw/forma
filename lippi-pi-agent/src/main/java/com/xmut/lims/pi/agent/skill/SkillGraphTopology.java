package com.xmut.lims.pi.agent.skill;

import com.xmut.lims.pi.agent.agent.DefaultAgent;

/**
 * Skill 声明的图拓扑偏好（Story 51-9）。
 *
 * <p>存入 Manifest 供校验 / 文档 / 后续 Router 参考；
 * {@link DefaultAgent} <strong>不</strong>按此换图——
 * Loop 始终一张 Tool-loop；无 tools 的 Skill（如 OCR）靠空 whitelist 约束。
 */
public enum SkillGraphTopology {

    /** 默认：agent ⇄ policy / tools / human。 */
    TOOL_LOOP,

    /**
     * 声明「偏短路径」的 Skill 偏好（历史 OCR 标签）；
     * 运行时仍走同一 Tool-loop。依赖 {@code read_skill} 的 Skill
     * （如 certificate.ocr / test-standard-schema）须在 whitelist 暴露该工具，
     * 规则正文在 {@code promptRef} md，不靠本枚举换图。
     */
    SIMPLE_AGENT_END
}
