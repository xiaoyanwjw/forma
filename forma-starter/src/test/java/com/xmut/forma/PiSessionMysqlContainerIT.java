package com.xmut.forma;

import com.xmut.forma.pi.ai.message.Message;
import com.xmut.forma.pi.agent.session.Session;
import com.xmut.forma.pi.agent.session.SessionStore;
import com.xmut.forma.support.MysqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * True MySQL gate: SessionStore append + load on production DDL.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles({"test", "mysql-container"})
class PiSessionMysqlContainerIT {

    @Container
    static final MySQLContainer<?> MYSQL = MysqlContainerSupport.MYSQL;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        MysqlContainerSupport.registerDatasource(registry);
    }

    @Autowired
    private SessionStore sessionStore;

    @Test
    void appendThenLoadRoundTripsMessage() {
        String sessionId = "s-tc-" + UUID.randomUUID();
        sessionStore.getOrCreate(Session.Meta.builder().sessionId(sessionId).build());
        sessionStore.append(sessionId, "run-tc-1",
                Collections.singletonList(Message.user("hello-mysql")));

        List<Message> loaded = sessionStore.load(sessionId);
        assertThat(loaded).extracting(Message::getContent).containsExactly("hello-mysql");
    }
}
