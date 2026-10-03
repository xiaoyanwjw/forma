package com.xmut.forma.pi.agent.skill;

import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 不可变 Skill（对齐官方 Pi Skill 值对象）。
 * 功能描述：id（= frontmatter name）、description、promptRef、allowedTools、可选 sceneCode。
 */
@Value
@Builder(toBuilder = true)
public class Skill {

    /** 稳定 skill 键，对应 SKILL.md frontmatter {@code name}。 */
    String id;

    String description;

    /** 外部 prompt 引用（如 {@code classpath:scenes/ecommerce/ecommerce-picklist/SKILL.md}）。 */
    String promptRef;

    /**
     * 本轮可启用的工具名；非 null，可 empty（= 不向模型暴露任何 tools）。
     */
    @Builder.Default
    List<String> allowedTools = Collections.emptyList();

    /** 由资源路径推导的场景码；可 null。 */
    String sceneCode;

    Skill(String id,
                  String description,
                  String promptRef,
                  List<String> allowedTools,
                  String sceneCode) {
        this.id = id;
        this.description = description;
        this.promptRef = promptRef;
        this.allowedTools = allowedTools == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(allowedTools));
        this.sceneCode = sceneCode;
    }
}
