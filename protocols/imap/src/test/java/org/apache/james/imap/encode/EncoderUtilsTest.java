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

package org.apache.james.imap.encode;

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;


public class EncoderUtilsTest {


    @Test
    public void testSimpleEncodeDateTime() {
        assertEquals("03-Sep-2004 05:08:43 +0000", EncoderUtils
                .encodeDateTime(new Date(1094188123661L)));
        assertEquals("10-Dec-2005 04:15:23 +0000", EncoderUtils
                .encodeDateTime(new Date(1134188123661L)));
        assertEquals("11-Jul-2007 21:08:43 +0000", EncoderUtils
                .encodeDateTime(new Date(1184188123661L)));
        assertEquals("04-Nov-2007 14:55:23 +0000", EncoderUtils
                .encodeDateTime(new Date(1194188123661L)));
        assertEquals("31-Aug-2353 20:32:24 +0000", EncoderUtils
                .encodeDateTime(new Date(12107305944309L)));
        assertEquals("03-Jan-2322 11:42:52 +0000", EncoderUtils
                .encodeDateTime(new Date(11108230972614L)));
        assertEquals("30-Oct-2321 21:19:44 +0000", EncoderUtils
                .encodeDateTime(new Date(11102649584790L)));
        assertEquals("30-Sep-2321 05:22:54 +0000", EncoderUtils
                .encodeDateTime(new Date(11100000174728L)));
        assertEquals("08-Jun-2353 08:38:17 +0000", EncoderUtils
                .encodeDateTime(new Date(12100005497072L)));
        assertEquals("14-Jun-2353 01:11:35 +0000", EncoderUtils
                .encodeDateTime(new Date(12100497095056L)));
        assertEquals("13-Jul-2353 18:06:29 +0000", EncoderUtils
                .encodeDateTime(new Date(12103063589157L)));
        assertEquals("05-Feb-2290 02:32:14 +0000", EncoderUtils
                .encodeDateTime(new Date(10101292334681L)));
        assertEquals("20-Oct-2321 06:24:33 +0000", EncoderUtils
                .encodeDateTime(new Date(11101731873573L)));
        assertEquals("31-Jan-2290 02:12:49 +0000", EncoderUtils
                .encodeDateTime(new Date(10100859169426L)));

    }

}
