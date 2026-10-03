package com.xmut.forma.application.business.computer;

import com.xmut.forma.common.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * No-skill turns: wrap final model text as a v2 markdown document.
 */
@Component
public class NoSkillMarkdownProjector implements ComputerViewProjector {

    @Override
    public boolean supports(ViewProjectContext context) {
        return context != null
                && !context.isSkillBound()
                && StringUtils.hasText(context.getFinalResponse());
    }

    @Override
    public Map<String, Object> project(ViewProjectContext context) {
        return ComputerDocument.v2("draft", "markdown", context.getFinalResponse().trim());
    }
}
