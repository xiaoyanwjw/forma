package com.xmut.forma.application.business.agent.support;

import com.xmut.forma.common.output.OutputParseContext;
import com.xmut.forma.common.output.OutputParser;
import com.xmut.forma.common.output.ParsedGenerationOutput;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 按顺序挑第一个接手的 {@link OutputParser}。
 * 功能描述：工作区 parser 先试；都不接手时落到 {@link GenerationOutputParser}。
 * 关键设计：已经接手的 parser 抛错就失败，不再改走闲聊兜底。收集列表时排除自己。
 */
@Component
public class OutputParserComposite implements OutputParser {

    private final List<OutputParser> parsers;
    private final GenerationOutputParser fallback;

    public OutputParserComposite(List<OutputParser> parsers, GenerationOutputParser fallback) {
        List<OutputParser> ordered = new ArrayList<OutputParser>();
        if (parsers != null) {
            for (OutputParser parser : parsers) {
                if (parser != null && parser != this) {
                    ordered.add(parser);
                }
            }
        }
        AnnotationAwareOrderComparator.sort(ordered);
        this.parsers = ordered;
        this.fallback = fallback;
    }

    @Override
    public boolean appliesTo(OutputParseContext ctx) {
        return true;
    }

    @Override
    public ParsedGenerationOutput parse(OutputParseContext ctx) {
        for (OutputParser p : parsers) {
            if (p != null && p != this && p.appliesTo(ctx)) {
                return p.parse(ctx);
            }
        }
        return fallback.parse(ctx);
    }
}
