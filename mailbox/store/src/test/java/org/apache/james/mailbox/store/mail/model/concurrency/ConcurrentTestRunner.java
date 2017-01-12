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

package org.apache.james.mailbox.store.mail.model.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ConcurrentTestRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentTestRunner.class);

    public class ConcurrentRunnableTask implements Runnable {
        private final int threadNumber;

        public ConcurrentRunnableTask(int threadNumber) {
            this.threadNumber = threadNumber;
        }

        @Override
        public void run() {
            countDownLatch.countDown();
            for (int i = 0; i < operationCount; i++) {
                try {
                    performOperation(threadNumber, i);
                } catch (Exception e) {
                    LOGGER.error("Error caught during concurrent testing", e);
                }
            }
        }
    }

    private final int threadCount;
    private final int operationCount;
    private final CountDownLatch countDownLatch;

    public ConcurrentTestRunner(int threadCount, int operationCount) {
        this.threadCount = threadCount;
        this.operationCount = operationCount;
        this.countDownLatch = new CountDownLatch(threadCount);
    }

    public void run() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(new ConcurrentRunnableTask(i));
        }
        executorService.shutdown();
        assertThat(executorService.awaitTermination(1, TimeUnit.MINUTES))
            .isTrue();
    }

    protected abstract void performOperation(int threadNumber, int step) throws Exception;
}
