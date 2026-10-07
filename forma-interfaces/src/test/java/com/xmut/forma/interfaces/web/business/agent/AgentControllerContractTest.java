package com.xmut.forma.interfaces.web.business.agent;

import com.xmut.forma.interfaces.vo.business.agent.StartGenerationRunRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁 AD-16：Generation Run API 不得接受系统提示词 / tool 定义正文参数。
 */
class AgentControllerContractTest {

    private static final Set<String> FORBIDDEN = new HashSet<String>(Arrays.asList(
            "systemprompt", "system_prompt", "system", "prompt", "tools", "tooldefs", "tool_definitions",
            "dryrun", "dry_run"));

    @Test
    void streamGenerationRunRequestDoesNotAcceptSystemPromptOrToolBodyFields() throws Exception {
        Method method = AgentController.class.getDeclaredMethod(
                "streamGenerationRun", StartGenerationRunRequest.class);
        assertTrue(method != null);

        Set<String> names = new HashSet<String>();
        for (Field field : StartGenerationRunRequest.class.getDeclaredFields()) {
            names.add(field.getName().toLowerCase(Locale.ROOT));
        }
        assertTrue(names.contains("sessionid"));
        assertTrue(names.contains("sceneid") || names.contains("scenecode"));
        assertTrue(names.contains("text"));
        for (String forbidden : FORBIDDEN) {
            assertFalse(names.contains(forbidden), "unexpected field: " + forbidden);
        }
    }
}
