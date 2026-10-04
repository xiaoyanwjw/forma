package com.xmut.forma.application.business.agent.support;

import com.xmut.forma.common.output.OutputParseContext;
import com.xmut.forma.common.output.OutputParser;
import com.xmut.forma.common.output.ParsedGenerationOutput;
import com.xmut.forma.common.util.StringUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 成果解析兜底。
 * 功能描述：任何一轮都能接手，只看终稿有没有字。
 * 关键设计：不读工作区、不抽 view/artifact、不跟随指针；空白终稿也不当缺文件。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class GenerationOutputParser implements OutputParser {

    @Override
    public boolean appliesTo(OutputParseContext ctx) {
        return true;
    }

    @Override
    public ParsedGenerationOutput parse(OutputParseContext ctx) {
        String raw = ctx == null ? null : ctx.getFinalResponse();
        if (!StringUtils.hasText(raw)) {
            return textPayload("");
        }
        return textPayload(raw.trim());
    }

    private static ParsedGenerationOutput textPayload(String text) {
        return new ParsedGenerationOutput(null, Collections.<String, Object>singletonMap("text", text));
    }
}
