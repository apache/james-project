/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.nurkiewicz.asyncretry.AsyncRetryExecutor;
import com.nurkiewicz.asyncretry.RetryExecutor;

public class RetryExecutorUtilTest {
    private static final int MAX_RETRIES = 3;
    private static final int MIN_DELAY = 100;
    @Mock
    protected FaultyService serviceMock;

    private RetryExecutor retryExecutor;
    private ScheduledExecutorService scheduledExecutor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    @After
    public void tearDown() throws Exception {
        scheduledExecutor.shutdownNow();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void retryOnExceptionsAndExecuteShouldRethrowWhenScheduledServiceAlwaysThrowException() throws Exception {
        given(serviceMock.faultyService())
                .willThrow(IllegalArgumentException.class)
                .willThrow(IllegalArgumentException.class)
                .willThrow(IllegalArgumentException.class)
                .willThrow(IllegalArgumentException.class);

        retryExecutor = RetryExecutorUtil.retryOnExceptions(new AsyncRetryExecutor(scheduledExecutor), MAX_RETRIES, MIN_DELAY, IllegalArgumentException.class);

        assertThatThrownBy(() -> retryExecutor.getWithRetry(serviceMock::faultyService).get())
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void retryOnExceptionsAndExecuteShouldRetryWhenMatchExceptionAndSuccess() throws Exception {
        given(serviceMock.faultyService())
                .willThrow(IllegalArgumentException.class)
                .willReturn("Foo");
        retryExecutor = RetryExecutorUtil.retryOnExceptions(new AsyncRetryExecutor(scheduledExecutor), MAX_RETRIES, MIN_DELAY, IllegalArgumentException.class);

        final CompletableFuture<String> future = retryExecutor.getWithRetry(serviceMock::faultyService);

        assertThat(future.get()).isEqualTo("Foo");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void retryOnExceptionsAndExecuteShouldNotRetryWhenDoesNotMatchException() throws Exception {
        given(serviceMock.faultyService())
                .willThrow(IllegalStateException.class)
                .willReturn("Foo");

        retryExecutor = RetryExecutorUtil.retryOnExceptions(new AsyncRetryExecutor(scheduledExecutor), MAX_RETRIES, MIN_DELAY, IllegalArgumentException.class);

        assertThatThrownBy(() -> retryExecutor.getWithRetry(serviceMock::faultyService).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void retryOnExceptionsAndExecuteShouldRetryWithMaxTimesAndReturnValue() throws Exception {
        given(serviceMock.faultyService())
                .willThrow(IllegalStateException.class, IllegalStateException.class, IllegalStateException.class)
                .willReturn("Foo");

        retryExecutor = RetryExecutorUtil.retryOnExceptions(new AsyncRetryExecutor(scheduledExecutor), MAX_RETRIES, MIN_DELAY, IllegalStateException.class);

        CompletableFuture<String> future = retryExecutor.getWithRetry(serviceMock::faultyService);

        assertThat(future.get()).isEqualTo("Foo");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void retryOnExceptionsAndExecuteShouldFailIfFailMoreThanMaxRetry() throws Exception {
        given(serviceMock.faultyService())
            .willThrow(IllegalStateException.class, IllegalStateException.class, IllegalStateException.class, IllegalStateException.class)
            .willReturn("Foo");

        retryExecutor = RetryExecutorUtil.retryOnExceptions(new AsyncRetryExecutor(scheduledExecutor), MAX_RETRIES, MIN_DELAY, IllegalStateException.class);

        assertThatThrownBy(() -> retryExecutor.getWithRetry(serviceMock::faultyService).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}