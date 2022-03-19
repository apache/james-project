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

import static org.apache.james.mailbox.store.PreDeletionHooks.PRE_DELETION_HOOK_METRIC_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.MetadataWithMailboxId;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

class PreDeletionHooksTest {

    private static final TestId MAILBOX_ID = TestId.of(45);
    private static final ModSeq MOD_SEQ = ModSeq.of(18);
    private static final int SIZE = 12;
    private static final MessageMetaData MESSAGE_META_DATA = new MessageMetaData(MessageUid.of(1), MOD_SEQ, new Flags(), SIZE, new Date(), Optional.empty(), TestMessageId.of(42), ThreadId.fromBaseMessageId(TestMessageId.of(42)));
    private static final PreDeletionHook.DeleteOperation DELETE_OPERATION = PreDeletionHook.DeleteOperation.from(ImmutableList.of(MetadataWithMailboxId.from(
        MESSAGE_META_DATA,
        MAILBOX_ID)));
    private PreDeletionHook hook1;
    private PreDeletionHook hook2;
    private PreDeletionHooks testee;
    private RecordingMetricFactory metricFactory;

    @BeforeEach
    void setUp() {
        hook1 = mock(PreDeletionHook.class);
        hook2 = mock(PreDeletionHook.class);

        metricFactory = new RecordingMetricFactory();

        testee = new PreDeletionHooks(ImmutableSet.of(hook1, hook2), metricFactory);
    }

    @Test
    void runHooksShouldCallAllHooks() {
        when(hook1.notifyDelete(any())).thenReturn(Mono.empty());
        when(hook2.notifyDelete(any())).thenReturn(Mono.empty());

        testee.runHooks(DELETE_OPERATION).block();

        verify(hook1).notifyDelete(DELETE_OPERATION);
        verify(hook2).notifyDelete(DELETE_OPERATION);
        verifyNoMoreInteractions(hook1);
        verifyNoMoreInteractions(hook2);
    }

    @Test
    void runHooksShouldThrowWhenOneHookThrows() {
        when(hook1.notifyDelete(any())).thenThrow(new RuntimeException());
        when(hook2.notifyDelete(any())).thenReturn(Mono.empty());

        assertThatThrownBy(() -> testee.runHooks(DELETE_OPERATION).block()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void runHooksShouldNotRunHooksAfterAHookThrows() {
        when(hook1.notifyDelete(any())).thenThrow(new RuntimeException());
        when(hook2.notifyDelete(any())).thenReturn(Mono.empty());

        try {
            testee.runHooks(DELETE_OPERATION).block();
        } catch (Exception e) {
            // ignored
        }

        verifyNoMoreInteractions(hook2);
    }

    @Test
    void runHooksShouldThrowWhenOneHookReturnsErrorMono() {
        when(hook1.notifyDelete(any())).thenReturn(Mono.error(new RuntimeException()));
        when(hook2.notifyDelete(any())).thenReturn(Mono.empty());

        assertThatThrownBy(() -> testee.runHooks(DELETE_OPERATION).block()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void runHooksShouldNotRunHooksAfterAHookReturnsErrorMono() {
        when(hook1.notifyDelete(any())).thenReturn(Mono.error(new RuntimeException()));
        when(hook2.notifyDelete(any())).thenReturn(Mono.empty());

        try {
            testee.runHooks(DELETE_OPERATION).block();
        } catch (Exception e) {
            // ignored
        }

        verifyNoMoreInteractions(hook2);
    }

    @Test
    void runHooksShouldExecuteHooksSequentially() {
        ReentrantLock reentrantLock = new ReentrantLock();

        Answer<Publisher<Void>> lockAndSleepAnswer = invocationOnMock -> {
            reentrantLock.lock();
            Thread.sleep(Duration.ofMillis(100).toMillis());
            reentrantLock.unlock();
            return Mono.empty();
        };
        Answer<Publisher<Void>> throwIfLockedAnswer = invocationOnMock -> {
            if (reentrantLock.isLocked()) {
                throw new RuntimeException("This task is running while the previous one is waiting");
            }
            return Mono.empty();
        };

        when(hook1.notifyDelete(any())).thenAnswer(lockAndSleepAnswer);
        when(hook2.notifyDelete(any())).thenAnswer(throwIfLockedAnswer);

        assertThatCode(() -> testee.runHooks(DELETE_OPERATION).block())
            .describedAs("RunHook does not throw if hooks are executed in a sequential manner")
            .doesNotThrowAnyException();
    }

    @Test
    void runHooksShouldPublishTimerMetrics() {
        Duration sleepDuration = Duration.ofSeconds(1);

        Mono<Void> notifyDeleteAnswer = Mono.fromCallable(() -> {
            Thread.sleep(sleepDuration.toMillis());
            return Mono.empty();
        }).then();

        when(hook1.notifyDelete(any())).thenReturn(notifyDeleteAnswer);
        when(hook2.notifyDelete(any())).thenReturn(notifyDeleteAnswer);

        testee.runHooks(DELETE_OPERATION).block();

        assertThat(metricFactory.executionTimesFor(PRE_DELETION_HOOK_METRIC_NAME))
            .hasSize(2)
            .allSatisfy(duration -> assertThat(duration).isGreaterThanOrEqualTo(sleepDuration));
    }
}