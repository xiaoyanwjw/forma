package com.xmut.lims.pi.agent.skill;

import com.xmut.lims.pi.agent.tool.ToolLevel;
import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 不可变 Skill 资产描述。
 * 功能描述：声明 prompt、toolWhitelist、maxToolLevel、graphTopology。
 */
@Value
@Builder(toBuilder = true)
public class SkillManifest {

    /** 稳定 skill 键，如 {@code certificate.ocr}。 */
    String id;

    /** 版本字符串（MVP 仅要求非空；推荐 semver）。 */
    String version;

    String displayName;

    String description;

    /** 内联 skills prompt；与 {@link #promptRef} 至少一个。 */
    String skillsPrompt;

    /** 外部 prompt 引用（如 {@code classpath:skills/certificate-ocr.md}）。 */
    String promptRef;

    /**
     * 工具名白名单；非 null，可 empty（= 不向模型暴露任何 tools）。
     */
    @Builder.Default
    List<String> toolWhitelist = Collections.emptyList();

    /** 本 Skill 允许的最高工具等级；FORBIDDEN 入册拒绝。 */
    ToolLevel maxToolLevel;

    SkillGraphTopology graphTopology;

    /** 可选；OCR multimodal useCase 留给 51-5。 */
    String modelUseCase;

    /** 可选内容 hash（备 51-5）。 */
    String contentHash;

    SkillManifest(String id,
                  String version,
                  String displayName,
                  String description,
                  String skillsPrompt,
                  String promptRef,
                  List<String> toolWhitelist,
                  ToolLevel maxToolLevel,
                  SkillGraphTopology graphTopology,
                  String modelUseCase,
                  String contentHash) {
        this.id = id;
        this.version = version;
        this.displayName = displayName;
        this.description = description;
        this.skillsPrompt = skillsPrompt;
        this.promptRef = promptRef;
        // null 保留给校验拒绝（AC2）；builder 未设时 @Builder.Default → empty
        this.toolWhitelist = toolWhitelist == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(toolWhitelist));
        this.maxToolLevel = maxToolLevel;
        this.graphTopology = graphTopology;
        this.modelUseCase = modelUseCase;
        this.contentHash = contentHash;
    }
}
