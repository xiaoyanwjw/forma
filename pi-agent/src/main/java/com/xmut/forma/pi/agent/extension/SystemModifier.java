package com.xmut.forma.pi.agent.extension;

import org.springframework.util.StringUtils;

/**
 * 可变 system prompt 三槽：overwrite 后写覆盖；append 非空段用 {@code \n\n} 拼接。
 */
public final class SystemModifier {

    private String owStable;
    private String owContext;
    private String owVariable;
    private String apStable;
    private String apContext;
    private String apVariable;

    public void overwrite(String stable, String context, String variable) {
        owStable = lastNonBlank(owStable, stable);
        owContext = lastNonBlank(owContext, context);
        owVariable = lastNonBlank(owVariable, variable);
    }

    public void append(String stable, String context, String variable) {
        apStable = join(apStable, stable);
        apContext = join(apContext, context);
        apVariable = join(apVariable, variable);
    }

    public void appendVariable(String variable) {
        append(null, null, variable);
    }

    public PromptSegments getOverwrite() {
        if (!anyText(owStable, owContext, owVariable)) {
            return null;
        }
        return PromptSegments.of(owStable, owContext, owVariable);
    }

    public PromptSegments getAppend() {
        if (!anyText(apStable, apContext, apVariable)) {
            return null;
        }
        return PromptSegments.of(apStable, apContext, apVariable);
    }

    void applySegments(PromptSegments overwrite, PromptSegments append) {
        if (overwrite != null && !overwrite.isEmpty()) {
            overwrite(overwrite.getStable(), overwrite.getContext(), overwrite.getVariable());
        }
        if (append != null && !append.isEmpty()) {
            append(append.getStable(), append.getContext(), append.getVariable());
        }
    }

    private static String lastNonBlank(String previous, String next) {
        if (!StringUtils.hasText(next)) {
            return previous;
        }
        return next.trim();
    }

    private static String join(String left, String right) {
        if (!StringUtils.hasText(right)) {
            return left;
        }
        String trimmed = right.trim();
        if (!StringUtils.hasText(left)) {
            return trimmed;
        }
        return left + "\n\n" + trimmed;
    }

    private static boolean anyText(String a, String b, String c) {
        return StringUtils.hasText(a) || StringUtils.hasText(b) || StringUtils.hasText(c);
    }
}
