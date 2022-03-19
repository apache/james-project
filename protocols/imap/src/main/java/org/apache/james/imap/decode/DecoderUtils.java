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

package org.apache.james.imap.decode;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import jakarta.mail.Flags;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.MessageFlags;

/**
 * Utility procedures.
 */
public final class DecoderUtils {

    private static final int ASCII_ZERO = '0';

    private static final int JAN_BIT = 0x1;

    private static final int FEB_BIT = 0x2;

    private static final int MAR_BIT = 0x4;

    private static final int APR_BIT = 0x8;

    private static final int MAY_BIT = 0x10;

    private static final int JUN_BIT = 0x20;

    private static final int JUL_BIT = 0x40;

    private static final int AUG_BIT = 0x80;

    private static final int SEP_BIT = 0x100;

    private static final int OCT_BIT = 0x200;

    private static final int NOV_BIT = 0x400;

    private static final int DEC_BIT = 0x800;

    private static final int ALL_MONTH_BITS = JAN_BIT | FEB_BIT | MAR_BIT | APR_BIT | MAY_BIT | JUN_BIT | JUL_BIT | AUG_BIT | SEP_BIT | OCT_BIT | NOV_BIT | DEC_BIT;

    public static void setFlag(String flagString, Flags flags) throws DecodingException {
        if (flagString.equalsIgnoreCase(MessageFlags.ANSWERED_ALL_CAPS)) {
            flags.add(Flags.Flag.ANSWERED);
        } else if (flagString.equalsIgnoreCase(MessageFlags.DELETED_ALL_CAPS)) {
            flags.add(Flags.Flag.DELETED);
        } else if (flagString.equalsIgnoreCase(MessageFlags.DRAFT_ALL_CAPS)) {
            flags.add(Flags.Flag.DRAFT);
        } else if (flagString.equalsIgnoreCase(MessageFlags.FLAGGED_ALL_CAPS)) {
            flags.add(Flags.Flag.FLAGGED);
        } else if (flagString.equalsIgnoreCase(MessageFlags.SEEN_ALL_CAPS)) {
            flags.add(Flags.Flag.SEEN);
        } else {
            if (flagString.equalsIgnoreCase(MessageFlags.RECENT_ALL_CAPS)) {
                // RFC3501 specifically excludes \Recent
                // The \Recent flag should be set automatically by the server so throw Exception
                //
                // See IMAP-316
                throw new DecodingException(HumanReadableText.INVALID_SYSTEM_FLAG, "\\Recent flag is now allowed to set.");
            } else {
                // RFC3501 allows novel flags
                flags.add(flagString);
            }
        }
    }

