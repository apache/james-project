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

import static org.junit.Assert.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.mail.Flags;

import org.apache.commons.lang.time.FastDateFormat;
import org.apache.james.protocols.imap.DecodingException;
import org.apache.james.protocols.imap.utils.DecoderUtils;
import org.junit.Test;

public class DecoderUtilsTest {

    private static final String EXTENSION_FLAG = "\\Extension";

    private static final String A_CUSTOM_FLAG = "Another";


    
    @Test
    public void testSetRecentFlag() {
        Flags flags = new Flags();
        try {
            DecoderUtils.setFlag("\\Recent", flags);
            fail();
        } catch (DecodingException e) {
            // expected
        }
    }

    @Test
    public void testSetOtherFlag() throws Exception {
        Flags flags = new Flags();
        DecoderUtils.setFlag(A_CUSTOM_FLAG, flags);
        assertTrue("Unknown flags should be added", flags
                .contains(A_CUSTOM_FLAG));
    }

    @Test
    public void testExtensionFlag() throws Exception {
        Flags flags = new Flags();
        DecoderUtils.setFlag(EXTENSION_FLAG, flags);
        assertTrue("Extension flags should be added", flags
                .contains(EXTENSION_FLAG));
    }

    @Test
    public void testBadDateTime() throws Exception {
        checkDateTime(null);
        checkDateTime("");
        checkDateTime("This is a string long enough to be too big");
        checkDateTime("1");
        checkDateTime("12");
        checkDateTime("123");
        checkDateTime("1234");
        checkDateTime("12345");
        checkDateTime("123456");
        checkDateTime("1234567");
        checkDateTime("12345678");
        checkDateTime("123456789");
        checkDateTime("1234567890");
        checkDateTime("12345678901");
        checkDateTime("123456789012");
        checkDateTime("1234567890123");
        checkDateTime("12345678901234");
        checkDateTime("123456789012345");
        checkDateTime("1234567890123456");
        checkDateTime("12345678901234567");
        checkDateTime("123456789012345678");
        checkDateTime("1234567890123456789");
        checkDateTime("12345678901234567890");
        checkDateTime("123456789012345678901");
        checkDateTime("1234567890123456789012");
        checkDateTime("12345678901234567890123");
        checkDateTime("123456789012345678901234");
        checkDateTime("1234567890123456789012345");
        checkDateTime("12345678901234567890123456");
        checkDateTime("123456789012345678901234567");
    }

    private void checkDateTime(String datetime) throws Exception {
        try {
            DecoderUtils.decodeDateTime(datetime);
            fail("Bad date-time" + datetime);
        } catch (DecodingException e) {
            // expected
        }
    }

    @Test
    @SuppressWarnings("deprecation")
	public void testSimpleDecodeDateTime() throws Exception {
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
        assertEquals("21 Oct 1972 20:30:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -0030").toGMTString());

