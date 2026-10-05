package com.xmut.forma.extension.output;

import com.xmut.forma.common.output.OutputParseContext;
import com.xmut.forma.common.output.OutputParser;
import com.xmut.forma.common.output.ParsedGenerationOutput;
import org.springframework.core.annotation.Order;

import java.util.Objects;

/**
 * 按本轮路径读盘。
 * 功能描述：上下文带了合法 output 时接手，从工作区读该文件。
 * 关键设计：闲聊未带路径时 {@code appliesTo} 为 false；带了却缺文件必须抛错，不能当成闲聊成功。
 */
@Order(0)
public final class WorkspaceOutputParser implements OutputParser {

    private final WorkspaceOutputReader reader;

    public WorkspaceOutputParser() {
        this(new WorkspaceOutputReader());
    }

    WorkspaceOutputParser(WorkspaceOutputReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    @Override
    public boolean appliesTo(OutputParseContext ctx) {
        if (ctx == null) {
            return false;
        }
        return TurnDeliverableKeys.output(ctx.getAttachment()) != null;
    }

    @Override
    public ParsedGenerationOutput parse(OutputParseContext ctx) {
        String output = ctx == null ? null : TurnDeliverableKeys.output(ctx.getAttachment());
        if (output == null) {
            throw new IllegalArgumentException("output file missing: view.json");
        }

        return reader.read(ctx.getRunWorkspace(), output);
    }
}
