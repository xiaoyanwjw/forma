package com.xmut.forma.common.output;

import lombok.Builder;
import lombok.Value;

import java.nio.file.Path;

/**
 * 一次成果解析的入参。
 * 功能描述：把 skill、场景、续跑选项、终稿和工作区交给 {@link OutputParser}。
 * 关键设计：只读；字段均可空，谁接手由实现方的 {@code appliesTo} 决定。
 */
@Value
@Builder
public class OutputParseContext {

    String skillId;
    String sceneCode;
    String resumeOptionId;
    String finalResponse;
    Path runWorkspace;
    /** 本轮交付附件；闲聊可空（null 视为 empty）。 */
    @Builder.Default
    TurnAttachment attachment = TurnAttachment.empty();

    public OutputParseContext(String skillId,
                              String sceneCode,
                              String resumeOptionId,
                              String finalResponse,
                              Path runWorkspace,
                              TurnAttachment attachment) {
        this.skillId = skillId;
        this.sceneCode = sceneCode;
        this.resumeOptionId = resumeOptionId;
        this.finalResponse = finalResponse;
        this.runWorkspace = runWorkspace;
        this.attachment = attachment == null ? TurnAttachment.empty() : attachment;
    }
}
