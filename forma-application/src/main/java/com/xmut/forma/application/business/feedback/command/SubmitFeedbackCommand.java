package com.xmut.forma.application.business.feedback.command;

import com.xmut.forma.common.command.BaseCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 提交成果质量反馈（不扣生成分）。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SubmitFeedbackCommand extends BaseCommand {

    private final String artifactId;
    private final String tag;
    private final String commentText;
}
