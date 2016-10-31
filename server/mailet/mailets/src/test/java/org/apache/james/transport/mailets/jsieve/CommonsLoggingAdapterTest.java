/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.transport.mailets.jsieve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;

public class CommonsLoggingAdapterTest {

    @Rule public ExpectedException expectedException = ExpectedException.none();
    private Logger logger;

    @Before
    public void setUp() {
        logger = mock(Logger.class);
    }

    @Test
    public void buildShouldThrowWhenNoMailetSpecified() {
        expectedException.expect(NullPointerException.class);

        CommonsLoggingAdapter.builder().build();
    }

    @Test
    public void buildShouldDefaultToLogLevelWarn() {
        CommonsLoggingAdapter loggingAdapter = CommonsLoggingAdapter.builder()
            .wrappedLogger(logger)
            .build();

        assertThat(loggingAdapter.isTraceEnabled()).isFalse();
        assertThat(loggingAdapter.isDebugEnabled()).isFalse();
        assertThat(loggingAdapter.isInfoEnabled()).isFalse();
        assertThat(loggingAdapter.isWarnEnabled()).isTrue();
        assertThat(loggingAdapter.isErrorEnabled()).isTrue();
        assertThat(loggingAdapter.isFatalEnabled()).isTrue();
    }


    @Test
    public void buildShouldUseFatalWithQuiet() {
        CommonsLoggingAdapter loggingAdapter = CommonsLoggingAdapter.builder()
            .wrappedLogger(logger)
            .quiet(true)
            .build();

        assertThat(loggingAdapter.isTraceEnabled()).isFalse();
        assertThat(loggingAdapter.isDebugEnabled()).isFalse();
        assertThat(loggingAdapter.isInfoEnabled()).isFalse();
        assertThat(loggingAdapter.isWarnEnabled()).isFalse();
        assertThat(loggingAdapter.isErrorEnabled()).isFalse();
        assertThat(loggingAdapter.isFatalEnabled()).isTrue();
    }

    @Test
    public void buildShouldUseTraceWithVerbose() {
        CommonsLoggingAdapter loggingAdapter = CommonsLoggingAdapter.builder()
            .wrappedLogger(logger)
            .verbose(true)
            .build();

        assertThat(loggingAdapter.isTraceEnabled()).isTrue();
        assertThat(loggingAdapter.isDebugEnabled()).isTrue();
        assertThat(loggingAdapter.isInfoEnabled()).isTrue();
        assertThat(loggingAdapter.isWarnEnabled()).isTrue();
        assertThat(loggingAdapter.isErrorEnabled()).isTrue();
        assertThat(loggingAdapter.isFatalEnabled()).isTrue();
    }

    @Test
    public void buildShouldThrowWhenBothQuietAndVerbose() {
        expectedException.expect(IllegalStateException.class);

        CommonsLoggingAdapter.builder()
            .wrappedLogger(logger)
            .verbose(true)
            .quiet(true)
            .build();
    }

    @Test
    public void simpleLoggingInVerboseModeShouldWorkInDebug() {
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .wrappedLogger(logger)
            .verbose(true)
            .build()
            .debug(message);

        verify(logger).debug(message);
    }

    @Test
    public void exceptionLoggingInVerboseModeShouldWorkInDebug() {
        Exception exception = new Exception();
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .wrappedLogger(logger)
            .verbose(true)
            .build()
            .debug(message, exception);

        verify(logger).debug(message, exception);
    }

    @Test
    public void simpleLoggingInInfoModeShouldNotWorkByDefault() {
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .wrappedLogger(logger)
            .build()
            .info(message);

        verifyNoMoreInteractions(logger);
    }

    @Test
    public void exceptionLoggingInInfoModeShouldNotWorkByDefault() {
        Exception exception = new Exception();
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .wrappedLogger(logger)
            .build()
            .info(message, exception);

        verifyNoMoreInteractions(logger);
    }

    @Test
    public void simpleLoggingInWarnModeShouldWorkByDefault() {
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .wrappedLogger(logger)
            .build()
            .warn(message);

        verify(logger).warn(message);
    }

    @Test
    public void exceptionLoggingInWarnModeShouldWorkByDefault() {
        Exception exception = new Exception();
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .wrappedLogger(logger)
            .build()
            .warn(message, exception);

        verify(logger).warn(message, exception);
    }

    @Test
    public void simpleLoggingInErrorModeShouldNotWorkWithQuiet() {
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .wrappedLogger(logger)
            .quiet(true)
            .build()
            .error(message);

        verifyNoMoreInteractions(logger);
    }

    @Test
    public void exceptionLoggingInErrorModeShouldNotWorkWithQuiet() {
        Exception exception = new Exception();
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .wrappedLogger(logger)
            .quiet(true)
            .build()
            .error(message, exception);

        verifyNoMoreInteractions(logger);
    }

    @Test
    public void simpleLoggingInFatalModeShouldWorkWithQuiet() {
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .wrappedLogger(logger)
            .quiet(true)
            .build()
            .fatal(message);

        verify(logger).error(message);
    }

    @Test
    public void exceptionLoggingInFatalModeShouldWorkWithQuiet() {
        Exception exception = new Exception();
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .wrappedLogger(logger)
            .quiet(true)
            .build()
            .fatal(message, exception);

        verify(logger).error(message, exception);
    }

    @Test
    public void logShouldHandleNullValue() {
        CommonsLoggingAdapter.builder()
            .wrappedLogger(logger)
            .build()
            .fatal(null);

        verify(logger).error("NULL");
    }

    @Test
    public void logShouldHandleNullValueWithException() {
        Exception exception = new Exception();
        CommonsLoggingAdapter.builder()
            .wrappedLogger(logger)
            .build()
            .fatal(null, exception);

        verify(logger).error("NULL", exception);
    }


    @Test
    public void logShouldHandleNullException() {
        CommonsLoggingAdapter.builder()
            .wrappedLogger(logger)
            .build()
            .fatal(null, null);

        verify(logger).error("NULL", (Throwable) null);
    }
}
