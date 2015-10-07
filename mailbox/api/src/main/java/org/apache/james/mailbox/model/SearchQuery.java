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
package org.apache.james.mailbox.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

/**
 * <p>
 * Models a query used to search for messages. A query is the logical
 * <code>AND</code> of the contained criteria.
 * </p>
 * <p>
 * Each <code>Criterion</code> is composed of an <code>Operator</code>
 * (combining value and operation) together with field information (optional
 * since the criteria type may imply a particular field). Factory methods are
 * provided for criteria.
 * </p>
 */
public class SearchQuery implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The Resolution which should get used for {@link Date} searches
     */
    public static enum DateResolution {
        Second, Minute, Hour, Day, Month, Year
    }

    public static enum AddressType {
        From, To, Cc, Bcc
    }

    /**
     * Allow to sort a {@link SearchQuery} response in different ways.
     */
    public static final class Sort implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Specify on what to sort
         */
        public static enum SortClause {

            /**
             * Internal date and time of the message (internaldate)
             */
            Arrival,

            /**
             * addr-mailbox of the first "cc" address.
             * 
             * This MUST BE converted to uppercase before doing the sort
             */
            MailboxCc,

            /**
             * addr-mailbox of the first "from" address.
             * 
             * This MUST BE converted to uppercase before doing the sort
             */
            MailboxFrom,

            /**
             * addr-mailbox of the first "To" address
             * 
             * This MUST BE converted to uppercase before doing the sort
             */
            MailboxTo,

            /**
             * Base subject text.
             * 
             * This MUST BE converted to uppercase before doing the sort
             */
            BaseSubject,

            /**
             * Size of the message in octets.
             */
            Size,

            /**
             * <p>
             * As used in this document, the term "sent date" refers to the date
             * and time from the Date: header, adjusted by time zone to
             * normalize to UTC. For example, "31 Dec 2000 16:01:33 -0800" is
             * equivalent to the UTC date and time of
             * "1 Jan 2001 00:01:33 +0000". If the time zone is invalid, the
             * date and time SHOULD be treated as UTC. If the time is also
             * invalid, the time SHOULD be treated as 00:00:00. If there is no
             * valid date or time, the date and time SHOULD be treated as
             * 00:00:00 on the earliest possible date.
             * 
             * If the sent date cannot be determined (a Date: header is missing
             * or cannot be parsed), the INTERNALDATE for that message is used
             * as the sent date.
             * </p>
             */
            SentDate,

            /**
             * addr-name of the first "From" address
             * 
             * This MUST BE converted to uppercase before doing the sort
             */
            DisplayFrom,

            /**
             * addr-name of the first "To" address
             * 
             * This MUST BE converted to uppercase before doing the sort
             */
            DisplayTo,

            /**
             * Uid of the message. This is the DEFAULT if no other is specified
             */
            Uid
        }

        private final boolean reverse;
        private final SortClause sortClause;

        public Sort(SortClause sortClause, boolean reverse) {
            this.reverse = reverse;
            this.sortClause = sortClause;
        }

        /**
         * Create a new {@link Sort} which is NOT {@link #reverse}
         * 
         * @param sortClause
         */
        public Sort(SortClause sortClause) {
            this(sortClause, false);
        }

        /**
         * Return true if the sort should be in reverse order
         * 
         * @return reverse
         */
        public boolean isReverse() {
            return reverse;
        }

        /**
         * Return the {@link SortClause}
         * 
         * @return clause
         */
        public SortClause getSortClause() {
            return sortClause;
        }
    }

    /**
     * Creates a filter for message size less than the given value
     * 
     * @param value
     *            messages with size less than this value will be selected by
     *            the returned criterion
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion sizeLessThan(long value) {
        return new SizeCriterion(new NumericOperator(value, NumericComparator.LESS_THAN));
    }

    /**
     * Creates a filter for message size greater than the given value
     * 
     * @param value
     *            messages with size greater than this value will be selected by
     *            the returned criterion
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion sizeGreaterThan(long value) {
        return new SizeCriterion(new NumericOperator(value, NumericComparator.GREATER_THAN));
    }

    /**
     * Creates a filter for message size equal to the given value
     * 
     * @param value
     *            messages with size equal to this value will be selected by the
     *            returned criterion
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion sizeEquals(long value) {
        return new SizeCriterion(new NumericOperator(value, NumericComparator.EQUALS));
    }

    /**
     * Creates a filter for message mod-sequence less than the given value
     * 
     * @param value
     *            messages with mod-sequence less than this value will be
     *            selected by the returned criterion
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion modSeqLessThan(long value) {
        return new ModSeqCriterion(new NumericOperator(value, NumericComparator.LESS_THAN));
    }

    /**
     * Creates a filter for message mod-sequence greater than the given value
     * 
     * @param value
     *            messages with mod-sequence greater than this value will be
     *            selected by the returned criterion
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion modSeqGreaterThan(long value) {
        return new ModSeqCriterion(new NumericOperator(value, NumericComparator.GREATER_THAN));
    }

    /**
     * Creates a filter for message mod-sequence equal to the given value
     * 
     * @param value
     *            messages with mod-sequence equal to this value will be
     *            selected by the returned criterion
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion modSeqEquals(long value) {
        return new ModSeqCriterion(new NumericOperator(value, NumericComparator.EQUALS));
    }

    /**
     * Creates a filter matching messages with internal date after the given
     * date.
     * 
     * @param date
     *            given date
     * @param res
     *            the date resolution, either {@link DateResolution#Year},
     *            {@link DateResolution#Month}, {@link DateResolution#Day},
     *            {@link DateResolution#Hour}, {@link DateResolution#Minute} or
     *            {@link DateResolution#Second}
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion internalDateAfter(Date date, DateResolution res) {
        return new InternalDateCriterion(new DateOperator(DateComparator.AFTER, date, res));
    }

    /**
     * Creates a filter matching messages with internal date on the given date.
     * 
     * @param date
     *            given date
     * @param res
     *            the date resolution, either {@link DateResolution#Year},
     *            {@link DateResolution#Month}, {@link DateResolution#Day},
     *            {@link DateResolution#Hour}, {@link DateResolution#Minute} or
     *            {@link DateResolution#Second}
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion internalDateOn(Date date, DateResolution res) {
        return new InternalDateCriterion(new DateOperator(DateComparator.ON, date, res));
    }

    /**
     * Creates a filter matching messages with internal date before the given
     * date.
     * 
     * @param date
     *            given date
     * @param res
     *            the date resolution, either {@link DateResolution#Year},
     *            {@link DateResolution#Month}, {@link DateResolution#Day},
     *            {@link DateResolution#Hour}, {@link DateResolution#Minute} or
     *            {@link DateResolution#Second}
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion internalDateBefore(Date date, DateResolution res) {
        return new InternalDateCriterion(new DateOperator(DateComparator.BEFORE, date, res));
    }

    /**
     * Creates a filter matching messages with the date of the given header
     * after the given date. If the header's value is not a date then it will
     * not be included.
     * 
     * @param headerName
     *            name of the header whose value will be compared, not null
     * @param date
     *            given date
     * @param res
     *            the date resolution, either {@link DateResolution#Year},
     *            {@link DateResolution#Month}, {@link DateResolution#Day},
     *            {@link DateResolution#Hour}, {@link DateResolution#Minute} or
     *            {@link DateResolution#Second}
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion headerDateAfter(String headerName, Date date, DateResolution res) {
        return new HeaderCriterion(headerName, new DateOperator(DateComparator.AFTER, date, res));
    }

    /**
     * Creates a filter matching messages with the date of the given header on
     * the given date. If the header's value is not a date then it will not be
     * included.
     * 
     * @param headerName
     *            name of the header whose value will be compared, not null
     * @param date
     *            given date
     * @param res
     *            the date resolution, either {@link DateResolution#Year},
     *            {@link DateResolution#Month}, {@link DateResolution#Day},
     *            {@link DateResolution#Hour}, {@link DateResolution#Minute} or
     *            {@link DateResolution#Second}
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion headerDateOn(String headerName, Date date, DateResolution res) {
        return new HeaderCriterion(headerName, new DateOperator(DateComparator.ON, date, res));
    }

    /**
     * Creates a filter matching messages with the date of the given header
     * before the given date. If the header's value is not a date then it will
     * not be included.
     * 
     * @param headerName
     *            name of the header whose value will be compared, not null
     * @param date
     *            given date
     * @param res
     *            the date resolution, either {@link DateResolution#Year},
     *            {@link DateResolution#Month}, {@link DateResolution#Day},
     *            {@link DateResolution#Hour}, {@link DateResolution#Minute} or
     *            {@link DateResolution#Second}
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion headerDateBefore(String headerName, Date date, DateResolution res) {
        return new HeaderCriterion(headerName, new DateOperator(DateComparator.BEFORE, date, res));
    }

    /**
     * Creates a filter matching messages whose Address header contains the
     * given address. The address header of the message MUST get canonicalized
     * before try to match it.
     * 
     * @param type
     * @param address
     * @return <code>Criterion</code>
     */
    public static final Criterion address(AddressType type, String address) {
        return new HeaderCriterion(type.name(), new AddressOperator(address));
    }

    /**
     * Creates a filter matching messages whose header value contains the given
     * value.
     * 
     * All to-compared Strings MUST BE converted to uppercase before doing so
     * (this also include the search value)
     * 
     * @param headerName
     *            name of the header whose value will be compared, not null
     * @param value
     *            when null or empty the existance of the header will be
     *            checked, otherwise contained value
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion headerContains(String headerName, String value) {
        if (value == null || value.length() == 0) {
            return headerExists(headerName);
        } else {
            return new HeaderCriterion(headerName, new ContainsOperator(value));
        }
    }

    /**
     * Creates a filter matching messages with a header matching the given name.
     * 
     * All to-compared Strings MUST BE converted to uppercase before doing so
     * (this also include the search value)
     * 
     * @param headerName
     *            name of the header whose value will be compared, not null
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion headerExists(String headerName) {
        return new HeaderCriterion(headerName, ExistsOperator.exists());
    }

    /**
     * Creates a filter matching messages which contains the given text either
     * within the body or in the headers. Implementations may choose to ignore
     * mime parts which cannot be decoded to text.
     * 
     * All to-compared Strings MUST BE converted to uppercase before doing so
     * (this also include the search value)
     * 
     * @param value
     *            search value
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion mailContains(String value) {
        return new TextCriterion(value, Scope.FULL);
    }

    /**
     * Creates a filter matching messages which contains the given text within
     * the body. Implementations may choose to ignore mime parts which cannot be
     * decoded to text.
     * 
     * All to-compared Strings MUST BE converted to uppercase before doing so
     * (this also include the search value)
     * 
     * @param value
     *            search value
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion bodyContains(String value) {
        return new TextCriterion(value, Scope.BODY);
    }

    /**
     * Creates a filter matching messages within any of the given ranges.
     * 
     * @param range
     *            <code>NumericRange</code>'s, not null
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion uid(NumericRange[] range) {
        return new UidCriterion(range);
    }

    /**
     * Creates a filter composing the two different criteria.
     * 
     * @param one
     *            <code>Criterion</code>, not null
     * @param two
     *            <code>Criterion</code>, not null
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion or(Criterion one, Criterion two) {
        final List<Criterion> criteria = new ArrayList<Criterion>();
        criteria.add(one);
        criteria.add(two);
        return new ConjunctionCriterion(Conjunction.OR, criteria);
    }

    /**
     * Creates a filter composing the two different criteria.
     * 
     * @param one
     *            <code>Criterion</code>, not null
     * @param two
     *            <code>Criterion</code>, not null
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion and(Criterion one, Criterion two) {
        final List<Criterion> criteria = new ArrayList<Criterion>();
        criteria.add(one);
        criteria.add(two);
        return new ConjunctionCriterion(Conjunction.AND, criteria);
    }

    /**
     * Creates a filter composing the listed criteria.
     * 
     * @param criteria
     *            <code>List</code> of {@link Criterion}
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion and(List<Criterion> criteria) {
        return new ConjunctionCriterion(Conjunction.AND, criteria);
    }

    /**
     * Creates a filter inverting the given criteria.
     * 
     * @param criterion
     *            <code>Criterion</code>, not null
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion not(Criterion criterion) {
        final List<Criterion> criteria = new ArrayList<Criterion>();
        criteria.add(criterion);
        return new ConjunctionCriterion(Conjunction.NOR, criteria);
    }

    /**
     * Creates a filter on the given flag.
     * 
     * @param flag
     *            <code>Flag</code>, not null
     * @param isSet
     *            true if the messages with the flag set should be matched,
     *            false otherwise
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion flagSet(final Flag flag, final boolean isSet) {
        final Criterion result;
        if (isSet) {
            result = flagIsSet(flag);
        } else {
            result = flagIsUnSet(flag);
        }
        return result;
    }

    /**
     * Creates a filter on the given flag selecting messages where the given
     * flag is selected.
     * 
     * @param flag
     *            <code>Flag</code>, not null
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion flagIsSet(final Flag flag) {
        return new FlagCriterion(flag, BooleanOperator.set());
    }

    /**
     * Creates a filter on the given flag selecting messages where the given
     * flag is not selected.
     * 
     * @param flag
     *            <code>Flag</code>, not null
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion flagIsUnSet(final Flag flag) {
        return new FlagCriterion(flag, BooleanOperator.unset());
    }

    /**
     * Creates a filter on the given flag.
     * 
     * @param flag
     *            <code>Flag</code>, not null
     * @param isSet
     *            true if the messages with the flag set should be matched,
     *            false otherwise
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion flagSet(final String flag, final boolean isSet) {
        final Criterion result;
        if (isSet) {
            result = flagIsSet(flag);
        } else {
            result = flagIsUnSet(flag);
        }
        return result;
    }

    /**
     * Creates a filter on the given flag selecting messages where the given
     * flag is selected.
     * 
     * @param flag
     *            <code>Flag</code>, not null
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion flagIsSet(final String flag) {
        return new CustomFlagCriterion(flag, BooleanOperator.set());
    }

    /**
     * Creates a filter on the given flag selecting messages where the given
     * flag is not selected.
     * 
     * @param flag
     *            <code>Flag</code>, not null
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion flagIsUnSet(final String flag) {
        return new CustomFlagCriterion(flag, BooleanOperator.unset());
    }

    /**
     * Creates a filter matching all messages.
     * 
     * @return <code>Criterion</code>, not null
     */
    public static final Criterion all() {
        return AllCriterion.all();
    }

    private final Set<Long> recentMessageUids = new HashSet<Long>();

    private final List<Criterion> criterias = new ArrayList<Criterion>();

    private List<Sort> sorts = new ArrayList<SearchQuery.Sort>(Arrays.asList(new Sort(Sort.SortClause.Uid, false)));

    public void andCriteria(Criterion crit) {
        criterias.add(crit);
    }

    public List<Criterion> getCriterias() {
        return criterias;
    }

    /**
     * Set the {@link Sort}'s to use
     * 
     * @param sorts
     */
    public void setSorts(List<Sort> sorts) {
        if (sorts == null || sorts.isEmpty())
            throw new IllegalArgumentException("There must be at least one Sort");
        this.sorts = sorts;
    }

    /**
     * Return the {@link Sort}'s which should get used for sorting the result.
     * They get "executed" in a chain, if the first does not give an result the
     * second will get executed etc.
     * 
     * If not set via {@link #setSorts(List)} it will sort via
     * {@link Sort.SortClause#Uid}
     * 
     * @return sorts
     */
    public List<Sort> getSorts() {
        return sorts;
    }

    /**
     * Gets the UIDS of messages which are recent for this client session. The
     * list of recent mail is maintained in the protocol layer since the
     * mechanics are protocol specific.
     * 
     * @return mutable <code>Set</code> of <code>Long</code> UIDS
     */
    public Set<Long> getRecentMessageUids() {
        return recentMessageUids;
    }

    /**
     * Adds all the uids to the collection of recents.
     * 
     * @param uids
     *            not null
     */
    public void addRecentMessageUids(final Collection<Long> uids) {
        recentMessageUids.addAll(uids);
    }

    @Override
    public String toString() {
        return "Search:" + criterias.toString();
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((criterias == null) ? 0 : criterias.hashCode());
        return result;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final SearchQuery other = (SearchQuery) obj;
        if (criterias == null) {
            if (other.criterias != null)
                return false;
        } else if (!criterias.equals(other.criterias))
            return false;
        return true;
    }

    /**
     * Numbers within a particular range. Range includes both high and low
     * boundaries. May be a single value. {@link Long#MAX_VALUE} represents
     * unlimited in either direction.
     */
    public static final class NumericRange implements Serializable {
        private static final long serialVersionUID = 1L;

        private final long lowValue;

        private final long highValue;

        public NumericRange(final long value) {
            super();
            this.lowValue = value;
            this.highValue = value;
        }

        public NumericRange(final long lowValue, final long highValue) {
            super();
            this.lowValue = lowValue;
            this.highValue = highValue;
        }

        public long getHighValue() {
            return highValue;
        }

        public long getLowValue() {
            return lowValue;
        }

        /**
         * Is the given value in this range?
         * 
         * @param value
         *            value to be tested
         * @return true if the value is in range, false otherwise
         */
        public boolean isIn(long value) {
            if (lowValue == Long.MAX_VALUE) {
                return highValue >= value;
            }
            return lowValue <= value && highValue >= value;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + (int) (highValue ^ (highValue >>> 32));
            result = PRIME * result + (int) (lowValue ^ (lowValue >>> 32));
            return result;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final NumericRange other = (NumericRange) obj;
            if (highValue != other.highValue)
                return false;
            if (lowValue != other.lowValue)
                return false;
            return true;
        }

        /**
         * Constructs a <code>String</code> with all attributes in name = value
         * format.
         * 
         * @return a <code>String</code> representation of this object.
         */
        public String toString() {
            return new StringBuffer().append(this.lowValue).append("->").append(this.highValue).toString();
        }

    }

    /**
     * Marker superclass for criteria.
     */
    public static abstract class Criterion implements Serializable {
        private static final long serialVersionUID = 1L;

    }

    public enum Conjunction {
        AND, OR, NOR
    }

    /**
     * Conjunction applying to the contained criteria. {@link #getType}
     * indicates how the conjoined criteria should be related.
     */
    public static final class ConjunctionCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        private final Conjunction type;

        private final List<Criterion> criteria;

        public ConjunctionCriterion(final Conjunction type, final List<Criterion> criteria) {
            super();
            this.type = type;
            this.criteria = criteria;
        }

        /**
         * Gets the criteria related through this conjunction.
         * 
         * @return <code>List</code> of {@link Criterion}
         */
        public List<Criterion> getCriteria() {
            return criteria;
        }

        /**
         * Gets the type of conjunction.
         * 
         * @return not null
         */
        public Conjunction getType() {
            return type;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + ((criteria == null) ? 0 : criteria.hashCode());
            return result;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final ConjunctionCriterion other = (ConjunctionCriterion) obj;
            if (criteria == null) {
                if (other.criteria != null)
                    return false;
            } else if (!criteria.equals(other.criteria))
                return false;
            if (type != other.type)
                return false;
            return true;
        }

        /**
         * Constructs a <code>String</code> with all attributes in name = value
         * format.
         * 
         * @return a <code>String</code> representation of this object.
         */
        public String toString() {
            final String TAB = " ";

            StringBuffer retValue = new StringBuffer();

            retValue.append("ConjunctionCriterion ( ").append("criteria = ").append(this.criteria).append(TAB)
                    .append("type = ").append(this.type).append(TAB).append(" )");

            return retValue.toString();
        }

    }

    /**
     * Any message.
     */
    public static final class AllCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        private static final AllCriterion ALL = new AllCriterion();

        private static Criterion all() {
            return ALL;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            return obj instanceof AllCriterion;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            return 1729;
        }

        public String toString() {
            return "AllCriterion";
        }
    }

    public enum Scope {
        /** Only message body content */
        BODY,

        /** Full message content including headers */
        FULL
    }

    /**
     * Message text.
     */
    public static final class TextCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        private final Scope type;

        private final ContainsOperator operator;

        private TextCriterion(final String value, final Scope type) {
            super();
            this.operator = new ContainsOperator(value);
            this.type = type;
        }

        /**
         * Gets the type of text to be searched.
         * 
         * @return not null
         */
        public Scope getType() {
            return type;
        }

        /**
         * Gets the search operation and value to be evaluated.
         * 
         * @return the <code>Operator</code>, not null
         */
        public ContainsOperator getOperator() {
            return operator;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + ((operator == null) ? 0 : operator.hashCode());
            return result;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final TextCriterion other = (TextCriterion) obj;
            if (operator == null) {
                if (other.operator != null)
                    return false;
            } else if (!operator.equals(other.operator))
                return false;
            if (type != other.type)
                return false;
            return true;
        }

        /**
         * Constructs a <code>String</code> with all attributes in name = value
         * format.
         * 
         * @return a <code>String</code> representation of this object.
         */
        public String toString() {
            final String TAB = " ";

            StringBuffer retValue = new StringBuffer();

            retValue.append("TextCriterion ( ").append("operator = ").append(this.operator).append(TAB)
                    .append("type = ").append(this.type).append(TAB).append(" )");

            return retValue.toString();
        }
    }

    /**
     * Header value content search.
     */
    public static final class HeaderCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        private final HeaderOperator operator;

        private final String headerName;

        private HeaderCriterion(final String headerName, final HeaderOperator operator) {
            super();
            this.operator = operator;
            this.headerName = headerName;
        }

        /**
         * Gets the name of the header whose value is to be searched.
         * 
         * @return the headerName
         */
        public String getHeaderName() {
            return headerName;
        }

        /**
         * Gets the search operation and value to be evaluated.
         * 
         * @return the <code>Operator</code>, not null
         */
        public HeaderOperator getOperator() {
            return operator;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + ((headerName == null) ? 0 : headerName.hashCode());
            result = PRIME * result + ((operator == null) ? 0 : operator.hashCode());
            return result;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final HeaderCriterion other = (HeaderCriterion) obj;
            if (headerName == null) {
                if (other.headerName != null)
                    return false;
            } else if (!headerName.equals(other.headerName))
                return false;
            if (operator == null) {
                if (other.operator != null)
                    return false;
            } else if (!operator.equals(other.operator))
                return false;
            return true;
        }

        /**
         * Constructs a <code>String</code> with all attributes in name = value
         * format.
         * 
         * @return a <code>String</code> representation of this object.
         */
        public String toString() {
            final String TAB = " ";

            StringBuffer retValue = new StringBuffer();

            retValue.append("HeaderCriterion ( ").append("headerName = ").append(this.headerName).append(TAB)
                    .append("operator = ").append(this.operator).append(TAB).append(" )");

            return retValue.toString();
        }

    }

    /**
     * Filters on the internal date.
     */
    public static final class InternalDateCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        private final DateOperator operator;

        public InternalDateCriterion(final DateOperator operator) {
            super();
            this.operator = operator;
        }

        /**
         * Gets the search operation and value to be evaluated.
         * 
         * @return the <code>Operator</code>, not null
         */
        public DateOperator getOperator() {
            return operator;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + ((operator == null) ? 0 : operator.hashCode());
            return result;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final InternalDateCriterion other = (InternalDateCriterion) obj;
            if (operator == null) {
                if (other.operator != null)
                    return false;
            } else if (!operator.equals(other.operator))
                return false;
            return true;
        }

        /**
         * Constructs a <code>String</code> with all attributes in name = value
         * format.
         * 
         * @return a <code>String</code> representation of this object.
         */
        public String toString() {
            final String TAB = " ";

            StringBuffer retValue = new StringBuffer();

            retValue.append("InternalDateCriterion ( ").append("operator = ").append(this.operator).append(TAB)
                    .append(" )");

            return retValue.toString();
        }
    }

    /**
     * Filters on the mod-sequence of the messages.
     */
    public static final class ModSeqCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        private final NumericOperator operator;

        private ModSeqCriterion(final NumericOperator operator) {
            super();
            this.operator = operator;
        }

        /**
         * Gets the search operation and value to be evaluated.
         * 
         * @return the <code>NumericOperator</code>, not null
         */
        public NumericOperator getOperator() {
            return operator;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + ((operator == null) ? 0 : operator.hashCode());
            return result;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final ModSeqCriterion other = (ModSeqCriterion) obj;
            if (operator == null) {
                if (other.operator != null)
                    return false;
            } else if (!operator.equals(other.operator))
                return false;
            return true;
        }

        /**
         * Constructs a <code>String</code> with all attributes in name = value
         * format.
         * 
         * @return a <code>String</code> representation of this object.
         */
        public String toString() {
            final String TAB = " ";

            StringBuffer retValue = new StringBuffer();

            retValue.append("SizeCriterion ( ").append("operator = ").append(this.operator).append(TAB).append(" )");

            return retValue.toString();
        }
    }

    public static final class SizeCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        private final NumericOperator operator;

        private SizeCriterion(final NumericOperator operator) {
            super();
            this.operator = operator;
        }

        /**
         * Gets the search operation and value to be evaluated.
         * 
         * @return the <code>NumericOperator</code>, not null
         */
        public NumericOperator getOperator() {
            return operator;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + ((operator == null) ? 0 : operator.hashCode());
            return result;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final SizeCriterion other = (SizeCriterion) obj;
            if (operator == null) {
                if (other.operator != null)
                    return false;
            } else if (!operator.equals(other.operator))
                return false;
            return true;
        }

        /**
         * Constructs a <code>String</code> with all attributes in name = value
         * format.
         * 
         * @return a <code>String</code> representation of this object.
         */
        public String toString() {
            final String TAB = " ";

            StringBuffer retValue = new StringBuffer();

            retValue.append("SizeCriterion ( ").append("operator = ").append(this.operator).append(TAB).append(" )");

            return retValue.toString();
        }
    }

    /**
     * Filters on a custom flag valuation.
     */
    public static final class CustomFlagCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        private final String flag;

        private final BooleanOperator operator;

        private CustomFlagCriterion(final String flag, final BooleanOperator operator) {
            super();
            this.flag = flag;
            this.operator = operator;
        }

        /**
         * Gets the custom flag to be search.
         * 
         * @return the flag name, not null
         */
        public String getFlag() {
            return flag;
        }

        /**
         * Gets the value to be tested.
         * 
         * @return the <code>BooleanOperator</code>, not null
         */
        public BooleanOperator getOperator() {
            return operator;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + ((flag == null) ? 0 : flag.hashCode());
            result = PRIME * result + ((operator == null) ? 0 : operator.hashCode());
            return result;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final CustomFlagCriterion other = (CustomFlagCriterion) obj;
            if (flag == null) {
                if (other.flag != null)
                    return false;
            } else if (!flag.equals(other.flag))
                return false;
            if (operator == null) {
                if (other.operator != null)
                    return false;
            } else if (!operator.equals(other.operator))
                return false;
            return true;
        }

        /**
         * Constructs a <code>String</code> with all attributes in name = value
         * format.
         * 
         * @return a <code>String</code> representation of this object.
         */
        public String toString() {
            final String TAB = " ";

            StringBuffer retValue = new StringBuffer();

            retValue.append("CustomFlagCriterion ( ").append("flag = ").append(this.flag).append(TAB)
                    .append("operator = ").append(this.operator).append(TAB).append(" )");

            return retValue.toString();
        }
    }

    /**
     * Filters on a standard flag.
     */
    public static final class FlagCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        // Flags not Flag because Flags are serializable and Flag is not
        private final Flags flag;        
        private final BooleanOperator operator;

        private FlagCriterion(final Flag flag, final BooleanOperator operator) {
            super();
            this.flag = new Flags(flag);
            this.operator = operator;
        }

        /**
         * Gets the flag filtered on.
         * 
         * @return the flag, not null
         */
        public Flag getFlag() {
           // safe because the Flags(Flag) does system flags, 
           // and James code also constructs FlagCriterion only with system flags
           return flag.getSystemFlags()[0];
        }

        /**
         * Gets the test to be preformed.
         * 
         * @return the <code>BooleanOperator</code>, not null
         */
        public BooleanOperator getOperator() {
            return operator;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + ((flag == null) ? 0 : flag.hashCode());
            result = PRIME * result + ((operator == null) ? 0 : operator.hashCode());
            return result;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final FlagCriterion other = (FlagCriterion) obj;
            if (flag == null) {
                if (other.flag != null)
                    return false;
            } else if (!flag.equals(other.flag))
                return false;
            if (operator == null) {
                if (other.operator != null)
                    return false;
            } else if (!operator.equals(other.operator))
                return false;
            return true;
        }

        /**
         * Constructs a <code>String</code> with all attributes in name = value
         * format.
         * 
         * @return a <code>String</code> representation of this object.
         */
        public String toString() {
            final String TAB = " ";

            StringBuffer retValue = new StringBuffer();

            retValue.append("FlagCriterion ( ").append("flag = ").append(this.flag).append(TAB).append("operator = ")
                    .append(this.operator).append(TAB).append(" )");

            return retValue.toString();
        }

    }

    /**
     * Filters on message identity.
     */
    public static final class UidCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        private final InOperator operator;

        public UidCriterion(final NumericRange[] ranges) {
            super();
            this.operator = new InOperator(ranges);
        }

        /**
         * Gets the filtering operation.
         * 
         * @return the <code>InOperator</code>
         */
        public InOperator getOperator() {
            return operator;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + ((operator == null) ? 0 : operator.hashCode());
            return result;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final UidCriterion other = (UidCriterion) obj;
            if (operator == null) {
                if (other.operator != null)
                    return false;
            } else if (!operator.equals(other.operator))
                return false;
            return true;
        }

        /**
         * Constructs a <code>String</code> with all attributes in name = value
         * format.
         * 
         * @return a <code>String</code> representation of this object.
         */
        public String toString() {
            final String TAB = " ";

            StringBuffer retValue = new StringBuffer();

            retValue.append("UidCriterion ( ").append("operator = ").append(this.operator).append(TAB).append(" )");

            return retValue.toString();
        }

    }

    /**
     * Search operator.
     */
    public interface Operator extends Serializable {
    }

    /**
     * Marks operator as suitable for header value searching.
     */
    public interface HeaderOperator extends Operator {
    }

    public static final class AddressOperator implements HeaderOperator {
        private static final long serialVersionUID = 1L;

        private final String address;

        public AddressOperator(final String address) {
            super();
            this.address = address;
        }

        /**
         * Gets the value to be searched for.
         * 
         * @return the value
         */
        public String getAddress() {
            return address;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + ((address == null) ? 0 : address.hashCode());
            return result;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final AddressOperator other = (AddressOperator) obj;
            if (address == null) {
                if (other.address != null)
                    return false;
            } else if (!address.equals(other.address))
                return false;
            return true;
        }

        /**
         * Constructs a <code>String</code> with all attributes in name = value
         * format.
         * 
         * @return a <code>String</code> representation of this object.
         */
        public String toString() {
            final String TAB = " ";

            StringBuffer retValue = new StringBuffer();

            retValue.append("AdressOperator ( ").append("address = ").append(this.address).append(TAB).append(" )");

            return retValue.toString();
        }
    }

    /**
     * Contained value search.
     */
    public static final class ContainsOperator implements HeaderOperator {
        private static final long serialVersionUID = 1L;

        private final String value;

        public ContainsOperator(final String value) {
            super();
            this.value = value;
        }

        /**
         * Gets the value to be searched for.
         * 
         * @return the value
         */
        public String getValue() {
            return value;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + ((value == null) ? 0 : value.hashCode());
            return result;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final ContainsOperator other = (ContainsOperator) obj;
            if (value == null) {
                if (other.value != null)
                    return false;
            } else if (!value.equals(other.value))
                return false;
            return true;
        }

        /**
         * Constructs a <code>String</code> with all attributes in name = value
         * format.
         * 
         * @return a <code>String</code> representation of this object.
         */
        public String toString() {
            final String TAB = " ";

            StringBuffer retValue = new StringBuffer();

            retValue.append("ContainsOperator ( ").append("value = ").append(this.value).append(TAB).append(" )");

            return retValue.toString();
        }
    }

    /**
     * Existance search.
     */
    public static final class ExistsOperator implements HeaderOperator {
        private static final long serialVersionUID = 1L;

        private static final ExistsOperator EXISTS = new ExistsOperator();

        public static ExistsOperator exists() {
            return EXISTS;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            return obj instanceof ExistsOperator;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            return 42;
        }

        /**
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return "ExistsCriterion";
        }

    }

    /**
     * Boolean value search.
     */
    public static final class BooleanOperator implements Operator {
        private static final long serialVersionUID = 1L;

        private static final BooleanOperator SET = new BooleanOperator(true);

        private static final BooleanOperator UNSET = new BooleanOperator(false);

        public static BooleanOperator set() {
            return SET;
        }

        public static BooleanOperator unset() {
            return UNSET;
        }

        private final boolean set;

        private BooleanOperator(final boolean set) {
            super();
            this.set = set;
        }

        /**
         * Is the search for set?
         * 
         * @return true indicates that set values should be selected, false
         *         indicates that unset values should be selected
         */
        public boolean isSet() {
            return set;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + (set ? 1231 : 1237);
            return result;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final BooleanOperator other = (BooleanOperator) obj;
            if (set != other.set)
                return false;
            return true;
        }

        /**
         * Constructs a <code>String</code> with all attributes in name = value
         * format.
         * 
         * @return a <code>String</code> representation of this object.
         */
        public String toString() {
            final String TAB = " ";

            StringBuffer retValue = new StringBuffer();

            retValue.append("BooleanOperator ( ").append("set = ").append(this.set).append(TAB).append(" )");

            return retValue.toString();
        }

    }

    public enum NumericComparator {
        EQUALS, LESS_THAN, GREATER_THAN
    }

    /**
     * Searches numeric values.
     */
    public static final class NumericOperator implements Operator {
        private static final long serialVersionUID = 1L;

        private final long value;

        private final NumericComparator type;

        private NumericOperator(final long value, final NumericComparator type) {
            super();
            this.value = value;
            this.type = type;
        }

        /**
         * Gets the operation type
         * 
         * @return not null
         */
        public NumericComparator getType() {
            return type;
        }

        /**
         * Gets the value to be compared.
         * 
         * @return the value
         */
        public long getValue() {
            return value;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + (int) (value ^ (value >>> 32));
            return result;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final NumericOperator other = (NumericOperator) obj;
            if (type != other.type)
                return false;
            if (value != other.value)
                return false;
            return true;
        }

        /**
         * Constructs a <code>String</code> with all attributes in name = value
         * format.
         * 
         * @return a <code>String</code> representation of this object.
         */
        public String toString() {
            final String TAB = " ";

            StringBuffer retValue = new StringBuffer();

            retValue.append("NumericOperator ( ").append("type = ").append(this.type).append(TAB).append("value = ")
                    .append(this.value).append(TAB).append(" )");

            return retValue.toString();
        }
    }

    public enum DateComparator {
        BEFORE, AFTER, ON
    }

    /**
     * Operates on a date.
     */
    public static final class DateOperator implements HeaderOperator {
        private static final long serialVersionUID = 1L;

        public static final int BEFORE = 1;

        public static final int AFTER = 2;

        public static final int ON = 3;

        private final DateComparator type;

        private final Date date;

        private final DateResolution res;

        public DateOperator(final DateComparator type, final Date date, final DateResolution res) {
            super();
            this.type = type;
            this.date = date;
            this.res = res;
        }

        public Date getDate() {
            return date;
        }

        public DateResolution getDateResultion() {
            return res;
        }

        /**
         * Gets the operator type.
         * 
         * @return the type, either {@link DateComparator#BEFORE},
         *         {@link DateComparator#AFTER} or {@link DateComparator#ON}
         */
        public DateComparator getType() {
            return type;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            // result = PRIME * result + (int)date.getTime();
            result = PRIME * result + type.hashCode();
            return result;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final DateOperator other = (DateOperator) obj;
            // if (date != other.date)
            // return false;
            if (res != other.res)
                return false;
            if (type != other.type)
                return false;
            return true;
        }

        /**
         * Constructs a <code>String</code> with all attributes in name = value
         * format.
         * 
         * @return a <code>String</code> representation of this object.
         */
        public String toString() {
            final String TAB = " ";

            StringBuffer retValue = new StringBuffer();

            retValue.append("DateOperator ( ").append("date = ").append(date.toString()).append(TAB).append("res = ")
                    .append(this.res.name()).append(TAB).append("type = ").append(this.type).append(TAB).append(TAB)
                    .append(" )");

            return retValue.toString();
        }

    }

    /**
     * Search for numbers within set of ranges.
     */
    public static final class InOperator implements Operator {
        private static final long serialVersionUID = 1L;

        private final NumericRange[] range;

        public InOperator(final NumericRange[] range) {
            super();
            this.range = range;
        }

        /**
         * Gets the filtering ranges. Values falling within these ranges will be
         * selected.
         * 
         * @return the <code>NumericRange</code>'s search on, not null
         */
        public NumericRange[] getRange() {
            return range;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            return range.length;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final InOperator other = (InOperator) obj;
            if (!Arrays.equals(range, other.range))
                return false;
            return true;
        }

        /**
         * Constructs a <code>String</code> with all attributes in name = value
         * format.
         * 
         * @return a <code>String</code> representation of this object.
         */
        public String toString() {
            final String TAB = " ";

            StringBuffer retValue = new StringBuffer();

            retValue.append("InOperator ( ").append("range = ").append(Arrays.toString(this.range)).append(TAB)
                    .append(" )");

            return retValue.toString();
        }

    }
}
