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

package org.apache.james.protocols.imap.utils;

import java.util.Locale;
import static org.junit.Assert.*;

import org.apache.james.protocols.imap.utils.DecoderUtils;
import org.junit.After;
import org.junit.Test;

public class DecoderUtilsLocaleDateTest  {

    private static final Locale BASE_DEFAULT_LOCALE = Locale.getDefault();


    @After
    public void tearDown() throws Exception {
        Locale.setDefault(BASE_DEFAULT_LOCALE);
    }

    @Test
    public void testUS() throws Exception {
        runTests(Locale.US);
    }

    @Test
    public void testITALY() throws Exception {
        runTests(Locale.ITALY);
    }

    @Test
    public void testTAIWAN() throws Exception {
        runTests(Locale.TAIWAN);
    }

    @Test
    public void testCHINA() throws Exception {
        runTests(Locale.CHINA);
    }

    @Test
    public void testKOREA() throws Exception {
        runTests(Locale.KOREA);
    }

    @Test
    public void testJAPAN() throws Exception {
        runTests(Locale.JAPAN);
    }

    @Test
    public void testUK() throws Exception {
        runTests(Locale.UK);
    }

    @Test
    public void testCANADA() throws Exception {
        runTests(Locale.CANADA);
    }

    @Test
    public void testGERMANY() throws Exception {
        runTests(Locale.GERMANY);
    }

    @Test
    public void testFRANCH() throws Exception {
        runTests(Locale.FRANCE);
    }

    
    private void runTests(Locale locale) throws Exception {
        Locale.setDefault(locale);
        decodeDateTime();
    }

