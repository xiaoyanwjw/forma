package com.xmut.forma.pi.agent.skill;

/**
 * Skill 校验/突变异常。
 * 功能描述：在 Manifest 非法或生产拒绝运行时突变时抛出。
 */
public class SkillValidationException extends RuntimeException {

    public SkillValidationException(String message) {
        super(message);
    }

    public SkillValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