        assertEquals("22 Oct 1972 06:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 20:00:00 -1000").toGMTString());
        assertEquals("20 Oct 1972 20:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 06:00:00 +1000").toGMTString());
        assertEquals("21 Oct 1972 16:00:00 GMT", DecoderUtils.decodeDateTime(
                "21-Oct-1972 06:00:00 -1000").toGMTString());
    }

    @Test
    @SuppressWarnings("deprecation")
	public void testAppleMailPrependsZeroNotSpace() throws Exception {
        assertEquals("9 Apr 2008 13:17:51 GMT", DecoderUtils.decodeDateTime(
                "09-Apr-2008 15:17:51 +0200").toGMTString());
    }

    @Test
    public void testDecodeDateTime() throws Exception {
        runTimeZoneTest(TimeZone.getTimeZone("GMT+0"));
        runTimeZoneTest(TimeZone.getTimeZone("GMT+1"));
        runTimeZoneTest(TimeZone.getTimeZone("GMT-1"));
        runTimeZoneTest(TimeZone.getTimeZone("GMT+2"));
        runTimeZoneTest(TimeZone.getTimeZone("GMT-2"));
        runTimeZoneTest(TimeZone.getTimeZone("GMT+3"));
        runTimeZoneTest(TimeZone.getTimeZone("GMT-3"));
        runTimeZoneTest(TimeZone.getTimeZone("GMT+11"));
        runTimeZoneTest(TimeZone.getTimeZone("GMT-11"));
        runTimeZoneTest(TimeZone.getTimeZone("GMT+1030"));
        runTimeZoneTest(TimeZone.getTimeZone("GMT-1030"));
        runTimeZoneTest(TimeZone.getTimeZone("GMT+0045"));
        runTimeZoneTest(TimeZone.getTimeZone("GMT-0045"));
    }

    private void runTimeZoneTest(TimeZone zone) throws Exception {
        runDecodeDateTimeTest(new Date(10000000), zone);
        runDecodeDateTimeTest(new Date(100000000), zone);
        runDecodeDateTimeTest(new Date(1000000000), zone);
        runDecodeDateTimeTest(new Date(10000000000L), zone);
        runDecodeDateTimeTest(new Date(100000000000L), zone);
        runDecodeDateTimeTest(new Date(1000000000000L), zone);
        runDecodeDateTimeTest(new Date(1194168899658L), zone);
        runDecodeDateTimeTest(new Date(1912093499271L), zone);
        runDecodeDateTimeTest(new Date(1526720308423L), zone);
        runDecodeDateTimeTest(new Date(1487487260757L), zone);
        runDecodeDateTimeTest(new Date(1584040720026L), zone);
        runDecodeDateTimeTest(new Date(1983293490921L), zone);
        runDecodeDateTimeTest(new Date(1179806572669L), zone);
        runDecodeDateTimeTest(new Date(1194038035064L), zone);
        runDecodeDateTimeTest(new Date(1057865248366L), zone);
        runDecodeDateTimeTest(new Date(1052797936633L), zone);
        runDecodeDateTimeTest(new Date(1075268253439L), zone);
        runDecodeDateTimeTest(new Date(1033938440306L), zone);
        runDecodeDateTimeTest(new Date(1031614051298L), zone);
        runDecodeDateTimeTest(new Date(1059929345305L), zone);
        runDecodeDateTimeTest(new Date(1162582627756L), zone);
        runDecodeDateTimeTest(new Date(1185747232134L), zone);
        runDecodeDateTimeTest(new Date(1151301821303L), zone);
        runDecodeDateTimeTest(new Date(1116091684805L), zone);
        runDecodeDateTimeTest(new Date(1159599194961L), zone);
        runDecodeDateTimeTest(new Date(1222523245646L), zone);
        runDecodeDateTimeTest(new Date(1219556266559L), zone);
        runDecodeDateTimeTest(new Date(1290015730272L), zone);
        runDecodeDateTimeTest(new Date(1221694598854L), zone);
        runDecodeDateTimeTest(new Date(1212132783343L), zone);
        runDecodeDateTimeTest(new Date(1221761134897L), zone);
        runDecodeDateTimeTest(new Date(1270941981377L), zone);
        runDecodeDateTimeTest(new Date(1224491731327L), zone);
        runDecodeDateTimeTest(new Date(1268571556436L), zone);
        runDecodeDateTimeTest(new Date(1246838821081L), zone);
        runDecodeDateTimeTest(new Date(1226795970848L), zone);
        runDecodeDateTimeTest(new Date(1260254185119L), zone);
    }

    private void runDecodeDateTimeTest(Date date, TimeZone zone)
            throws Exception {
        dateDecode(formatAsImap(date, zone), zone);
    }

    private void dateDecode(String in, TimeZone zone) throws Exception {
        Date date = DecoderUtils.decodeDateTime(in);
        String out = formatAsImap(date, zone);
        assertEquals("Round trip", in, out);
    }

    private String formatAsImap(Date date, TimeZone zone) {
        assertNotNull(date);
        FastDateFormat format = FastDateFormat.getInstance(
                "dd-MMM-yyyy hh:mm:ss Z", zone, Locale.US);
        String out = format.format(date);
        if (out.charAt(0) == '0') {
            out = ' ' + out.substring(1, out.length());
        }
        return out;
    }

    @Test
    public void testDecodeDigit() throws Exception {
        assertEquals(0, DecoderUtils.decodeDigit('0'));
        assertEquals(1, DecoderUtils.decodeDigit('1'));
        assertEquals(2, DecoderUtils.decodeDigit('2'));
        assertEquals(3, DecoderUtils.decodeDigit('3'));
        assertEquals(4, DecoderUtils.decodeDigit('4'));
        assertEquals(5, DecoderUtils.decodeDigit('5'));
        assertEquals(6, DecoderUtils.decodeDigit('6'));
        assertEquals(7, DecoderUtils.decodeDigit('7'));
        assertEquals(8, DecoderUtils.decodeDigit('8'));
        assertEquals(9, DecoderUtils.decodeDigit('9'));

        try {
            DecoderUtils.decodeDigit('/');
            fail("/ is not a digit");
        } catch (DecodingException e) {
            // expected
        }

        try {
            DecoderUtils.decodeDigit(':');
            fail(": is not a digit");
        } catch (DecodingException e) {
            // expected
        }
    }

    @Test
    public void testDecodeMonth() throws Exception {
        assertEquals(Calendar.JANUARY, DecoderUtils.decodeMonth('J', 'A', 'N'));
        assertEquals(Calendar.JANUARY, DecoderUtils.decodeMonth('j', 'a', 'n'));
        assertEquals(Calendar.FEBRUARY, DecoderUtils.decodeMonth('F', 'E', 'B'));
        assertEquals(Calendar.FEBRUARY, DecoderUtils.decodeMonth('f', 'e', 'b'));
        assertEquals(Calendar.MARCH, DecoderUtils.decodeMonth('M', 'A', 'R'));
        assertEquals(Calendar.MARCH, DecoderUtils.decodeMonth('m', 'a', 'r'));
        assertEquals(Calendar.APRIL, DecoderUtils.decodeMonth('A', 'P', 'R'));
        assertEquals(Calendar.APRIL, DecoderUtils.decodeMonth('a', 'p', 'r'));
        assertEquals(Calendar.MAY, DecoderUtils.decodeMonth('M', 'A', 'Y'));
        assertEquals(Calendar.MAY, DecoderUtils.decodeMonth('m', 'a', 'y'));
        assertEquals(Calendar.JUNE, DecoderUtils.decodeMonth('J', 'U', 'N'));
        assertEquals(Calendar.JUNE, DecoderUtils.decodeMonth('j', 'u', 'n'));
        assertEquals(Calendar.JULY, DecoderUtils.decodeMonth('J', 'U', 'L'));
        assertEquals(Calendar.JULY, DecoderUtils.decodeMonth('j', 'u', 'l'));
        assertEquals(Calendar.AUGUST, DecoderUtils.decodeMonth('A', 'U', 'G'));
        assertEquals(Calendar.AUGUST, DecoderUtils.decodeMonth('a', 'u', 'g'));
        assertEquals(Calendar.SEPTEMBER, DecoderUtils
                .decodeMonth('S', 'E', 'P'));
        assertEquals(Calendar.SEPTEMBER, DecoderUtils
                .decodeMonth('s', 'e', 'p'));
        assertEquals(Calendar.OCTOBER, DecoderUtils.decodeMonth('O', 'C', 'T'));
        assertEquals(Calendar.OCTOBER, DecoderUtils.decodeMonth('o', 'c', 't'));
        assertEquals(Calendar.NOVEMBER, DecoderUtils.decodeMonth('N', 'O', 'V'));
        assertEquals(Calendar.NOVEMBER, DecoderUtils.decodeMonth('n', 'o', 'v'));
        assertEquals(Calendar.DECEMBER, DecoderUtils.decodeMonth('D', 'E', 'C'));
        assertEquals(Calendar.DECEMBER, DecoderUtils.decodeMonth('d', 'e', 'c'));
    }

    @Test
    public void testRejectBogusMonths() throws Exception {
        checkReject('N', 'O', 'C');
        checkReject('A', 'N', 'T');
        checkReject('Z', 'Z', 'Z');
        checkReject('S', 'I', 'P');
        checkReject('D', 'E', 'P');
    }

    private void checkReject(char one, char two, char three) {
        try {
            DecoderUtils.decodeMonth(one, two, three);
            fail(one + two + three + "is not a month");
        } catch (DecodingException e) {
            // expected
        }
    }

    @Test
    public void testDecodeYear() throws Exception {
        assertEquals(1999, DecoderUtils.decodeYear('1', '9', '9', '9'));
        assertEquals(747, DecoderUtils.decodeYear('0', '7', '4', '7'));
        assertEquals(2525, DecoderUtils.decodeYear('2', '5', '2', '5'));
        assertEquals(5678, DecoderUtils.decodeYear('5', '6', '7', '8'));
        assertEquals(2453, DecoderUtils.decodeYear('2', '4', '5', '3'));
        assertEquals(2000, DecoderUtils.decodeYear('2', '0', '0', '0'));
        assertEquals(2007, DecoderUtils.decodeYear('2', '0', '0', '7'));
        assertEquals(2008, DecoderUtils.decodeYear('2', '0', '0', '8'));
        assertEquals(2010, DecoderUtils.decodeYear('2', '0', '1', '0'));
        assertEquals(2020, DecoderUtils.decodeYear('2', '0', '2', '0'));
    }

    @Test
    public void testRejectBogusYear() throws Exception {
        checkRejectYear('D', '0', '2', '3');
        checkRejectYear('1', 'A', '2', '3');
        checkRejectYear('1', '5', 'B', '3');
        checkRejectYear('9', '8', '2', 'C');
        checkRejectYear('S', 'A', 'F', 'd');
    }

    private void checkRejectYear(char one, char two, char three, char four) {
        try {
            DecoderUtils.decodeYear(one, two, three, four);
            fail(one + two + three + four + "is not a month");
        } catch (DecodingException e) {
            // expected
        }
    }

    @Test
    public void testDecodeZone() throws Exception {
        assertEquals(0, DecoderUtils.decodeZone('+', '0', '0', '0', '0'));
        assertEquals(100, DecoderUtils.decodeZone('+', '0', '1', '0', '0'));
        assertEquals(200, DecoderUtils.decodeZone('+', '0', '2', '0', '0'));
        assertEquals(300, DecoderUtils.decodeZone('+', '0', '3', '0', '0'));
        assertEquals(400, DecoderUtils.decodeZone('+', '0', '4', '0', '0'));
        assertEquals(500, DecoderUtils.decodeZone('+', '0', '5', '0', '0'));
        assertEquals(600, DecoderUtils.decodeZone('+', '0', '6', '0', '0'));
        assertEquals(700, DecoderUtils.decodeZone('+', '0', '7', '0', '0'));
        assertEquals(800, DecoderUtils.decodeZone('+', '0', '8', '0', '0'));
        assertEquals(900, DecoderUtils.decodeZone('+', '0', '9', '0', '0'));
        assertEquals(1000, DecoderUtils.decodeZone('+', '1', '0', '0', '0'));
        assertEquals(1100, DecoderUtils.decodeZone('+', '1', '1', '0', '0'));
        assertEquals(1200, DecoderUtils.decodeZone('+', '1', '2', '0', '0'));
        assertEquals(30, DecoderUtils.decodeZone('+', '0', '0', '3', '0'));
        assertEquals(130, DecoderUtils.decodeZone('+', '0', '1', '3', '0'));
        assertEquals(230, DecoderUtils.decodeZone('+', '0', '2', '3', '0'));
        assertEquals(330, DecoderUtils.decodeZone('+', '0', '3', '3', '0'));
        assertEquals(430, DecoderUtils.decodeZone('+', '0', '4', '3', '0'));
        assertEquals(530, DecoderUtils.decodeZone('+', '0', '5', '3', '0'));
        assertEquals(630, DecoderUtils.decodeZone('+', '0', '6', '3', '0'));
        assertEquals(730, DecoderUtils.decodeZone('+', '0', '7', '3', '0'));
        assertEquals(830, DecoderUtils.decodeZone('+', '0', '8', '3', '0'));
        assertEquals(930, DecoderUtils.decodeZone('+', '0', '9', '3', '0'));
        assertEquals(1030, DecoderUtils.decodeZone('+', '1', '0', '3', '0'));
        assertEquals(1130, DecoderUtils.decodeZone('+', '1', '1', '3', '0'));
        assertEquals(1111, DecoderUtils.decodeZone('+', '1', '1', '1', '1'));
        assertEquals(0, DecoderUtils.decodeZone('-', '0', '0', '0', '0'));
        assertEquals(-100, DecoderUtils.decodeZone('-', '0', '1', '0', '0'));
        assertEquals(-200, DecoderUtils.decodeZone('-', '0', '2', '0', '0'));
        assertEquals(-300, DecoderUtils.decodeZone('-', '0', '3', '0', '0'));
        assertEquals(-400, DecoderUtils.decodeZone('-', '0', '4', '0', '0'));
        assertEquals(-500, DecoderUtils.decodeZone('-', '0', '5', '0', '0'));
        assertEquals(-600, DecoderUtils.decodeZone('-', '0', '6', '0', '0'));
        assertEquals(-700, DecoderUtils.decodeZone('-', '0', '7', '0', '0'));
        assertEquals(-800, DecoderUtils.decodeZone('-', '0', '8', '0', '0'));
        assertEquals(-900, DecoderUtils.decodeZone('-', '0', '9', '0', '0'));
        assertEquals(-1000, DecoderUtils.decodeZone('-', '1', '0', '0', '0'));
        assertEquals(-1100, DecoderUtils.decodeZone('-', '1', '1', '0', '0'));
        assertEquals(-1200, DecoderUtils.decodeZone('-', '1', '2', '0', '0'));
        assertEquals(-30, DecoderUtils.decodeZone('-', '0', '0', '3', '0'));
        assertEquals(-130, DecoderUtils.decodeZone('-', '0', '1', '3', '0'));
        assertEquals(-230, DecoderUtils.decodeZone('-', '0', '2', '3', '0'));
        assertEquals(-330, DecoderUtils.decodeZone('-', '0', '3', '3', '0'));
        assertEquals(-430, DecoderUtils.decodeZone('-', '0', '4', '3', '0'));
        assertEquals(-530, DecoderUtils.decodeZone('-', '0', '5', '3', '0'));
        assertEquals(-630, DecoderUtils.decodeZone('-', '0', '6', '3', '0'));
        assertEquals(-730, DecoderUtils.decodeZone('-', '0', '7', '3', '0'));
        assertEquals(-830, DecoderUtils.decodeZone('-', '0', '8', '3', '0'));
        assertEquals(-930, DecoderUtils.decodeZone('-', '0', '9', '3', '0'));
        assertEquals(-1030, DecoderUtils.decodeZone('-', '1', '0', '3', '0'));
        assertEquals(-1130, DecoderUtils.decodeZone('-', '1', '1', '3', '0'));
        assertEquals(-1111, DecoderUtils.decodeZone('-', '1', '1', '1', '1'));

    }

    @Test
    public void testBogusZones() throws Exception {
        checkRejectZone(" 0000");
        checkRejectZone(" GMT ");
        checkRejectZone("DANG!");
        checkRejectZone("+a000");
        checkRejectZone("+0b00");
        checkRejectZone("+00c0");
        checkRejectZone("+000d");
        checkRejectZone("-a000");
        checkRejectZone("-0b00");
        checkRejectZone("-00c0");
        checkRejectZone("-000d");
    }

    private void checkRejectZone(String zone) {
        try {
            DecoderUtils.decodeZone(zone.charAt(0), zone.charAt(1), zone
                    .charAt(2), zone.charAt(3), zone.charAt(4));
            fail(zone + "is not a timezone");
        } catch (DecodingException e) {
            // expected
        }
    }

    @Test
    public void testIsSimpleDigit() throws Exception {
        assertTrue(DecoderUtils.isSimpleDigit('0'));
        assertTrue(DecoderUtils.isSimpleDigit('1'));
        assertTrue(DecoderUtils.isSimpleDigit('2'));
        assertTrue(DecoderUtils.isSimpleDigit('3'));
        assertTrue(DecoderUtils.isSimpleDigit('4'));
        assertTrue(DecoderUtils.isSimpleDigit('5'));
        assertTrue(DecoderUtils.isSimpleDigit('6'));
        assertTrue(DecoderUtils.isSimpleDigit('7'));
        assertTrue(DecoderUtils.isSimpleDigit('8'));
        assertTrue(DecoderUtils.isSimpleDigit('9'));

        assertFalse(DecoderUtils.isSimpleDigit('/'));
        assertFalse(DecoderUtils.isSimpleDigit('.'));
        assertFalse(DecoderUtils.isSimpleDigit('-'));
        assertFalse(DecoderUtils.isSimpleDigit('+'));
        assertFalse(DecoderUtils.isSimpleDigit(','));
        assertFalse(DecoderUtils.isSimpleDigit('*'));
        assertFalse(DecoderUtils.isSimpleDigit(':'));
        assertFalse(DecoderUtils.isSimpleDigit(';'));
        assertFalse(DecoderUtils.isSimpleDigit('<'));
        assertFalse(DecoderUtils.isSimpleDigit('='));
        assertFalse(DecoderUtils.isSimpleDigit('>'));
        assertFalse(DecoderUtils.isSimpleDigit('A'));
        assertFalse(DecoderUtils.isSimpleDigit('B'));
    }

    @Test
    public void testDecodeNumber() throws Exception {
        assertEquals(1, DecoderUtils.decodeNumber('0', '1'));
        assertEquals(2, DecoderUtils.decodeNumber('0', '2'));
        assertEquals(3, DecoderUtils.decodeNumber('0', '3'));
        assertEquals(4, DecoderUtils.decodeNumber('0', '4'));
        assertEquals(5, DecoderUtils.decodeNumber('0', '5'));
        assertEquals(6, DecoderUtils.decodeNumber('0', '6'));
        assertEquals(7, DecoderUtils.decodeNumber('0', '7'));
        assertEquals(8, DecoderUtils.decodeNumber('0', '8'));
        assertEquals(9, DecoderUtils.decodeNumber('0', '9'));
        assertEquals(19, DecoderUtils.decodeNumber('1', '9'));
        assertEquals(28, DecoderUtils.decodeNumber('2', '8'));
        assertEquals(37, DecoderUtils.decodeNumber('3', '7'));
        assertEquals(46, DecoderUtils.decodeNumber('4', '6'));
        assertEquals(55, DecoderUtils.decodeNumber('5', '5'));
        assertEquals(64, DecoderUtils.decodeNumber('6', '4'));
        assertEquals(73, DecoderUtils.decodeNumber('7', '3'));
        assertEquals(82, DecoderUtils.decodeNumber('8', '2'));
        assertEquals(91, DecoderUtils.decodeNumber('9', '1'));
    }

    @Test
    public void testRejectNumber() throws Exception {
        checkRejectNumber("A1");
        checkRejectNumber("1A");
        checkRejectNumber("AA");
    }

    private void checkRejectNumber(String number) {
        try {
            DecoderUtils.decodeNumber(number.charAt(0), number.charAt(1));
            fail(number + "is not a number");
        } catch (DecodingException e) {
            // expected
        }
    }
}
