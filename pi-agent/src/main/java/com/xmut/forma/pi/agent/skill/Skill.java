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

    /**
     * SKILL.md {@code metadata.output.persistAs}；可 null。
     * 有值且非 {@code none} 时，计费生成按该码落成果。
     */
    String persistAs;

    /**
     * SKILL.md {@code metadata.output.hideFromHistory}：中间稿类型码，不进会话历史。
     */
    @Builder.Default
    List<String> hideFromHistory = Collections.emptyList();

    /** SKILL.md {@code metadata.output.viewPath}；可 null。 */
    String viewPath;

    /** SKILL.md {@code metadata.output.artifactPath}；可 null。 */
    String artifactPath;

    /** SKILL.md {@code metadata.output.planViewPath}；可 null。 */
    String planViewPath;

    /** SKILL.md {@code metadata.output.planArtifactPath}；可 null。 */
    String planArtifactPath;

    Skill(String id,
                  String description,
                  String promptRef,
                  List<String> allowedTools,
                  String sceneCode,
                  String persistAs,
                  List<String> hideFromHistory,
                  String viewPath,
                  String artifactPath,
                  String planViewPath,
                  String planArtifactPath) {
        this.id = id;
        this.description = description;
        this.promptRef = promptRef;
        this.allowedTools = allowedTools == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(allowedTools));
        this.sceneCode = sceneCode;
        this.persistAs = persistAs;
        this.hideFromHistory = hideFromHistory == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(hideFromHistory));
        this.viewPath = viewPath;
        this.artifactPath = artifactPath;
        this.planViewPath = planViewPath;
        this.planArtifactPath = planArtifactPath;
    }
}
