package com.xmut.lims.pi.agent.skill;

/**
 * SkillManifest 校验失败或生产运行时突变被拒（Story 51-9）。
 */
public class SkillValidationException extends RuntimeException {

    public SkillValidationException(String message) {
        super(message);
    }

    public SkillValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
