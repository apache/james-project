/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.util.concurrency;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.james.util.concurrent.NamedThreadFactory;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class ConcurrentTestRunner implements Closeable {

    public static final int DEFAULT_OPERATION_COUNT = 1;

    @FunctionalInterface
    public interface RequireOperation {
        RequireThreadCount operation(ConcurrentOperation operation);

        default RequireThreadCount reactorOperation(ReactorOperation reactorOperation) {
            return operation(reactorOperation.blocking());
        }
    }

    @FunctionalInterface
    public interface RequireThreadCount {
        Builder threadCount(int threadCount);
    }

    public static class Builder {
        private final int threadCount;
        private final ConcurrentOperation operation;
        private Optional<Integer> operationCount;

        private Builder(int threadCount, ConcurrentOperation operation) {
            Preconditions.checkArgument(threadCount > 0, "Thread count should be strictly positive");
            Preconditions.checkNotNull(operation);

            this.threadCount = threadCount;
            this.operation = operation;
            this.operationCount = Optional.empty();
        }

        public Builder operationCount(int operationCount) {
            Preconditions.checkArgument(operationCount > 0, "Operation count should be strictly positive");
            this.operationCount = Optional.of(operationCount);
            return this;
        }

        private ConcurrentTestRunner build() {
            return new ConcurrentTestRunner(
                threadCount,
                operationCount.orElse(DEFAULT_OPERATION_COUNT),
                operation);
        }

        public ConcurrentTestRunner run() {
            ConcurrentTestRunner testRunner = build();
            testRunner.run();
            return testRunner;
        }

        public ConcurrentTestRunner runSuccessfullyWithin(Duration duration) throws InterruptedException, ExecutionException {
            return build()
                .runSuccessfullyWithin(duration);
        }

        public ConcurrentTestRunner runAcceptingErrorsWithin(Duration duration) throws InterruptedException, ExecutionException {
            return build()
                .runAcceptingErrorsWithin(duration);
        }
    }

    @FunctionalInterface
    public interface ConcurrentOperation {
        void execute(int threadNumber, int step) throws Exception;
    }

    @FunctionalInterface
    public interface ReactorOperation {
        Publisher<Void> execute(int threadNumber, int step) throws Exception;

        default ConcurrentOperation blocking() {
            return (threadNumber, step) -> Mono.from(execute(threadNumber, step))
                .then()
                .block();
        }
    }

    private class ConcurrentRunnableTask implements Runnable {
        private final int threadNumber;
        private final ConcurrentOperation concurrentOperation;
        private Exception exception;

        public ConcurrentRunnableTask(int threadNumber, ConcurrentOperation concurrentOperation) {
            this.threadNumber = threadNumber;
            this.concurrentOperation = concurrentOperation;
        }

        @Override
        public void run() {
            exception = null;
            countDownLatch.countDown();
            for (int i = 0; i < operationCount; i++) {
                try {
                    concurrentOperation.execute(threadNumber, i);
                } catch (Exception e) {
                    LOGGER.error("Error caught during concurrent testing (iteration {}, threadNumber {})", i, threadNumber, e);
                    exception = e;
                }
            }
            if (exception != null) {
                throw new RuntimeException(exception);
            }
        }
    }

    public static class NotTerminatedException extends RuntimeException {

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentTestRunner.class);

    public static RequireOperation builder() {
        return operation -> threadCount -> new Builder(threadCount, operation);
    }

    private final int threadCount;
    private final int operationCount;
    private final CountDownLatch countDownLatch;
    private final ConcurrentOperation biConsumer;
    private final ExecutorService executorService;
    private final List<Future<?>> futures;

    private ConcurrentTestRunner(int threadCount, int operationCount, ConcurrentOperation biConsumer) {
        this.threadCount = threadCount;
        this.operationCount = operationCount;
        this.countDownLatch = new CountDownLatch(threadCount);
        this.biConsumer = biConsumer;
        ThreadFactory threadFactory = NamedThreadFactory.withClassName(getClass());
        this.executorService = Executors.newFixedThreadPool(threadCount, threadFactory);
        this.futures = new ArrayList<>();
    }

    public ConcurrentTestRunner run() {
        for (int i = 0; i < threadCount; i++) {
            futures.add(executorService.submit(new ConcurrentRunnableTask(i, biConsumer)));
        }
        return this;
    }

    public ConcurrentTestRunner assertNoException() throws ExecutionException, InterruptedException {
        for (Future<?> future: futures) {
            future.get();
        }
        return this;
    }

    public ConcurrentTestRunner awaitTermination(Duration duration) throws InterruptedException {
        executorService.shutdown();
        boolean terminated = executorService.awaitTermination(duration.toMillis(), TimeUnit.MILLISECONDS);
        if (!terminated) {
            throw new NotTerminatedException();
        }
        return this;
    }

    public ConcurrentTestRunner runSuccessfullyWithin(Duration duration) throws InterruptedException, ExecutionException {
        return run()
            .awaitTermination(duration)
            .assertNoException();
    }

    public ConcurrentTestRunner runAcceptingErrorsWithin(Duration duration) throws InterruptedException, ExecutionException {
        return run()
            .awaitTermination(duration);
    }


    @Override
    public void close() throws IOException {
        executorService.shutdownNow();
    }
}
