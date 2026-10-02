package com.xmut.lims.pi.agent.config;

import com.xmut.lims.pi.agent.ResumeRequest;
import com.xmut.lims.pi.agent.agent.*;
import com.xmut.lims.pi.agent.graph.checkpoint.Checkpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.ResumeIdempotencyStore;
import com.xmut.lims.pi.agent.tool.base.ReadSkill;
import com.xmut.lims.pi.ai.model.ModelCatalog;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.agent.session.AgentSession;
import com.xmut.lims.pi.agent.session.DefaultAgentSession;
import com.xmut.lims.pi.agent.session.InMemorySessionStore;
import com.xmut.lims.pi.agent.session.PromptRequest;
import com.xmut.lims.pi.agent.session.SessionStore;
import com.xmut.lims.pi.agent.session.SqliteSessionStore;
import com.xmut.lims.pi.agent.session.TurnResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PiAutoConfigurationTest {

    @TempDir
    Path tempDir;

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PiAutoConfiguration.class));
    }

    private ApplicationContextRunner contextRunnerWithSqlite() {
        Path db = tempDir.resolve("pi-session-test.db");
        return contextRunner()
                .withPropertyValues("lims.pi.session.sqlite-path=" + db.toAbsolutePath());
    }

    @Test
    void agentSession_bean_assembled_without_public_agent_bean() {
        contextRunner().run(context -> {
            assertThat(context).hasSingleBean(AgentSession.class);
            assertThat(context.getBean(AgentSession.class)).isInstanceOf(DefaultAgentSession.class);
            assertThat(context).doesNotHaveBean(Agent.class);
            assertThat(context).hasSingleBean(SessionStore.class);
            assertThat(context.getBean(SessionStore.class)).isInstanceOf(InMemorySessionStore.class);
            assertThat(context).hasSingleBean(Checkpointer.class);
            assertThat(context.getBean(Checkpointer.class)).isInstanceOf(InMemoryCheckpointer.class);
            assertThat(context).hasSingleBean(ResumeIdempotencyStore.class);
            assertThat(context).hasSingleBean(ModelProvider.class);
            assertThat(context).hasSingleBean(ModelCatalog.class);
            assertThat(context).hasSingleBean(PromptBuilder.class);
            assertThat(context.getBean(PromptBuilder.class)).isInstanceOf(DefaultPromptBuilder.class);
            assertThat(context).doesNotHaveBean("memoryStore");
            assertThat(context).doesNotHaveBean("memoryManager");
            assertThat(context).hasSingleBean(ContextCompressor.class);
            assertThat(context).hasSingleBean(CompressionConfig.class);
            assertThat(context).hasSingleBean(com.xmut.lims.pi.agent.skill.SkillCatalog.class);
            assertThat(context).hasSingleBean(com.xmut.lims.pi.agent.skill.SkillCatalogProperties.class);
            assertThat(context.getBean(com.xmut.lims.pi.agent.skill.SkillCatalogProperties.class)
                    .isAllowRuntimeMutation()).isFalse();
            assertThat(context.getBean(com.xmut.lims.pi.agent.skill.SkillCatalog.class)
                    .resolve("ecommerce-picklist")).isPresent();
            assertThat(context.getBean(com.xmut.lims.pi.agent.skill.SkillCatalog.class)
                    .listByScene("ecommerce"))
                    .extracting(com.xmut.lims.pi.agent.skill.Skill::getId)
                    .contains("ecommerce-picklist", "ecommerce-skulist");
            assertThat(context.getBean(com.xmut.lims.pi.agent.tool.ToolCatalog.class)
                    .resolve("read_skill")).isPresent();
            assertThat(context.getBean(com.xmut.lims.pi.agent.tool.ToolCatalog.class)
                    .handlerOf("read_skill")).isPresent();
            assertThat(context.getBean(com.xmut.lims.pi.agent.tool.ToolCatalog.class)
                    .handlerOf("read_skill").get())
                    .isInstanceOf(ReadSkill.class);
            assertThat(context).hasSingleBean(com.xmut.lims.pi.agent.resource.PiResourceLoader.class);
            assertThat(context).hasSingleBean(com.xmut.lims.pi.agent.extension.ExtensionRunner.class);
            assertThat(context).hasSingleBean(com.xmut.lims.pi.agent.extension.ToolPolicyExtension.class);
            assertThat(context.getBean(com.xmut.lims.pi.agent.extension.ToolPolicyExtension.class)
                    .isWriteApprovalEnabled()).isFalse();
            assertThat(context.getBean(com.xmut.lims.pi.agent.resource.PiResourceLoader.class)
                    .findPrompt("ping")).isPresent();
            assertThat(context.getBean(AgentSession.class))
                    .isSameAs(context.getBean(AgentSession.class));
        });
    }

    @Test
    void write_approval_enabled_property_wires_tool_policy() {
        contextRunner()
                .withPropertyValues("lims.pi.tool.write-approval.enabled=true")
                .run(context -> assertThat(
                        context.getBean(com.xmut.lims.pi.agent.extension.ToolPolicyExtension.class)
                                .isWriteApprovalEnabled()).isTrue());
    }

    @Test
    void default_session_store_is_in_memory_without_creating_cwd_sqlite() {
        Path cwdDb = java.nio.file.Paths.get(System.getProperty("user.dir"), ".lippi-pi", "state.db");
        boolean existedBefore = java.nio.file.Files.exists(cwdDb);
        contextRunner().run(context -> {
            assertThat(context.getBean(SessionStore.class)).isInstanceOf(InMemorySessionStore.class);
            if (!existedBefore) {
                assertThat(cwdDb).doesNotExist();
            }
        });
    }

    @Test
    void explicit_sqlite_path_registers_sqlite_session_store() {
        contextRunnerWithSqlite().run(context -> {
            assertThat(context.getBean(SessionStore.class)).isInstanceOf(SqliteSessionStore.class);
        });
    }

    @Test
    void builtin_scene_skills_sealed_after_boot() {
        contextRunner().run(context -> {
            com.xmut.lims.pi.agent.skill.SkillCatalog skills =
                    context.getBean(com.xmut.lims.pi.agent.skill.SkillCatalog.class);
            assertThat(skills.resolve("ecommerce-picklist")).isPresent();
            assertThatThrownBy(() -> skills.registerBootstrap(
                    com.xmut.lims.pi.agent.skill.Skill.builder()
                            .id("late")
                            .description("p")
                            .promptRef("classpath:skills/late.md")
                            .allowedTools(java.util.Collections.emptyList())
                            .build()))
                    .isInstanceOf(com.xmut.lims.pi.agent.skill.SkillValidationException.class)
                    .hasMessageContaining("bootstrap window closed");
        });
    }

    @Test
    void prompt_runs_default_tool_loop_returns_ok() {
        contextRunner().run(context -> {
            AgentSession session = context.getBean(AgentSession.class);
            TurnResult result = session.prompt(PromptRequest.builder()
                    .text("hello")
                    .build());
            assertThat(result.getStatus()).isEqualTo(TurnResult.Status.OK);
            assertThat(result.getFinalResponse()).isEqualTo("hello");
            assertThat(result.getRunId()).isNotBlank();
            assertThat(result.getSessionId()).isNotBlank();
            assertThat(result.getStatus()).isNotEqualTo(TurnResult.Status.NOT_IMPLEMENTED);
        });
    }

    @Test
    void resume_without_checkpoint_returns_failed() {
        contextRunner().run(context -> {
            AgentSession session = context.getBean(AgentSession.class);
            TurnResult result = session.resume(ResumeRequest.builder()
                    .runId("run-missing")
                    .decision(com.xmut.lims.pi.agent.tool.ToolDecision.APPROVE)
                    .build());
            assertThat(result.getStatus()).isEqualTo(TurnResult.Status.FAILED);
            assertThat(result.getFinalResponse()).contains("No checkpoint found");
            assertThat(result.getStatus()).isNotEqualTo(TurnResult.Status.NOT_IMPLEMENTED);
        });
    }

    @Test
    void cancel_blank_runId_is_silent() {
        contextRunner().run(context -> {
            AgentSession session = context.getBean(AgentSession.class);
            session.cancel(null, "test");
            session.cancel("", "test");
            session.cancel("   ", "test");
        });
    }
}
