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
package org.apache.james.util.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import javax.naming.Context;

import org.apache.james.util.retry.api.ExceptionRetryingProxy;
import org.apache.james.util.retry.api.RetryHandler;
import org.apache.james.util.retry.api.RetrySchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExceptionRetryHandlerTest {
    private static class TestRetryingProxy implements ExceptionRetryingProxy {

        @Override
        public Context getDelegate() {
            return null;
        }

        @Override
        public Context newDelegate() {
            return null;
        }

        @Override
        public void resetDelegate() {
        }
    }

    private Class<?>[] exceptionClasses = null;
    private ExceptionRetryingProxy proxy = null;
    private RetrySchedule schedule = null;

    @BeforeEach
    void setUp() {
        exceptionClasses = new Class<?>[]{Exception.class};
        proxy = new TestRetryingProxy();
        schedule = i -> i;
    }

    @Test
    void testExceptionRetryHandler() {
        assertThat(RetryHandler.class.isAssignableFrom(new ExceptionRetryHandler(
            exceptionClasses, proxy, schedule, 0) {

            @Override
            public Object operation() {
                return null;
            }
        }.getClass())).isTrue();
    }

    @Test
    void testPerform() throws Exception {
        Object result = new ExceptionRetryHandler(
            exceptionClasses, proxy, schedule, 0) {

            @Override
            public Object operation() {
                return "Hi!";
            }
        }.perform();
        assertThat(result).isEqualTo("Hi!");

        try {
            new ExceptionRetryHandler(
                exceptionClasses, proxy, schedule, 0) {

                @Override
                public Object operation() throws Exception {
                    throw new Exception();
                }
            }.perform();
        } catch (Exception ex) {
            // no-op
        }
        assertThat(result).isEqualTo("Hi!");
    }

    @Test
    void testPostFailure() {
        final List<Exception> results = new ArrayList<>();
        RetryHandler handler = new ExceptionRetryHandler(
            exceptionClasses, proxy, schedule, 7) {

            @Override
            public void postFailure(Exception ex, int retryCount) {
                super.postFailure(ex, retryCount);
                results.add(ex);
            }

            @Override
            public Object operation() throws Exception {
                throw new Exception();
            }
        };
        try {
            handler.perform();
        } catch (Exception ex) {
            // no-op
        }
        assertThat(results.size()).isEqualTo(7);
    }

    @Test
    void testOperation() throws Exception {
        RetryHandler handler = new ExceptionRetryHandler(
            exceptionClasses, proxy, schedule, 0) {

            @Override
            public Object operation() {
                return "Hi!";
            }
        };
        assertThat(handler.operation()).isEqualTo("Hi!");
    }

    @Test
    void testGetRetryInterval() {
        ExceptionRetryHandler handler = new ExceptionRetryHandler(
            exceptionClasses, proxy, schedule, 0) {

            @Override
            public Object operation() {
                return null;
            }
        };
        assertThat(handler.getRetryInterval(8)).isEqualTo(8);
    }
}
