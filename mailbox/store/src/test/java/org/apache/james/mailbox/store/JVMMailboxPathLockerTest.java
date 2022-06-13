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

package org.apache.james.mailbox.store;

import java.time.Duration;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.RepeatedTest;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class JVMMailboxPathLockerTest {
    JVMMailboxPathLocker testee = new JVMMailboxPathLocker();

    @RepeatedTest(20)
    void concurrentExecutionShouldNotBeDeadlockedByTheWriteLock() throws Exception {
        ConcurrentTestRunner.builder()
            .operation((a, b) -> Mono.from(testee.executeReactiveWithLockReactive(MailboxPath.inbox(Username.of("bob")),
                Mono.fromCallable(() -> {
                    Thread.sleep(5);
                    return null;
                }).subscribeOn(Schedulers.boundedElastic()), MailboxPathLocker.LockType.Write))
                .subscribeOn(Schedulers.boundedElastic()).block())
            .threadCount(100)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }

    @RepeatedTest(20)
    void concurrentExecutionShouldNotBeDeadlockedByTheReadLock() throws Exception {
        ConcurrentTestRunner.builder()
            .operation((a, b) -> Mono.from(testee.executeReactiveWithLockReactive(MailboxPath.inbox(Username.of("bob")),
                    Mono.fromCallable(() -> {
                        Thread.sleep(5);
                        return null;
                    }).subscribeOn(Schedulers.boundedElastic()), MailboxPathLocker.LockType.Read))
                .subscribeOn(Schedulers.boundedElastic()).block())
            .threadCount(100)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }

}
