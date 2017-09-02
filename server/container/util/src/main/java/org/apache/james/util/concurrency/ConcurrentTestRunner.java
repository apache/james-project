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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

public class ConcurrentTestRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentTestRunner.class);

    public interface BiConsumer {
        void consume(int threadNumber, int step) throws Exception;
    }

    private class ConcurrentRunnableTask implements Runnable {
        private final int threadNumber;
        private final BiConsumer biConsumer;
        private Exception exception;

        public ConcurrentRunnableTask(int threadNumber, BiConsumer biConsumer) {
            this.threadNumber = threadNumber;
            this.biConsumer = biConsumer;
        }

        @Override
        public void run() {
            exception = null;
            countDownLatch.countDown();
            for (int i = 0; i < operationCount; i++) {
                try {
                    biConsumer.consume(threadNumber, i);
                } catch (Exception e) {
                    LOGGER.error("Error caught during concurrent testing", e);
                    exception = e;
                }
            }
            if (exception != null) {
                throw Throwables.propagate(exception);
            }
        }
    }

    private final int threadCount;
    private final int operationCount;
    private final CountDownLatch countDownLatch;
    private final BiConsumer biConsumer;
    private final ExecutorService executorService;
    private final List<Future<?>> futures;

    public ConcurrentTestRunner(int threadCount, int operationCount, BiConsumer biConsumer) {
        Preconditions.checkArgument(threadCount > 0, "Thread count should be strictly positive");
        Preconditions.checkArgument(operationCount > 0, "Operation count should be strictly positive");
        Preconditions.checkNotNull(biConsumer);
        this.threadCount = threadCount;
        this.operationCount = operationCount;
        this.countDownLatch = new CountDownLatch(threadCount);
        this.biConsumer = biConsumer;
        this.executorService = Executors.newFixedThreadPool(threadCount);
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

    public boolean awaitTermination(long time, TimeUnit unit) throws InterruptedException {
        executorService.shutdown();
        return executorService.awaitTermination(time, unit);
    }
}
