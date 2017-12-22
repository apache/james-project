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

import org.apache.james.util.retry.api.RetrySchedule;
import org.junit.Test;

/**
 * <code>DoublingRetryScheduleTest</code>
 */
public class DoublingRetryScheduleTest {

    /**
     * Test method for .
     */
    @Test
    public final void testDoublingRetrySchedule() {
	assertTrue(RetrySchedule.class.isAssignableFrom(DoublingRetrySchedule.class));
	assertEquals(0, new DoublingRetrySchedule(0, 0).getInterval(0));
	assertEquals(0, new DoublingRetrySchedule(-1, -1).getInterval(0));
	assertEquals(0, new DoublingRetrySchedule(-1, 0).getInterval(0));
	assertEquals(0, new DoublingRetrySchedule(0, -1).getInterval(0));
    }

    /**
     * Test method for .
     */
    @Test
    public final void testGetInterval() {
	assertEquals(0, new DoublingRetrySchedule(0, 8).getInterval(0));
	assertEquals(1, new DoublingRetrySchedule(0, 8).getInterval(1));
	assertEquals(2, new DoublingRetrySchedule(0, 8).getInterval(2));
	assertEquals(4, new DoublingRetrySchedule(0, 8).getInterval(3));
	assertEquals(8, new DoublingRetrySchedule(0, 8).getInterval(4));
	assertEquals(8, new DoublingRetrySchedule(0, 8).getInterval(5));

	assertEquals(1, new DoublingRetrySchedule(1, 8).getInterval(0));
	assertEquals(2, new DoublingRetrySchedule(1, 8).getInterval(1));
	assertEquals(4, new DoublingRetrySchedule(1, 8).getInterval(2));
	assertEquals(8, new DoublingRetrySchedule(1, 8).getInterval(3));
	assertEquals(8, new DoublingRetrySchedule(1, 8).getInterval(4));

	assertEquals(3, new DoublingRetrySchedule(3, 12).getInterval(0));
	assertEquals(6, new DoublingRetrySchedule(3, 12).getInterval(1));
	assertEquals(12, new DoublingRetrySchedule(3, 12).getInterval(2));
	assertEquals(12, new DoublingRetrySchedule(3, 12).getInterval(3));

	assertEquals(0, new DoublingRetrySchedule(0, 8, 1000).getInterval(0));
	assertEquals(1000, new DoublingRetrySchedule(0, 8, 1000).getInterval(1));
	assertEquals(2000, new DoublingRetrySchedule(0, 8, 1000).getInterval(2));
	assertEquals(4000, new DoublingRetrySchedule(0, 8, 1000).getInterval(3));
	assertEquals(8000, new DoublingRetrySchedule(0, 8, 1000).getInterval(4));
	assertEquals(8000, new DoublingRetrySchedule(0, 8, 1000).getInterval(5));
    }

    /**
     * Test method for .
     */
    @Test
    public final void testToString() {
	assertEquals("DoublingRetrySchedule [startInterval=0, maxInterval=1, multiplier=1]", new DoublingRetrySchedule(0,
		1).toString());
    }
}