    @SuppressWarnings("deprecation")
	private void decodeDateTime() throws Exception {
        assertEquals("21 Oct 1972 20:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0000").toGMTString());
        assertEquals("21 Oct 1972 19:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0100").toGMTString());
        assertEquals("21 Oct 1972 18:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0200").toGMTString());
        assertEquals("21 Oct 1972 17:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0300").toGMTString());
        assertEquals("21 Oct 1972 16:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0400").toGMTString());
        assertEquals("21 Oct 1972 15:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0500").toGMTString());
        assertEquals("21 Oct 1972 14:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0600").toGMTString());
        assertEquals("21 Oct 1972 13:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0700").toGMTString());
        assertEquals("21 Oct 1972 12:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0800").toGMTString());
        assertEquals("21 Oct 1972 11:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0900").toGMTString());
        assertEquals("21 Oct 1972 10:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +1000").toGMTString());
        assertEquals("21 Oct 1972 09:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +1100").toGMTString());
        assertEquals("21 Oct 1972 08:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +1200").toGMTString());

        assertEquals("21 Oct 1972 10:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +1000").toGMTString());
        assertEquals("21 Oct 1972 21:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0100").toGMTString());
        assertEquals("21 Oct 1972 22:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0200").toGMTString());
        assertEquals("21 Oct 1972 23:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0300").toGMTString());
        assertEquals("22 Oct 1972 00:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0400").toGMTString());
        assertEquals("22 Oct 1972 01:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0500").toGMTString());
        assertEquals("22 Oct 1972 02:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0600").toGMTString());
        assertEquals("22 Oct 1972 03:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0700").toGMTString());
        assertEquals("22 Oct 1972 04:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0800").toGMTString());
        assertEquals("22 Oct 1972 05:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0900").toGMTString());
        assertEquals("22 Oct 1972 06:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -1000").toGMTString());

        assertEquals("21 Oct 1972 19:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0030").toGMTString());
        assertEquals("21 Oct 1972 18:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0130").toGMTString());
        assertEquals("21 Oct 1972 17:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0230").toGMTString());
        assertEquals("21 Oct 1972 16:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0330").toGMTString());
        assertEquals("21 Oct 1972 15:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0430").toGMTString());
        assertEquals("21 Oct 1972 14:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0530").toGMTString());
        assertEquals("21 Oct 1972 13:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0630").toGMTString());
        assertEquals("21 Oct 1972 12:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0730").toGMTString());
        assertEquals("21 Oct 1972 11:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0830").toGMTString());
        assertEquals("21 Oct 1972 10:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +0930").toGMTString());
        assertEquals("21 Oct 1972 09:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +1030").toGMTString());
        assertEquals("21 Oct 1972 08:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +1130").toGMTString());
        assertEquals("21 Oct 1972 07:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 +1230").toGMTString());

        assertEquals("21 Oct 1972 20:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0030").toGMTString());
        assertEquals("21 Oct 1972 21:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0130").toGMTString());
        assertEquals("21 Oct 1972 22:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0230").toGMTString());
        assertEquals("21 Oct 1972 23:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0330").toGMTString());
        assertEquals("22 Oct 1972 00:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0430").toGMTString());
        assertEquals("22 Oct 1972 01:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0530").toGMTString());
        assertEquals("22 Oct 1972 02:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0630").toGMTString());
        assertEquals("22 Oct 1972 03:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0730").toGMTString());
        assertEquals("22 Oct 1972 04:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0830").toGMTString());
        assertEquals("22 Oct 1972 05:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0930").toGMTString());
        assertEquals("22 Oct 1972 06:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -1030").toGMTString());

        assertEquals("21 Oct 1972 20:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0000").toGMTString());
        assertEquals("21 Oct 1972 19:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0100").toGMTString());
        assertEquals("21 Oct 1972 18:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0200").toGMTString());
        assertEquals("21 Oct 1972 17:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0300").toGMTString());
        assertEquals("21 Oct 1972 16:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0400").toGMTString());
        assertEquals("21 Oct 1972 15:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0500").toGMTString());
        assertEquals("21 Oct 1972 14:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0600").toGMTString());
        assertEquals("21 Oct 1972 13:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0700").toGMTString());
        assertEquals("21 Oct 1972 12:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0800").toGMTString());
        assertEquals("21 Oct 1972 11:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0900").toGMTString());
        assertEquals("21 Oct 1972 10:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +1000").toGMTString());
        assertEquals("21 Oct 1972 09:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +1100").toGMTString());
        assertEquals("21 Oct 1972 08:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +1200").toGMTString());

        assertEquals("21 Oct 1972 20:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0000").toGMTString());
        assertEquals("21 Oct 1972 21:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0100").toGMTString());
        assertEquals("21 Oct 1972 22:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0200").toGMTString());
        assertEquals("21 Oct 1972 23:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0300").toGMTString());
        assertEquals("22 Oct 1972 00:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0400").toGMTString());
        assertEquals("22 Oct 1972 01:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0500").toGMTString());
        assertEquals("22 Oct 1972 02:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0600").toGMTString());
        assertEquals("22 Oct 1972 03:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0700").toGMTString());
        assertEquals("22 Oct 1972 04:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0800").toGMTString());
        assertEquals("22 Oct 1972 05:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0900").toGMTString());
        assertEquals("22 Oct 1972 06:16:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -1000").toGMTString());

        assertEquals("21 Oct 1972 19:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0030").toGMTString());
        assertEquals("21 Oct 1972 18:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0130").toGMTString());
        assertEquals("21 Oct 1972 17:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0230").toGMTString());
        assertEquals("21 Oct 1972 16:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0330").toGMTString());
        assertEquals("21 Oct 1972 15:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0430").toGMTString());
        assertEquals("21 Oct 1972 14:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0530").toGMTString());
        assertEquals("21 Oct 1972 13:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0630").toGMTString());
        assertEquals("21 Oct 1972 12:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0730").toGMTString());
        assertEquals("21 Oct 1972 11:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0830").toGMTString());
        assertEquals("21 Oct 1972 10:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +0930").toGMTString());
        assertEquals("21 Oct 1972 09:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +1030").toGMTString());
        assertEquals("21 Oct 1972 08:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +1130").toGMTString());
        assertEquals("21 Oct 1972 07:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 +1230").toGMTString());

        assertEquals("21 Oct 1972 20:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0030").toGMTString());
        assertEquals("21 Oct 1972 21:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0130").toGMTString());
        assertEquals("21 Oct 1972 22:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0230").toGMTString());
        assertEquals("21 Oct 1972 23:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0330").toGMTString());
        assertEquals("22 Oct 1972 00:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0430").toGMTString());
        assertEquals("22 Oct 1972 01:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0530").toGMTString());
        assertEquals("22 Oct 1972 02:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0630").toGMTString());
        assertEquals("22 Oct 1972 03:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0730").toGMTString());
        assertEquals("22 Oct 1972 04:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0830").toGMTString());
        assertEquals("22 Oct 1972 05:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -0930").toGMTString());
        assertEquals("22 Oct 1972 06:46:27 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:16:27 -1030").toGMTString());

    }
}
