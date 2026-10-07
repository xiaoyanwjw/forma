/**
 * Pi Runtime 根包（vendor-copy 自 LIMS，包名为 {@code com.xmut.forma.pi.agent}）。
 * 功能描述：提供 Agent 运行时；业务只依赖 session.AgentSession。
 * 关键设计：内部 Agent 禁止当业务门面注入。配置前缀 {@code forma.pi.*}。
 */
package com.xmut.forma.pi.agent;
