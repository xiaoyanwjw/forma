package com.xmut.forma.pi.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.message.Message;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelProviderCompleteTest {

    @Test
    void complete_without_tools_returns_content_and_empty_toolCalls() {
        ModelProvider provider = new StubModelProvider();
        ModelResponse response = provider.complete(ModelRequest.builder()
                .messages(Collections.singletonList(
                        Message.builder().role("user").content("ping").build()))
                .tools(null)
                .build());

        assertThat(response.getContent()).isEqualTo("ping");
        assertThat(response.getToolCalls()).isNotNull().isEmpty();
        assertThat(response.getFinishReason()).isEqualTo("stop");
    }

    @Test
    void complete_with_empty_tools_same_as_no_tools() {
        ModelProvider provider = new StubModelProvider();
        ModelResponse response = provider.complete(ModelRequest.builder()
                .messages(Collections.singletonList(
                        Message.builder().role("user").content("hi").build()))
                .tools(Collections.emptyList())
                .build());

        assertThat(response.getToolCalls()).isEmpty();
        assertThat(response.getContent()).isEqualTo("hi");
    }

    @Test
    void public_api_has_complete_completeBatch_and_stream_no_chatWithTools() {
        Method[] methods = ModelProvider.class.getDeclaredMethods();
        List<String> names = Arrays.stream(methods).map(Method::getName).collect(java.util.stream.Collectors.toList());
        assertThat(names).contains("complete", "completeBatch", "stream");
        assertThat(names).doesNotContain("chat", "chatWithTools");
        assertThat(ModelProvider.class.getDeclaredMethods()).hasSize(3);
    }

    @Test
    void completeBatch_preserves_order_and_empty_is_empty() {
        ModelProvider provider = new StubModelProvider();
        assertThat(provider.completeBatch(null)).isEmpty();
        assertThat(provider.completeBatch(Collections.<ModelRequest>emptyList())).isEmpty();

        List<ModelRequest> batch = Arrays.asList(
                ModelRequest.builder()
                        .messages(Collections.singletonList(
                                Message.builder().role("user").content("a").build()))
                        .build(),
                ModelRequest.builder()
                        .messages(Collections.singletonList(
                                Message.builder().role("user").content("b").build()))
                        .build());
        List<ModelResponse> responses = provider.completeBatch(batch);
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getContent()).isEqualTo("a");
        assertThat(responses.get(1).getContent()).isEqualTo("b");
        assertThat(ModelCompleteBatchExecutor.POOL.isShutdown()).isFalse();
    }

    @Test
    void completeBatch_runs_complete_concurrently() {
        AtomicInteger inflight = new AtomicInteger();
        AtomicInteger maxInflight = new AtomicInteger();
        ModelProvider provider = request -> {
            int n = inflight.incrementAndGet();
            maxInflight.accumulateAndGet(n, Math::max);
            try {
                Thread.sleep(80L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } finally {
                inflight.decrementAndGet();
            }
            return ModelResponse.builder()
                    .content(request.getMessages().get(0).getContent())
                    .toolCalls(Collections.emptyList())
                    .build();
        };
        List<ModelRequest> batch = Arrays.asList(userReq("a"), userReq("b"), userReq("c"));
        List<ModelResponse> responses = provider.completeBatch(batch);
        assertThat(responses).extracting(ModelResponse::getContent).containsExactly("a", "b", "c");
        assertThat(maxInflight.get()).isGreaterThan(1);
    }

    @Test
    void completeBatch_unwraps_complete_failure() {
        ModelProvider provider = request -> {
            if ("boom".equals(request.getMessages().get(0).getContent())) {
                throw new IllegalArgumentException("bad chunk");
            }
            return ModelResponse.builder()
                    .content("ok")
                    .toolCalls(Collections.emptyList())
                    .build();
        };
        assertThatThrownBy(() -> provider.completeBatch(Arrays.asList(userReq("ok"), userReq("boom"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bad chunk");
    }

    private static ModelRequest userReq(String content) {
        return ModelRequest.builder()
                .messages(Collections.singletonList(Message.builder().role("user").content(content).build()))
                .build();
    }

    @Test
    void default_stream_falls_back_to_complete_as_single_text_delta() {
        ModelProvider provider = new StubModelProvider();
        List<String> deltas = new ArrayList<>();
        AtomicReference<ModelResponse> completed = new AtomicReference<>();

        provider.stream(ModelRequest.builder()
                .messages(Collections.singletonList(
                        Message.builder().role("user").content("ping").build()))
                .build(), new TokenConsumer() {
            @Override
            public void onTextDelta(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onComplete(ModelResponse response) {
                completed.set(response);
            }
        });

        assertThat(deltas).containsExactly("ping");
        assertThat(completed.get().getContent()).isEqualTo("ping");
        assertThat(completed.get().getToolCalls()).isEmpty();
    }

    @Test
    void native_path_passes_tools_and_normalizes_toolCalls() {
        ModelProvider fake = request -> {
            assertThat(request.getTools()).isNotEmpty();
            return ModelResponse.builder()
                    .content(null)
                    .toolCalls(Collections.singletonList(
                            new ToolCallEntry("c1", "echo",
                                    JsonNodeFactory.instance.objectNode().put("x", 1))))
                    .finishReason("tool_calls")
                    .build();
        };
        ModelCatalog catalog = nativeCatalog();
        ModelProvider routed = new ProtocolRoutingModelProvider(fake, catalog);

        ModelResponse response = routed.complete(ModelRequest.builder()
                .useCase("uc-native")
                .messages(Collections.singletonList(
                        Message.builder().role("user").content("call").build()))
                .tools(Collections.singletonList(ToolSchema.builder().name("echo").build()))
                .build());

        assertThat(response.getToolCalls()).hasSize(1);
        assertThat(response.getToolCalls().get(0).getToolName()).isEqualTo("echo");
        assertThat(response.getToolCalls().get(0).getArguments().get("x").asInt()).isEqualTo(1);
    }

    @Test
    void text_fallback_parses_action_blocks() {
        ModelProvider fake = request -> {
            assertThat(request.getTools()).isNullOrEmpty();
            assertThat(request.getMessages().get(0).getRole()).isEqualTo("system");
            return ModelResponse.builder()
                    .content("Thought: need tool\nAction: echo\nAction Input: {\"text\":\"ok\"}\n")
                    .toolCalls(Collections.emptyList())
                    .finishReason("stop")
                    .build();
        };
        ModelCatalog catalog = textCatalog();
        ModelProvider routed = new ProtocolRoutingModelProvider(fake, catalog);

        ModelResponse response = routed.complete(ModelRequest.builder()
                .useCase("uc-text")
                .messages(Collections.singletonList(
                        Message.builder().role("user").content("go").build()))
                .tools(Collections.singletonList(ToolSchema.builder().name("echo").build()))
                .build());

        assertThat(response.getToolCalls()).hasSize(1);
        assertThat(response.getToolCalls().get(0).getToolName()).isEqualTo("echo");
        assertThat(response.getToolCalls().get(0).getArguments().get("text").asText()).isEqualTo("ok");
        assertThat(response.getFinishReason()).isEqualTo("tool_calls");
        assertThat(response.getContent()).doesNotContain("Action:");
        assertThat(response.getContent()).isEqualTo("Thought: need tool");
    }

    @Test
    void arguments_not_double_encoded_from_native_json_string() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        NativeToolCallMapper nativeMapper = new NativeToolCallMapper(mapper);
        JsonNode raw = mapper.readTree(
                "{\"id\":\"1\",\"function\":{\"name\":\"echo\",\"arguments\":\"{\\\"a\\\":2}\"}}");
        ToolCallEntry entry = nativeMapper.fromJson(raw);
        assertThat(entry.getArguments().isObject()).isTrue();
        assertThat(entry.getArguments().get("a").asInt()).isEqualTo(2);
        assertThat(entry.getArguments().isTextual()).isFalse();
    }

    private static ModelCatalog nativeCatalog() {
        return new InMemoryModelCatalog(Collections.singletonMap("uc-native",
                ModelDescriptor.builder()
                        .useCase("uc-native")
                        .provider("fake")
                        .model("m")
                        .supportsNativeToolCalling(true)
                        .build()));
    }

    private static ModelCatalog textCatalog() {
        return new InMemoryModelCatalog(Collections.singletonMap("uc-text",
                ModelDescriptor.builder()
                        .useCase("uc-text")
                        .provider("fake")
                        .model("m")
                        .supportsNativeToolCalling(false)
                        .build()));
    }
}
