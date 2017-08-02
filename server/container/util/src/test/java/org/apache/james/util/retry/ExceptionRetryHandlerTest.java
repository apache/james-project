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

/**
 * <code>ExceptionRetryHandlerTest</code>
 */
public class ExceptionRetryHandlerTest {

    private Class<?>[] _exceptionClasses = null;
    private ExceptionRetryingProxy _proxy = null;
    private RetrySchedule _schedule = null;

    /**
     * @see junit.framework.TestCase#setUp()
     */
    @Before
    public void setUp() throws Exception {
	_exceptionClasses = new Class<?>[]{Exception.class};
	_proxy = new TestRetryingProxy();
	_schedule = new TestRetrySchedule();
    }

    private class TestRetryingProxy implements ExceptionRetryingProxy {

	/**
     */
	@Override
	public Context getDelegate() {
	    return null;
	}

	/**
     */
	@Override
	public Context newDelegate() throws Exception {
	    return null;
	}

	/**
     */
	@Override
	public void resetDelegate() throws Exception {
	}
    }

    private class TestRetrySchedule implements RetrySchedule {

	/**
     */
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
	assertTrue(RetryHandler.class.isAssignableFrom(new ExceptionRetryHandler(
		_exceptionClasses, _proxy, _schedule, 0) {

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
    public final void testPerform() throws Exception {
	Object result = new ExceptionRetryHandler(
		_exceptionClasses, _proxy, _schedule, 0) {

	    @Override
	    public Object operation() throws Exception {
		return "Hi!";
	    }
	}.perform();
	assertEquals("Hi!", result);

	try {
	    new ExceptionRetryHandler(
		    _exceptionClasses, _proxy, _schedule, 0) {

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

    /**
     * Test method for .
     */
    @Test
    public final void testPostFailure() {
	final List<Exception> results = new ArrayList<>();
	RetryHandler handler = new ExceptionRetryHandler(
		_exceptionClasses, _proxy, _schedule, 7) {

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

    /**
     * Test method for .
     * @throws Exception 
     */
    @Test
    public final void testOperation() throws Exception {
	RetryHandler handler = new ExceptionRetryHandler(
		_exceptionClasses, _proxy, _schedule, 0) {

	    @Override
	    public Object operation() throws Exception {
		return "Hi!";
	    }
	};
	assertEquals("Hi!", handler.operation());
    }

    /**
     * Test method for .
     */
    @Test
    public final void testGetRetryInterval() {
	ExceptionRetryHandler handler = new ExceptionRetryHandler(
		_exceptionClasses, _proxy, _schedule, 0) {

	    @Override
	    public Object operation() throws Exception {
		return null;
	    }
	};
	assertEquals(8, handler.getRetryInterval(8));
    }
}