    /**
     * Decodes the given string as a standard IMAP date-time.
     * 
     * @param chars
     *            standard IMAP date-time
     * @return <code>Date</code> with time component, not null
     * @throws DecodingException
     *             when this conversion fails
     */
    public static LocalDateTime decodeDateTime(CharSequence chars) throws DecodingException {
        if (isDateTime(chars)) {
            char dayHigh = chars.charAt(0);
            char dayLow = chars.charAt(1);
            int day = decodeFixedDay(dayHigh, dayLow);

            char monthFirstChar = chars.charAt(3);
            char monthSecondChar = chars.charAt(4);
            char monthThirdChar = chars.charAt(5);
            int month = decodeMonth(monthFirstChar, monthSecondChar, monthThirdChar);

            char milleniumChar = chars.charAt(7);
            char centuryChar = chars.charAt(8);
            char decadeChar = chars.charAt(9);
            char yearChar = chars.charAt(10);
            int year = decodeYear(milleniumChar, centuryChar, decadeChar, yearChar);

            char zoneDeterminent = chars.charAt(21);
            char zoneDigitOne = chars.charAt(22);
            char zoneDigitTwo = chars.charAt(23);
            char zoneDigitThree = chars.charAt(24);
            char zoneDigitFour = chars.charAt(25);
            int offset = decodeZone(zoneDeterminent, zoneDigitOne, zoneDigitTwo, zoneDigitThree, zoneDigitFour);

            char hourHigh = chars.charAt(12);
            char hourLow = chars.charAt(13);
            int hour = applyHourOffset(offset, decodeNumber(hourHigh, hourLow));

            char minuteHigh = chars.charAt(15);
            char minuteLow = chars.charAt(16);
            int minute = applyMinuteOffset(offset, decodeNumber(minuteHigh, minuteLow));

            char secondHigh = chars.charAt(18);
            char secondLow = chars.charAt(19);
            int second = decodeNumber(secondHigh, secondLow);

            GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
            calendar.clear();
            calendar.set(year, month, day, hour, minute, second);
            return LocalDateTime.ofInstant(calendar.getTime().toInstant(), ZoneId.systemDefault());
        } else {
            final String message;
            if (chars == null) {
                message = "Expected a date-time but was nothing.";
            } else {
                message = new StringBuffer("Expected a date-time but was ").append(chars.toString()).toString();
            }

            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, message);
        }

    }

    private static boolean isDateTime(CharSequence chars) {
        boolean result;
        if (chars == null) {
            result = false;
        } else if (chars.length() < 26) {
            // Be liberal in what you accept
            result = false;
        } else {
            result = true;
        }
        return result;
    }

    private static int applyMinuteOffset(int offset, int minutes) {
        return minutes - ((Math.abs(offset) % 100) * (offset == 0 ? 0 : offset > 0 ? 1 : -1));
    }

    private static int applyHourOffset(int offset, int hours) {
        return hours - (offset / 100);
    }

    public static int decodeNumber(char high, char low) throws DecodingException {
        return (10 * decodeDigit(high)) + decodeDigit(low);
    }

    public static int decodeZone(char zoneDeterminent, char zoneDigitOne, char zoneDigitTwo, char zoneDigitThree, char zoneDigitFour) throws DecodingException {
        if (isInvalidZone(zoneDeterminent, zoneDigitOne, zoneDigitTwo, zoneDigitThree, zoneDigitFour)) {
            throw createTimeZoneException(zoneDeterminent, zoneDigitOne, zoneDigitTwo, zoneDigitThree, zoneDigitFour);
        }
        int sign;
        if (zoneDeterminent == '+') {
            sign = 1;
        } else if (zoneDeterminent == '-') {
            sign = -1;
        } else {
            throw createTimeZoneException(zoneDeterminent, zoneDigitOne, zoneDigitTwo, zoneDigitThree, zoneDigitFour);
        }
        return sign * ((1000 * decodeDigit(zoneDigitOne)) + (100 * decodeDigit(zoneDigitTwo)) + (10 * decodeDigit(zoneDigitThree)) + decodeDigit(zoneDigitFour));
    }

    private static DecodingException createTimeZoneException(char zoneDeterminent, char zoneDigitOne, char zoneDigitTwo, char zoneDigitThree, char zoneDigitFour) {
        return new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Expected time-zone but was " + zoneDeterminent + zoneDigitOne + zoneDigitTwo + zoneDigitThree + zoneDigitFour);
    }

    private static boolean isInvalidZone(char zoneDeterminent, char zoneDigitOne, char zoneDigitTwo, char zoneDigitThree, char zoneDigitFour) {
        final boolean result;
        if (zoneDeterminent == '+' || zoneDeterminent == '-') {
            result = !(isSimpleDigit(zoneDigitOne) && isSimpleDigit(zoneDigitTwo) && isSimpleDigit(zoneDigitThree) && isSimpleDigit(zoneDigitFour));
        } else {
            result = true;
        }
        return result;
    }

    /**
     * Is the given character an ASCII digit.
     * 
     * @param character
     *            character
     * @return true if ASCII 0-9, false otherwise
     */
    public static boolean isSimpleDigit(char character) {
        return !(character < '0' || character > '9');
    }

    /**
     * Decodes a year.
     * 
     * @param milleniumChar
     *            first digit
     * @param centuryChar
     *            second digit
     * @param decadeChar
     *            third digit
     * @param yearChar
     *            forth digit
     * @return {@link Calendar} year
     */
    public static int decodeYear(char milleniumChar, char centuryChar, char decadeChar, char yearChar) throws DecodingException {
        return (decodeDigit(milleniumChar) * 1000) + (decodeDigit(centuryChar) * 100) + (decodeDigit(decadeChar) * 10) + decodeDigit(yearChar);
    }

    /**
     * Decodes an IMAP <code>date-month</code> to a {@link Calendar} month.
     * 
     * @param monthFirstChar
     *            first character in a month triple
     * @param monthSecondChar
     *            second character in a month triple
     * @param monthThirdChar
     *            third character in a month triple
     * @return {@link Calendar} month (<code>JAN</code>=0)
     */
    public static int decodeMonth(char monthFirstChar, char monthSecondChar, char monthThirdChar) throws DecodingException {
        final int result;
        // Bitwise magic! Eliminate possibility by three switches
        int possibleMonths = ALL_MONTH_BITS;
        switch (monthFirstChar) {
        case 'J':
        case 'j':
            possibleMonths &= (JAN_BIT | JUN_BIT | JUL_BIT);
            break;
        case 'F':
        case 'f':
            possibleMonths &= FEB_BIT;
            break;
        case 'M':
        case 'm':
            possibleMonths &= (MAR_BIT | MAY_BIT);
            break;
        case 'A':
        case 'a':
            possibleMonths &= (APR_BIT | AUG_BIT);
            break;
        case 'S':
        case 's':
            possibleMonths &= SEP_BIT;
            break;
        case 'O':
        case 'o':
            possibleMonths &= OCT_BIT;
            break;
        case 'N':
        case 'n':
            possibleMonths &= NOV_BIT;
            break;
        case 'D':
        case 'd':
            possibleMonths &= DEC_BIT;
            break;
        default:
            possibleMonths = 0;
            break;
        }
        switch (monthSecondChar) {
        case 'A':
        case 'a':
            possibleMonths &= (JAN_BIT | MAR_BIT | MAY_BIT);
            break;
        case 'E':
        case 'e':
            possibleMonths &= (FEB_BIT | SEP_BIT | DEC_BIT);
            break;
        case 'P':
        case 'p':
            possibleMonths &= (APR_BIT);
            break;
        case 'U':
        case 'u':
            possibleMonths &= (JUN_BIT | JUL_BIT | AUG_BIT);
            break;
        case 'C':
        case 'c':
            possibleMonths &= OCT_BIT;
            break;
        case 'O':
        case 'o':
            possibleMonths &= NOV_BIT;
            break;
        default:
            possibleMonths = 0;
            break;
        }
        switch (monthThirdChar) {
        case 'N':
        case 'n':
            possibleMonths &= (JAN_BIT | JUN_BIT);
            break;
        case 'B':
        case 'b':
            possibleMonths &= FEB_BIT;
            break;
        case 'R':
        case 'r':
            possibleMonths &= (MAR_BIT | APR_BIT);
            break;
        case 'Y':
        case 'y':
            possibleMonths &= MAY_BIT;
            break;
        case 'L':
        case 'l':
            possibleMonths &= JUL_BIT;
            break;
        case 'G':
        case 'g':
            possibleMonths &= AUG_BIT;
            break;
        case 'P':
        case 'p':
            possibleMonths &= SEP_BIT;
            break;
        case 'T':
        case 't':
            possibleMonths &= OCT_BIT;
            break;
        case 'V':
        case 'v':
            possibleMonths &= NOV_BIT;
            break;
        case 'C':
        case 'c':
            possibleMonths &= DEC_BIT;
            break;
        default:
            possibleMonths = 0;
            break;
        }
        switch (possibleMonths) {
        case JAN_BIT:
            result = Calendar.JANUARY;
            break;
        case FEB_BIT:
            result = Calendar.FEBRUARY;
            break;
        case MAR_BIT:
            result = Calendar.MARCH;
            break;
        case APR_BIT:
            result = Calendar.APRIL;
            break;
        case MAY_BIT:
            result = Calendar.MAY;
            break;
        case JUN_BIT:
            result = Calendar.JUNE;
            break;
        case JUL_BIT:
            result = Calendar.JULY;
            break;
        case AUG_BIT:
            result = Calendar.AUGUST;
            break;
        case SEP_BIT:
            result = Calendar.SEPTEMBER;
            break;
        case OCT_BIT:
            result = Calendar.OCTOBER;
            break;
        case NOV_BIT:
            result = Calendar.NOVEMBER;
            break;
        case DEC_BIT:
            result = Calendar.DECEMBER;
            break;
        default:
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Expected month name but was " + monthFirstChar + monthSecondChar + monthThirdChar);
        }
        return result;
    }

    public static int decodeFixedDay(char dayHigh, char dayLow) throws DecodingException {
        int result = decodeDigit(dayLow);
        switch (dayHigh) {
        case '0':
            return result;
        case '1':
            return result += 10;
        case '2':
            return result += 20;
        case '3':
            return result += 30;
        case ' ':
            return result;
        }
        throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Expected SP, 0, 1, 2, or 3 but was " + dayHigh);
    }

    /**
     * Decodes a number character into a <code>0-9</code> digit.
     *
     * @return a digit
     * @throws DecodingException
     *             if the char is not a digit
     */
    public static int decodeDigit(char character) throws DecodingException {
        final int result = character - ASCII_ZERO;
        if (result < 0 || result > 9) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Expected a digit but was '" + character + "'");
        }
        return result;
    }
}
