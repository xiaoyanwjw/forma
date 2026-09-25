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
import com.xmut.lims.pi.agent.session.InMemorySessionStore;
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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Pi Runtime Spring 装配配置。
 * 功能描述：对外暴露 AgentSession，并组装图、模型、Skill、Tool、Session 等默认 Bean。
 * 关键设计：Session MissingBean 回落 InMemory；Sqlite 仅显式路径；
 * Checkpoint MissingBean 回落 InMemory（Adam 由 ebus-infrastructure MysqlCheckpointer @Primary 覆盖）。
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
     * MissingBean → {@link InMemorySessionStore}（过渡默认，非生产真相）。
     *
     * <p>仅当 {@code lims.pi.session.sqlite-path} 非空时显式 opt-in {@link SqliteSessionStore}。
     * 空路径<b>不得</b>静默创建 {@code {cwd}/.lippi-pi/state.db}（AD-S8）。
     * Adam 生产默认：ebus-infrastructure {@code MysqlSessionStore} {@code @Primary}。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(SessionStore.class)
    public SessionStore sessionStore(@Value("${lims.pi.session.sqlite-path:}") String sqlitePath) {
        if (StringUtils.hasText(sqlitePath)) {
            return new SqliteSessionStore(SqliteSessionStore.resolveSqlitePath(sqlitePath));
        }
        return new InMemorySessionStore();
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
     * 必装工具策略闸门；作为 {@link PiExtension} 进入 Runner。
     *
     * <p>WRITE 审批默认关（AD-S2）；{@code lims.pi.tool.write-approval.enabled=true} 可开。
     */
    @Bean
    public ToolPolicyExtension toolPolicyExtension(
            ToolConfig toolConfig,
            @Value("${lims.pi.tool.write-approval.enabled:false}") boolean writeApprovalEnabled) {
        return new ToolPolicyExtension(toolConfig, writeApprovalEnabled);
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
