package com.xmut.ebus.domain.business.agent.repository;

import com.xmut.ebus.domain.business.agent.model.GenerationRun;

import java.util.Optional;

/**
 * GenerationRun 持久化端口（AgentRuntime 真相）。
 */
public interface GenerationRunRepository {

    void save(GenerationRun run);

    void update(GenerationRun run);

    Optional<GenerationRun> findById(String id);
}
