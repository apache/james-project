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

package org.apache.james.imap.api.message.request;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * An IMAP <code>date</code> production.
 */
public class DayMonthYear {

    private final int day;

    private final int month;

    private final int year;

    public DayMonthYear(final int day, final int month, final int year) {
        super();
        this.day = day;
        this.month = month;
        this.year = year;
    }

    /**
     * Gets the day component of this date.
     * 
     * @return the day of the month, one based
     */
    public final int getDay() {
        return day;
    }

    /**
     * Gets the month component of this date.
     * 
     * @return the month of the year, one based
     */
    public final int getMonth() {
        return month;
    }

    /**
     * Return the {@link Date} representation
     * 
     * @return date
     */
    public final Date toDate() {
        Calendar cal = getGMT();
        cal.set(getYear(), getMonth() -1,  getDay());
        return cal.getTime();
    }
    
    private Calendar getGMT() {
        return Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.UK);
    }
    
    /**
     * Gets the year component of this date.
     * 
     * @return the year
     */
    public final int getYear() {
        return year;
    }

    public String toString() {
        return day + "-" + month + "-" + year;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + day;
        result = PRIME * result + month;
        result = PRIME * result + year;
        return result;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final DayMonthYear other = (DayMonthYear) obj;
        if (day != other.day)
            return false;
        if (month != other.month)
            return false;
        if (year != other.year)
            return false;
        return true;
    }

}
