package com.xmut.lims.pi.agent.config;

import com.xmut.lims.pi.agent.IterationBudget;
import com.xmut.lims.pi.agent.graph.checkpoint.Checkpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore;
import com.xmut.lims.pi.agent.graph.checkpoint.ResumeIdempotencyStore;
import com.xmut.lims.pi.agent.agent.CompressionConfig;
import com.xmut.lims.pi.agent.agent.ContextCompressor;
import com.xmut.lims.pi.agent.agent.Agent;
import com.xmut.lims.pi.agent.agent.DefaultContextCompressor;
import com.xmut.lims.pi.agent.agent.DefaultAgent;
import com.xmut.lims.pi.agent.agent.DefaultPromptBuilder;
import com.xmut.lims.pi.agent.agent.DefaultToolLoopGraph;
import com.xmut.lims.pi.agent.agent.PromptBuilder;
import com.xmut.lims.pi.ai.model.InMemoryModelCatalog;
import com.xmut.lims.pi.ai.model.ModelCatalog;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ProtocolRoutingModelProvider;
import com.xmut.lims.pi.ai.model.StubModelProvider;
import com.xmut.lims.pi.ai.model.decorator.ModelDecorators;
import com.xmut.lims.pi.agent.event.DefaultPiEventBus;
import com.xmut.lims.pi.agent.event.PiEventBus;
import com.xmut.lims.pi.agent.extension.ExtensionRunner;
import com.xmut.lims.pi.agent.extension.PiExtension;
import com.xmut.lims.pi.agent.extension.ToolPolicyExtension;
import com.xmut.lims.pi.agent.resource.DefaultPiResourceLoader;
import com.xmut.lims.pi.agent.resource.PiResourceLoader;
import com.xmut.lims.pi.agent.session.AgentSession;
import com.xmut.lims.pi.agent.session.DefaultAgentSession;
import com.xmut.lims.pi.agent.session.SessionStore;
import com.xmut.lims.pi.agent.session.SqliteSessionStore;
import com.xmut.lims.pi.agent.skill.ClasspathSkillBootstrap;
import com.xmut.lims.pi.agent.skill.InMemorySkillConfig;
import com.xmut.lims.pi.agent.skill.SkillConfig;
import com.xmut.lims.pi.agent.skill.SkillConfigProperties;
import com.xmut.lims.pi.agent.tool.ClasspathToolBootstrap;
import com.xmut.lims.pi.agent.tool.DefaultToolConfig;
import com.xmut.lims.pi.agent.tool.ToolBinding;
import com.xmut.lims.pi.agent.tool.ToolConfig;
import com.xmut.lims.pi.agent.tool.ToolHandlerAutoBinder;
import com.xmut.lims.pi.agent.tool.ToolManifest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Pi Runtime 装配（Story 51-1 … 51-12）：对外唯一门面 {@link AgentSession}。
 *
 * <p>组装 PromptBuilder + ModelProvider + ToolConfig [LIMS] + ContextCompressor +
 * SkillConfig + 默认 Tool-loop 图与 {@link DefaultAgent}。
 *
 * <p>Checkpoint：无 {@code JedisPool} 时回落 {@link InMemoryCheckpointer}；有池时
 * {@link PiCheckpointAutoConfiguration} 以 {@code @Primary} 注册 Redis 实现。
 *
 * <p>Session：默认 {@link SqliteSessionStore}（{@code lims.pi.session.sqlite-path}，
 * 缺省 {@code {user.dir}/.lippi-pi/state.db}）。{@code InMemorySessionStore} 仅单测 /
 * 显式 {@code @Bean} 覆盖。不做 Redis/MySQL Session；≠ Checkpoint。
 * Memory 层暂未接入。
 *
 * <p>ContextCompressor：默认 {@link DefaultContextCompressor}；{@code pi.compression.enabled=false}
 * 时注册 {@link ContextCompressor#NOOP}。
 *
 * <p>SkillConfig：启动扫 {@code classpath*:skills/*.skill.json}
 * （含 {@code certificate.ocr}）；然后 {@code sealBootstrap}；
 * {@code pi.skills.allow-runtime-mutation=false}。
 *
 * <p>ConversationLoop：仅被 {@link AgentSession} 内部委托；禁止业务直接注入作门面。
 *
 * <p>ToolConfig：启动扫 {@code classpath*:tools/*.tool.json}。
 */
@Configuration
@Order(Ordered.LOWEST_PRECEDENCE)
public class AgentConfiguration {

    @Bean
    @ConditionalOnMissingBean(Checkpointer.class)
    public Checkpointer checkpointer() {
        return new InMemoryCheckpointer();
    }

    @Bean
    @ConditionalOnMissingBean(ResumeIdempotencyStore.class)
    public ResumeIdempotencyStore resumeIdempotencyStore() {
        return new InMemoryResumeIdempotencyStore();
    }

    @Bean
    @ConditionalOnMissingBean(ModelCatalog.class)
    public ModelCatalog modelCatalog() {
        return InMemoryModelCatalog.defaults();
    }

    /**
     * 无 API Key 时装配 Stub；外层 ProtocolRouting + Decorator（NOOP 反向端口）。
     */
    @Bean
    @ConditionalOnMissingBean(ModelProvider.class)
    public ModelProvider modelProvider(ModelCatalog modelCatalog) {
        ModelProvider stub = new StubModelProvider();
        ModelProvider routed = new ProtocolRoutingModelProvider(stub, modelCatalog);
        return ModelDecorators.wrapWithNoopPorts(routed, modelCatalog);
    }

    @Bean
    @ConditionalOnMissingBean(PromptBuilder.class)
    public PromptBuilder promptBuilder() {
        return new DefaultPromptBuilder();
    }

    @Bean
    @ConditionalOnMissingBean(CompressionConfig.class)
    public CompressionConfig compressionConfig(
            @Value("${pi.compression.max-prompt-chars:48000}") int maxPromptChars,
            @Value("${pi.compression.protect-last-k:8}") int protectLastK,
            @Value("${pi.compression.context-max-chars:4000}") int contextMaxChars) {
        return CompressionConfig.builder()
                .enabled(true)
                .maxPromptChars(maxPromptChars)
                .protectLastK(protectLastK)
                .contextMaxChars(contextMaxChars)
                .build();
    }

    /**
     * 默认可注入 ContextCompressor；disabled → NOOP。
     * 仅当 Catalog 已注册 {@link ContextCompressor#USE_CASE_COMPRESSION} 时注入 ModelProvider
     * （AC5：无该 useCase → deterministic，不调模型）。
     */
    @Bean
    @ConditionalOnMissingBean(ContextCompressor.class)
    public ContextCompressor contextCompressor(CompressionConfig compressionConfig,
                                               ModelProvider modelProvider,
                                               ModelCatalog modelCatalog) {
        if (compressionConfig == null || !compressionConfig.isEnabled()) {
            return ContextCompressor.NOOP;
        }
        ModelProvider model = null;
        if (modelCatalog != null
                && modelCatalog.registeredUseCases() != null
                && modelCatalog.registeredUseCases().contains(ContextCompressor.USE_CASE_COMPRESSION)) {
            model = modelProvider;
        }
        return new DefaultContextCompressor(compressionConfig, model);
    }

    /**
     * 默认 ToolConfig：扫 {@code classpath*:tools/*.tool.json}；
     * 若 JSON 含 {@code handlerClass} → {@link ToolHandlerAutoBinder} 自动创建并绑定 Handler。
     *
     * <p>仅 JSON、无 handlerClass → 声明型工具（有 schema、不可执行）。
     */
    @Bean
    @ConditionalOnMissingBean(ToolConfig.class)
    public ToolConfig toolConfig(ResourcePatternResolver resourcePatternResolver,
                                 org.springframework.beans.factory.BeanFactory beanFactory) {
        List<ToolManifest> scanned = ClasspathToolBootstrap.load(resourcePatternResolver);
        List<ToolBinding> coded = new ArrayList<>(
                ToolHandlerAutoBinder.bindFromManifests(scanned, beanFactory));
        return DefaultToolConfig.merge(scanned, coded);
    }

    @Bean
    @ConditionalOnMissingBean(SkillConfigProperties.class)
    public SkillConfigProperties skillConfigProperties(@Value("${pi.skills.allow-runtime-mutation:false}") boolean allowRuntimeMutation) {
        return new SkillConfigProperties(allowRuntimeMutation);
    }

    /**
     * 默认可注入 SkillConfig：扫 {@code classpath*:skills/*.skill.json}
     * （含 certificate.ocr）→ {@code sealBootstrap}。
     */
    @Bean
    @ConditionalOnMissingBean(SkillConfig.class)
    public SkillConfig skillConfig(ResourcePatternResolver resourcePatternResolver,
                                   SkillConfigProperties skillConfigProperties) {
        InMemorySkillConfig config = new InMemorySkillConfig(skillConfigProperties);
        ClasspathSkillBootstrap.load(config, resourcePatternResolver);
        config.sealBootstrap();
        return config;
    }

    /**
     * 生产默认 SessionStore = SQLite 文件库（Story 51-17）。
     *
     * <p>{@code lims.pi.session.sqlite-path} 空则 {@code {user.dir}/.lippi-pi/state.db}。
     * 单测请 {@code new InMemorySessionStore()} 或测试 {@code @Bean}/{@code @Primary} 覆盖。
     * <b>不做</b> Redis / MySQL Session。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(SessionStore.class)
    public SessionStore sessionStore(
            @Value("${lims.pi.session.sqlite-path:}") String sqlitePath) {
        return new SqliteSessionStore(SqliteSessionStore.resolveSqlitePath(sqlitePath));
    }

    /**
     * 冻结 Agent Context：委托既有 Skill/Tool bootstrap，另扫 {@code classpath*:prompts/*.md}。
     * {@code reload()} 始终可用。
     */
    @Bean
    @ConditionalOnMissingBean(PiResourceLoader.class)
    public PiResourceLoader piResourceLoader(ResourcePatternResolver resourcePatternResolver,
                                             SkillConfig skillConfig,
                                             ToolConfig toolConfig,
                                             ExtensionRunner extensionRunner) {
        return new DefaultPiResourceLoader(
                resourcePatternResolver,
                skillConfig,
                toolConfig,
                extensionRunner.extensionNames());
    }

    /**
     * [LIMS] 必装工具策略闸门。作为 {@link PiExtension} 进 Runner 列表。
     */
    @Bean
    public ToolPolicyExtension toolPolicyExtension(ToolConfig toolConfig) {
        return new ToolPolicyExtension(toolConfig);
    }

    /**
     * Extension registrar；列表中必须有且仅有一个 {@link ToolPolicyExtension}。
     * 创建 Session bus 后 {@code register(bus)}，不再对外扇出方法钩子。
     */
    @Bean
    @ConditionalOnMissingBean(ExtensionRunner.class)
    public ExtensionRunner extensionRunner(@Autowired(required = false) java.util.List<PiExtension> extensions) {
        return new ExtensionRunner(Optional.ofNullable(extensions).orElse(Collections.emptyList()));
    }

    /**
     * 唯一对外公共 Bean：业务注入 {@link AgentSession}。
     * <p>不注册 Runtime {@code Agent} Bean（已迁 {@code pi.agent}，仅内部可选使用）。
     * <p>不注册 {@link Agent} Bean；Loop 仅在 Session 内构造委托。
     */
    @Bean
    @ConditionalOnMissingBean(AgentSession.class)
    public AgentSession agentSession(ModelProvider modelProvider,
                                     PromptBuilder promptBuilder,
                                     ToolConfig toolConfig,
                                     SkillConfig skillConfig,
                                     Checkpointer checkpointStore,
                                     ResumeIdempotencyStore resumeIdempotencyStore,
                                     SessionStore sessionStore,
                                     PiResourceLoader ResourceLoader,
                                     ContextCompressor compressor,
                                     ExtensionRunner extensionRunner) {

        PiEventBus eventBus = new DefaultPiEventBus();
        extensionRunner.register(eventBus);

        Agent agent = new DefaultAgent(
                DefaultToolLoopGraph.build(modelProvider, promptBuilder, toolConfig, compressor),
                checkpointStore,
                resumeIdempotencyStore,
                new IterationBudget(25),
                toolConfig,
                skillConfig);

        return new DefaultAgentSession(agent, sessionStore, ResourceLoader, eventBus);
    }
}
