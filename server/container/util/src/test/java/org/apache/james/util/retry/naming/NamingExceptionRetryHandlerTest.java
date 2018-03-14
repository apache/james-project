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
package org.apache.james.util.retry.naming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.naming.Context;
import javax.naming.NamingException;

import org.apache.james.util.retry.api.ExceptionRetryingProxy;
import org.apache.james.util.retry.api.RetryHandler;
import org.apache.james.util.retry.api.RetrySchedule;
import org.junit.Before;
import org.junit.Test;

/**
 * <code>ExceptionRetryHandlerTest</code>
 */
public class NamingExceptionRetryHandlerTest {

    private Class<?>[] exceptionClasses = null;
    private ExceptionRetryingProxy proxy = null;
    private RetrySchedule schedule = null;

    /**
     * @see junit.framework.TestCase#setUp()
     */
    @Before
    public void setUp() throws Exception {
    exceptionClasses = new Class<?>[]{NamingException.class};
    proxy = new TestRetryingProxy();
    schedule = new TestRetrySchedule();
    }

    private class TestRetryingProxy implements ExceptionRetryingProxy {

    @Override
    public Context getDelegate() {
        return null;
    }

    @Override
    public Context newDelegate() throws NamingException {
        return null;
    }

    @Override
    public void resetDelegate() throws NamingException {
    }
    }

    private class TestRetrySchedule implements RetrySchedule {

    @Override
    public long getInterval(int index) {
        return index;
    }
    }

    /**
     * Test method for .
     */
    @Test
    public final void testExceptionRetryHandler() {
    assertTrue(RetryHandler.class.isAssignableFrom(new NamingExceptionRetryHandler(
        exceptionClasses, proxy, schedule, 0) {

        @Override
        public Object operation() throws Exception {
        return null;
        }
    }.getClass()));
    }

    /**
     * Test method for .
     * @throws Exception 
     */
    @Test
    public final void testPerform() throws NamingException {
    Object result = new NamingExceptionRetryHandler(
        exceptionClasses, proxy, schedule, 0) {

        @Override
        public Object operation() throws NamingException {
        return "Hi!";
        }
    }.perform();
    assertEquals("Hi!", result);

    try {
        new NamingExceptionRetryHandler(
            exceptionClasses, proxy, schedule, 0) {

        @Override
        public Object operation() throws Exception {
            throw new NamingException();
        }
        }.perform();
    } catch (NamingException ex) {
        // no-op
    }
    assertEquals("Hi!", result);
    }
}
