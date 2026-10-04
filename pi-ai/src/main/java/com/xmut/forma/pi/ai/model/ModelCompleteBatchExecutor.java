package com.xmut.forma.pi.ai.model;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link ModelProvider#completeBatch} 共享线程池；守护线程，进程退出即可结束。
 */
final class ModelCompleteBatchExecutor {

    static final ExecutorService POOL = Executors.newFixedThreadPool(
            ModelProvider.DEFAULT_BATCH_PARALLELISM,
            new ThreadFactory() {
                private final AtomicInteger seq = new AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "pi-ai-completeBatch-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            });

    private ModelCompleteBatchExecutor() {
    }
}
