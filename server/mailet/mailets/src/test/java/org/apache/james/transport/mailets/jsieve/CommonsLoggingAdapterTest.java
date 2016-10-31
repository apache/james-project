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

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;

public class CommonsLoggingAdapterTest {

    @Rule public ExpectedException expectedException = ExpectedException.none();
    private GenericMailet genericMailet;

    @Before
    public void setUp() {
        genericMailet = mock(GenericMailet.class);
    }

    @Test
    public void buildShouldThrowWhenNoMailetSpecified() {
        expectedException.expect(NullPointerException.class);

        CommonsLoggingAdapter.builder().build();
    }

    @Test
    public void buildShouldDefaultToLogLevelWarn() {
        CommonsLoggingAdapter loggingAdapter = CommonsLoggingAdapter.builder()
            .mailet(genericMailet)
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
            .mailet(genericMailet)
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
            .mailet(genericMailet)
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
            .mailet(genericMailet)
            .verbose(true)
            .quiet(true)
            .build();
    }

    @Test
    public void simpleLoggingInVerboseModeShouldWorkInDebug() {
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .mailet(genericMailet)
            .verbose(true)
            .build()
            .debug(message);

        verify(genericMailet).log(message);
    }

    @Test
    public void exceptionLoggingInVerboseModeShouldWorkInDebug() {
        Exception exception = new Exception();
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .mailet(genericMailet)
            .verbose(true)
            .build()
            .debug(message, exception);

        verify(genericMailet).log(message, exception);
    }

    @Test
    public void simpleLoggingInInfoModeShouldNotWorkByDefault() {
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .mailet(genericMailet)
            .build()
            .info(message);

        verifyNoMoreInteractions(genericMailet);
    }

    @Test
    public void exceptionLoggingInInfoModeShouldNotWorkByDefault() {
        Exception exception = new Exception();
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .mailet(genericMailet)
            .build()
            .info(message, exception);

        verifyNoMoreInteractions(genericMailet);
    }

    @Test
    public void simpleLoggingInWarnModeShouldWorkByDefault() {
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .mailet(genericMailet)
            .build()
            .warn(message);

        verify(genericMailet).log(message);
    }

    @Test
    public void exceptionLoggingInWarnModeShouldWorkByDefault() {
        Exception exception = new Exception();
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .mailet(genericMailet)
            .build()
            .warn(message, exception);

        verify(genericMailet).log(message, exception);
    }

    @Test
    public void simpleLoggingInErrorModeShouldNotWorkWithQuiet() {
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .mailet(genericMailet)
            .quiet(true)
            .build()
            .error(message);

        verifyNoMoreInteractions(genericMailet);
    }

    @Test
    public void exceptionLoggingInErrorModeShouldNotWorkWithQuiet() {
        Exception exception = new Exception();
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .mailet(genericMailet)
            .quiet(true)
            .build()
            .error(message, exception);

        verifyNoMoreInteractions(genericMailet);
    }

    @Test
    public void simpleLoggingInFatalModeShouldWorkWithQuiet() {
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .mailet(genericMailet)
            .quiet(true)
            .build()
            .fatal(message);

        verify(genericMailet).log(message);
    }

    @Test
    public void exceptionLoggingInFatalModeShouldWorkWithQuiet() {
        Exception exception = new Exception();
        String message = "Message";
        CommonsLoggingAdapter.builder()
            .mailet(genericMailet)
            .quiet(true)
            .build()
            .fatal(message, exception);

        verify(genericMailet).log(message, exception);
    }

    @Ignore("Mailet logging choose log level based on arguments")
    @Test
    public void logIsUsingWrongLogLevelReported() throws Exception {
        GenericMailet genericMailet = new GenericMailet() {
            @Override
            public void service(Mail mail) throws MessagingException {

            }
        };
        Logger logger = mock(Logger.class);
        genericMailet.init(new FakeMailetConfig("name", FakeMailContext.builder()
            .logger(logger)
            .build()));

        String message = "Fatal";
        CommonsLoggingAdapter.builder().mailet(genericMailet).build().error(message);

        verify(logger).info(message);
    }

    @Test
    public void logShouldHandleNullValue() {
        CommonsLoggingAdapter.builder()
            .mailet(genericMailet)
            .build()
            .fatal(null);

        verify(genericMailet).log("NULL");
    }

    @Test
    public void logShouldHandleNullValueWithException() {
        Exception exception = new Exception();
        CommonsLoggingAdapter.builder()
            .mailet(genericMailet)
            .build()
            .fatal(null, exception);

        verify(genericMailet).log("NULL", exception);
    }


    @Test
    public void logShouldHandleNullException() {
        CommonsLoggingAdapter.builder()
            .mailet(genericMailet)
            .build()
            .fatal(null, null);

        verify(genericMailet).log("NULL", null);
    }
}
