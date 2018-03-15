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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.naming.Context;

import org.apache.james.util.retry.api.ExceptionRetryingProxy;
import org.apache.james.util.retry.api.RetryHandler;
import org.apache.james.util.retry.api.RetrySchedule;
import org.junit.Before;
import org.junit.Test;

public class ExceptionRetryHandlerTest {
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

    @Before
    public void setUp() throws Exception {
        exceptionClasses = new Class<?>[]{Exception.class};
        proxy = new TestRetryingProxy();
        schedule = i -> i;
    }

    @Test
    public final void testExceptionRetryHandler() {
        assertTrue(RetryHandler.class.isAssignableFrom(new ExceptionRetryHandler(
            exceptionClasses, proxy, schedule, 0) {

            @Override
            public Object operation() {
                return null;
            }
        }.getClass()));
    }

    @Test
    public final void testPerform() throws Exception {
        Object result = new ExceptionRetryHandler(
            exceptionClasses, proxy, schedule, 0) {

            @Override
            public Object operation() {
                return "Hi!";
            }
        }.perform();
        assertEquals("Hi!", result);

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
        assertEquals("Hi!", result);
    }

    @Test
    public final void testPostFailure() {
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
        assertEquals(7, results.size());
    }

    @Test
    public final void testOperation() throws Exception {
        RetryHandler handler = new ExceptionRetryHandler(
            exceptionClasses, proxy, schedule, 0) {

            @Override
            public Object operation() {
                return "Hi!";
            }
        };
        assertEquals("Hi!", handler.operation());
    }

    @Test
    public final void testGetRetryInterval() {
        ExceptionRetryHandler handler = new ExceptionRetryHandler(
            exceptionClasses, proxy, schedule, 0) {

            @Override
            public Object operation() {
                return null;
            }
        };
        assertEquals(8, handler.getRetryInterval(8));
    }
}
