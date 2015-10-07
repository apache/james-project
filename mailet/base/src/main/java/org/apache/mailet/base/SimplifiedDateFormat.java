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
import java.util.TimeZone;

/**
 * <p>This interface is designed to provide a simplified subset of the
 * methods provided by the <code>java.text.DateFormat</code> class.</p>
 *
 * <p>This interface is necessary because of the difficulty in writing
 * thread safe classes that inherit from <code>java.text.DateFormat</code>.
 * This difficulty leads us to approach the problem using composition
 * rather than inheritance.  In general classes that implement this
 * interface will delegate these calls to an internal DateFormat object.</p>
 *
 */
public interface SimplifiedDateFormat {

    /**
     * Formats a Date into a date/time string.
     * @param d the time value to be formatted into a time string.
     * @return the formatted time string.
     */
    public String format(Date d);

    /**
     * Parses text from the beginning of the given string to produce a date.
     * The method may not use the entire text of the given string.
     *
     * @param source A <code>String</code> whose beginning should be parsed.
     * @return A <code>Date</code> parsed from the string.
     * @throws ParseException if the beginning of the specified string
     *         cannot be parsed.
     */
    public Date parse(String source) throws ParseException;

    /**
     * Sets the time zone of this SimplifiedDateFormat object.
     * @param zone the given new time zone.
     */
    public void setTimeZone(TimeZone zone);

    /**
     * Gets the time zone.
     * @return the time zone associated with this SimplifiedDateFormat.
     */
    public TimeZone getTimeZone();

    /**
     * Specify whether or not date/time parsing is to be lenient.  With
     * lenient parsing, the parser may use heuristics to interpret inputs that
     * do not precisely match this object's format.  With strict parsing,
     * inputs must match this object's format.
     * @param lenient when true, parsing is lenient
     * @see java.util.Calendar#setLenient
     */
    public void setLenient(boolean lenient);

    /**
     * Tell whether date/time parsing is to be lenient.
     * @return whether this SimplifiedDateFormat is lenient.
     */
    public boolean isLenient();
}

