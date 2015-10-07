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


package org.apache.mailet.base;

import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A thread-safe date formatting class to produce dates formatted in accord with the
 * specifications of RFC 977.
 *
 */
public class RFC977DateFormat implements SimplifiedDateFormat {

    /**
     * Internal date formatter for long date formats
     */
    private final SynchronizedDateFormat internalLongDateFormat;

    /**
     * Internal date formatter for short date formats
     */
    private final SynchronizedDateFormat internalShortDateFormat;

    /**
     * Constructor for RFC977DateFormat
     */
    public RFC977DateFormat() {
        internalLongDateFormat = new SynchronizedDateFormat("yyyyMMdd HHmmss", Locale.ENGLISH);
        internalShortDateFormat = new SynchronizedDateFormat("yyMMdd HHmmss", Locale.ENGLISH);
    }

    /**
     * This method returns the long form of the RFC977 Date
     *
     * @return java.lang.String
     * @param d Date
     */
    public String format(Date d) {
        return internalLongDateFormat.format(d);
    }

    /**
     * Parses text from the beginning of the given string to produce a date.
     * The method may not use the entire text of the given string.
     * <p>
     * This method is designed to be thread safe, so we wrap our delegated
     * parse method in an appropriate synchronized block.
     *
     * @param source A <code>String</code> whose beginning should be parsed.
     * @return A <code>Date</code> parsed from the string.
     * @throws ParseException if the beginning of the specified string
     *         cannot be parsed.
     */
    public Date parse(String source) throws ParseException {
        source = source.trim();
        if (source.indexOf(' ') == 6) {
            return internalShortDateFormat.parse(source);
        } else {
            return internalLongDateFormat.parse(source);
        }
    }

    /**
     * Sets the time zone of this SynchronizedDateFormat object.
     * @param zone the given new time zone.
     */
    public void setTimeZone(TimeZone zone) {
        synchronized(this) {
            internalShortDateFormat.setTimeZone(zone);
            internalLongDateFormat.setTimeZone(zone);
        }
    }

    /**
     * Gets the time zone.
     * @return the time zone associated with this SynchronizedDateFormat.
     */
    public TimeZone getTimeZone() {
        synchronized(this) {
            return internalShortDateFormat.getTimeZone();
        }
    }

    /**
     * Specify whether or not date/time parsing is to be lenient.  With
     * lenient parsing, the parser may use heuristics to interpret inputs that
     * do not precisely match this object's format.  With strict parsing,
     * inputs must match this object's format.
     * @param lenient when true, parsing is lenient
     * @see java.util.Calendar#setLenient
     */
    public void setLenient(boolean lenient)
    {
        synchronized(this) {
            internalShortDateFormat.setLenient(lenient);
            internalLongDateFormat.setLenient(lenient);
        }
    }

    /**
     * Tell whether date/time parsing is to be lenient.
     * @return whether this SynchronizedDateFormat is lenient.
     */
    public boolean isLenient()
    {
        synchronized(this) {
            return internalShortDateFormat.isLenient();
        }
    }


    /**
     * Overrides equals
     */
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RFC977DateFormat)) {
            return false;
        }
        RFC977DateFormat theOtherRFC977DateFormat = (RFC977DateFormat)obj;
        synchronized (this) {
            return ((internalShortDateFormat.equals(theOtherRFC977DateFormat.internalShortDateFormat)) &&
                    (internalLongDateFormat.equals(theOtherRFC977DateFormat.internalLongDateFormat)));
        }
    }

    /**
     * Overrides hashCode
     */
    public int hashCode() {
        return internalLongDateFormat.hashCode() & internalShortDateFormat.hashCode();
    }

}
