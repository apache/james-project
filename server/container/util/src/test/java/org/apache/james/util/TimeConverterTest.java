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
package org.apache.james.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TimeConverterTest {

    private final long AMOUNT = 2;

    @Test
    public void testGetMilliSecondsMsec() {
	long time = 2;
	String unit = "msec";

	assertEquals(TimeConverter.getMilliSeconds(AMOUNT, unit), time);
	assertEquals(TimeConverter.getMilliSeconds(AMOUNT + " " + unit), time);
    }

    @Test
    public void testGetMilliSecondsMsecs() {
	long time = 2;
	String unit = "msecs";

	assertEquals(TimeConverter.getMilliSeconds(AMOUNT, unit), time);
	assertEquals(TimeConverter.getMilliSeconds(AMOUNT + " " + unit), time);
    }

    @Test
    public void testGetMilliSecondsSec() {
	long time = 2000;
	String unit = "sec";

	assertEquals(TimeConverter.getMilliSeconds(AMOUNT, unit), time);
	assertEquals(TimeConverter.getMilliSeconds(AMOUNT + " " + unit), time);
    }

    @Test
    public void testGetMilliSecondsSecs() {
	long time = 2000;
	String unit = "secs";

	assertEquals(TimeConverter.getMilliSeconds(AMOUNT, unit), time);
	assertEquals(TimeConverter.getMilliSeconds(AMOUNT + " " + unit), time);
    }

    @Test
    public void testGetMilliSecondsMinute() {
	long time = 120000;
	String unit = "minute";

	assertEquals(TimeConverter.getMilliSeconds(AMOUNT, unit), time);
	assertEquals(TimeConverter.getMilliSeconds(AMOUNT + " " + unit), time);
    }

    @Test
    public void testGetMilliSecondsMinutes() {
	long time = 120000;
	String unit = "minutes";

	assertEquals(TimeConverter.getMilliSeconds(AMOUNT, unit), time);
	assertEquals(TimeConverter.getMilliSeconds(AMOUNT + " " + unit), time);
    }

    @Test
    public void testGetMilliSecondsHour() {
	long time = 7200000;
	String unit = "hour";

	assertEquals(TimeConverter.getMilliSeconds(AMOUNT, unit), time);
	assertEquals(TimeConverter.getMilliSeconds(AMOUNT + " " + unit), time);
    }

    @Test
    public void testGetMilliSecondsHours() {
	long time = 7200000;
	String unit = "hours";

	assertEquals(TimeConverter.getMilliSeconds(AMOUNT, unit), time);
	assertEquals(TimeConverter.getMilliSeconds(AMOUNT + " " + unit), time);
    }

    @Test
    public void testGetMilliSecondsDay() {
	long time = 172800000;
	String unit = "day";

	assertEquals(TimeConverter.getMilliSeconds(AMOUNT, unit), time);
	assertEquals(TimeConverter.getMilliSeconds(AMOUNT + " " + unit), time);
    }

    @Test
    public void testGetMilliSecondsDays() {
	long time = 172800000;
	String unit = "days";

	assertEquals(TimeConverter.getMilliSeconds(AMOUNT, unit), time);
	assertEquals(TimeConverter.getMilliSeconds(AMOUNT + " " + unit), time);
    }

    @Test
    public void testIllegalUnit() {
	boolean exceptionThrown = false;
	try {
	    TimeConverter.getMilliSeconds(2, "week");
	    TimeConverter.getMilliSeconds(2 + " week");
	} catch (NumberFormatException e) {
	    exceptionThrown = true;
	}

	assertTrue(exceptionThrown);
    }

    @Test
    public void testIllegalPattern() {
	boolean exceptionThrown = false;
	try {
	    TimeConverter.getMilliSeconds("illegal pattern");
	} catch (NumberFormatException e) {
	    exceptionThrown = true;
	}

	assertTrue(exceptionThrown);
    }
}
