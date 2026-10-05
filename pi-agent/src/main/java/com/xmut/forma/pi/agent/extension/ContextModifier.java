package com.xmut.forma.pi.agent.extension;

/**
 * before_agent_start 上下文累加器。
 * 功能描述：对 system prompt 三槽做 overwrite 或 append；可选挂载 UserModifier。
 */
public final class ContextModifier {

    private final SystemModifier system = new SystemModifier();
    private UserModifier user;

    private ContextModifier() {
    }

    private ContextModifier(PromptSegments overwrite, PromptSegments append) {
        system.applySegments(overwrite, append);
    }

    public SystemModifier getSystem() {
        return system;
    }

    public UserModifier getUser() {
        return user;
    }

    /** 仅首次 setUser 生效。 */
    public void setUser(UserModifier userModifier) {
        if (this.user == null && userModifier != null) {
            this.user = userModifier;
        }
    }

    public PromptSegments getOverwrite() {
        return system.getOverwrite();
    }

    public PromptSegments getAppend() {
        return system.getAppend();
    }

    public static ContextModifier empty() {
        return new ContextModifier();
    }

    public static ContextModifier of(PromptSegments overwrite, PromptSegments append) {
        boolean hasOw = overwrite != null && !overwrite.isEmpty();
        boolean hasAp = append != null && !append.isEmpty();
        if (!hasOw && !hasAp) {
            return empty();
        }
        return new ContextModifier(overwrite, append);
    }

    public static ContextModifier overwrite(String stable, String context, String variable) {
        ContextModifier modifier = empty();
        modifier.getSystem().overwrite(stable, context, variable);
        return modifier;
    }

    public static ContextModifier append(String stable, String context, String variable) {
        ContextModifier modifier = empty();
        modifier.getSystem().append(stable, context, variable);
        return modifier;
    }

    /** 仅追加 variable（扩展常用）。 */
    public static ContextModifier appendVariable(String variable) {
        return append(null, null, variable);
    }
}
