package com.xmut.forma.common.output;

/**
 * 成果解析 SPI。
 * 功能描述：按本轮上下文决定是否接手，并解析终稿。
 * 关键设计：实现放在 application 或 extension；本接口不读盘、不含场景分支。
 */
public interface OutputParser {

    boolean appliesTo(OutputParseContext ctx);

    ParsedGenerationOutput parse(OutputParseContext ctx);
}
