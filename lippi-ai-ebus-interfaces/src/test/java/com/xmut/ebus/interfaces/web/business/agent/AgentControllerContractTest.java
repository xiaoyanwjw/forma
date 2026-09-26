package com.xmut.ebus.interfaces.web.business.agent;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁 AD-16：计费空跑 API 不得接受系统提示词 / tool 定义正文参数。
 */
class AgentControllerContractTest {

    private static final Set<String> FORBIDDEN = new HashSet<String>(Arrays.asList(
            "systemprompt", "system_prompt", "system", "prompt", "tools", "tooldefs", "tool_definitions"));

    @Test
    void startEmptyRunDoesNotAcceptSystemPromptOrToolBodyParams() throws Exception {
        Method method = AgentController.class.getDeclaredMethod(
                "startEmptyRun", String.class, String.class, String.class);
        Set<String> names = new HashSet<String>();
        for (Parameter p : method.getParameters()) {
            RequestParam rp = p.getAnnotation(RequestParam.class);
            if (rp != null && rp.value() != null && !rp.value().isEmpty()) {
                names.add(rp.value().toLowerCase(Locale.ROOT));
            } else {
                names.add(p.getName().toLowerCase(Locale.ROOT));
            }
        }
        assertTrue(names.contains("sessionid"));
        assertTrue(names.contains("sceneid"));
        assertTrue(names.contains("scenecode"));
        for (String forbidden : FORBIDDEN) {
            assertFalse(names.contains(forbidden), "unexpected param: " + forbidden);
        }
    }
}
