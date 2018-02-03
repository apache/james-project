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
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.mailbox.MessageUid;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

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
    private static final String DATE_HEADER_NAME = "Date";

    /**
     * The Resolution which should get used for {@link Date} searches
     */
    public enum DateResolution {
        Second, Minute, Hour, Day, Month, Year
    }

    public enum AddressType {
        From, To, Cc, Bcc
    }

    /**
     * Allow to sort a {@link SearchQuery} response in different ways.
     */
    public static class Sort implements Serializable {
        private static final long serialVersionUID = 1L;

        public enum Order {
            REVERSE,
            NATURAL
        }

        /**
         * Specify on what to sort
         */
        public enum SortClause {

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
            Uid,

            /**
             * Unique Id of the message.
             */
            Id
        }

        private final Order order;
        private final SortClause sortClause;

        public Sort(SortClause sortClause, Order order) {
            this.order = order;
            this.sortClause = sortClause;
        }

        /**
         * Create a new {@link Sort} which is NOT {@link #order}
         * 
         * @param sortClause
         */
        public Sort(SortClause sortClause) {
            this(sortClause, Order.NATURAL);
        }

        /**
         * Return true if the sort should be in reverse order
         * 
         * @return reverse
         */
        public boolean isReverse() {
            return order == Order.REVERSE;
        }

        /**
         * Return the {@link SortClause}
         * 
         * @return clause
         */
        public SortClause getSortClause() {
            return sortClause;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Sort) {
                Sort that = (Sort) o;
                return Objects.equal(this.sortClause, that.sortClause)
                    && Objects.equal(this.order, that.order);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(sortClause, order);
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
    public static Criterion sizeLessThan(long value) {
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
    public static Criterion sizeGreaterThan(long value) {
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
    public static Criterion sizeEquals(long value) {
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
    public static Criterion modSeqLessThan(long value) {
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
    public static Criterion modSeqGreaterThan(long value) {
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
    public static Criterion modSeqEquals(long value) {
        return new ModSeqCriterion(new NumericOperator(value, NumericComparator.EQUALS));
    }

    /**
     * Creates a filter matching messages with internal date after the given
     * date.
     * 
     * @param date
     *            given date
     * @param dateResolution
     *            the date resolution, either {@link DateResolution#Year},
     *            {@link DateResolution#Month}, {@link DateResolution#Day},
     *            {@link DateResolution#Hour}, {@link DateResolution#Minute} or
     *            {@link DateResolution#Second}
     * @return <code>Criterion</code>, not null
     */
    public static Criterion internalDateAfter(Date date, DateResolution dateResolution) {
        return new InternalDateCriterion(new DateOperator(DateComparator.AFTER, date, dateResolution));
    }

    /**
     * Creates a filter matching messages with internal date on the given date.
     * 
     * @param date
     *            given date
     * @param dateResolution
     *            the date resolution, either {@link DateResolution#Year},
     *            {@link DateResolution#Month}, {@link DateResolution#Day},
     *            {@link DateResolution#Hour}, {@link DateResolution#Minute} or
     *            {@link DateResolution#Second}
     * @return <code>Criterion</code>, not null
     */
    public static Criterion internalDateOn(Date date, DateResolution dateResolution) {
        return new InternalDateCriterion(new DateOperator(DateComparator.ON, date, dateResolution));
    }

    /**
     * Creates a filter matching messages with internal date before the given
     * date.
     * 
     * @param date
     *            given date
     * @param dateResolution
     *            the date resolution, either {@link DateResolution#Year},
     *            {@link DateResolution#Month}, {@link DateResolution#Day},
     *            {@link DateResolution#Hour}, {@link DateResolution#Minute} or
     *            {@link DateResolution#Second}
     * @return <code>Criterion</code>, not null
     */
    public static Criterion internalDateBefore(Date date, DateResolution dateResolution) {
        return new InternalDateCriterion(new DateOperator(DateComparator.BEFORE, date, dateResolution));
    }

    /**
     * Creates a filter matching messages with sent date after the given
     * date.
     * 
     * @param date
     *            given date
     * @param dateResolution
     *            the date resolution, either {@link DateResolution#Year},
     *            {@link DateResolution#Month}, {@link DateResolution#Day},
     *            {@link DateResolution#Hour}, {@link DateResolution#Minute} or
     *            {@link DateResolution#Second}
     * @return <code>Criterion</code>, not null
     */
    public static Criterion sentDateAfter(Date date, DateResolution dateResolution) {
        return headerDateAfter(DATE_HEADER_NAME, date, dateResolution);
    }

    /**
     * Creates a filter matching messages with sent date on the given date.
     * 
     * @param date
     *            given date
     * @param dateResolution
     *            the date resolution, either {@link DateResolution#Year},
     *            {@link DateResolution#Month}, {@link DateResolution#Day},
     *            {@link DateResolution#Hour}, {@link DateResolution#Minute} or
     *            {@link DateResolution#Second}
     * @return <code>Criterion</code>, not null
     */
    public static Criterion sentDateOn(Date date, DateResolution dateResolution) {
        return headerDateOn(DATE_HEADER_NAME, date, dateResolution);
    }

    /**
     * Creates a filter matching messages with sent date before the given
     * date.
     * 
     * @param date
     *            given date
     * @param dateResolution
     *            the date resolution, either {@link DateResolution#Year},
     *            {@link DateResolution#Month}, {@link DateResolution#Day},
     *            {@link DateResolution#Hour}, {@link DateResolution#Minute} or
     *            {@link DateResolution#Second}
     * @return <code>Criterion</code>, not null
     */
    public static Criterion sentDateBefore(Date date, DateResolution dateResolution) {
        return headerDateBefore(DATE_HEADER_NAME, date, dateResolution);
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
     * @param dateResolution
     *            the date resolution, either {@link DateResolution#Year},
     *            {@link DateResolution#Month}, {@link DateResolution#Day},
     *            {@link DateResolution#Hour}, {@link DateResolution#Minute} or
     *            {@link DateResolution#Second}
     * @return <code>Criterion</code>, not null
     */
    public static Criterion headerDateAfter(String headerName, Date date, DateResolution dateResolution) {
        return new HeaderCriterion(headerName, new DateOperator(DateComparator.AFTER, date, dateResolution));
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
     * @param dateResolution
     *            the date resolution, either {@link DateResolution#Year},
     *            {@link DateResolution#Month}, {@link DateResolution#Day},
     *            {@link DateResolution#Hour}, {@link DateResolution#Minute} or
     *            {@link DateResolution#Second}
     * @return <code>Criterion</code>, not null
     */
    public static Criterion headerDateOn(String headerName, Date date, DateResolution dateResolution) {
        return new HeaderCriterion(headerName, new DateOperator(DateComparator.ON, date, dateResolution));
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
     * @param dateResolution
     *            the date resolution, either {@link DateResolution#Year},
     *            {@link DateResolution#Month}, {@link DateResolution#Day},
     *            {@link DateResolution#Hour}, {@link DateResolution#Minute} or
     *            {@link DateResolution#Second}
     * @return <code>Criterion</code>, not null
     */
    public static Criterion headerDateBefore(String headerName, Date date, DateResolution dateResolution) {
        return new HeaderCriterion(headerName, new DateOperator(DateComparator.BEFORE, date, dateResolution));
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
    public static Criterion address(AddressType type, String address) {
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
    public static Criterion headerContains(String headerName, String value) {
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
    public static Criterion headerExists(String headerName) {
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
    public static Criterion mailContains(String value) {
        return new TextCriterion(value, Scope.FULL);
    }

    /**
     * Creates a filter matching messages which contains the given text either
     * within the headers (From, To, Cc, Bcc & Subject) and text / html bodies. 
     * Implementations may choose to ignore mime parts which cannot be decoded to text.
     * 
     * All to-compared Strings MUST BE converted to uppercase before doing so
     * (this also include the search value)
     * 
     * @param value
     *            search value
     * @return <code>Criterion</code>, not null
     */
    public static Criterion textContains(String value) {
        return new TextCriterion(value, Scope.TEXT);
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
    public static Criterion bodyContains(String value) {
        return new TextCriterion(value, Scope.BODY);
    }

    /**
     * Creates a filter matching messages which has an attachment containing the given text.
     * 
     * @param value
     *            search value
     * @return <code>Criterion</code>, not null
     */
    public static Criterion attachmentContains(String value) {
        return new TextCriterion(value, Scope.ATTACHMENTS);
    }

    /**
     * Creates a filter matching messages within any of the given ranges.
     * 
     * @param range
     *            <code>NumericRange</code>'s, not null
     * @return <code>Criterion</code>, not null
     */
    public static Criterion uid(UidRange[] range) {
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
    public static Criterion or(Criterion one, Criterion two) {
        final List<Criterion> criteria = new ArrayList<>();
        criteria.add(one);
        criteria.add(two);
        return new ConjunctionCriterion(Conjunction.OR, criteria);
    }

    /**
     * Creates a filter composing the listed criteria.
     * 
     * @param criteria
     *            <code>List</code> of {@link Criterion}
     * @return <code>Criterion</code>, not null
     */
    public static Criterion or(List<Criterion> criteria) {
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
    public static Criterion and(Criterion one, Criterion two) {
        final List<Criterion> criteria = new ArrayList<>();
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
    public static Criterion and(List<Criterion> criteria) {
        return new ConjunctionCriterion(Conjunction.AND, criteria);
    }

    /**
     * Creates a filter inverting the given criteria.
     * 
     * @param criterion
     *            <code>Criterion</code>, not null
     * @return <code>Criterion</code>, not null
     */
    public static Criterion not(Criterion criterion) {
        final List<Criterion> criteria = new ArrayList<>();
        criteria.add(criterion);
        return new ConjunctionCriterion(Conjunction.NOR, criteria);
    }

    /**
     * Creates a filter composing the listed criteria.
     * 
     * @param criteria
     *            <code>List</code> of {@link Criterion}
     * @return <code>Criterion</code>, not null
     */
    public static Criterion not(List<Criterion> criteria) {
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
    public static Criterion flagSet(Flag flag, boolean isSet) {
        final Criterion result;
        if (isSet) {
            result = flagIsSet(flag);
        } else {
            result = flagIsUnSet(flag);
        }
        return result;
    }

    public static Criterion hasAttachment(boolean value) {
        if (value) {
            return new AttachmentCriterion(BooleanOperator.set());
        } else {
            return new AttachmentCriterion(BooleanOperator.unset());
        }
    }

    public static Criterion hasAttachment() {
        return hasAttachment(true);
    }

    public static Criterion hasNoAttachment() {
        return hasAttachment(false);
    }

    /**
     * Creates a filter on the given flag selecting messages where the given
     * flag is selected.
     * 
     * @param flag
     *            <code>Flag</code>, not null
     * @return <code>Criterion</code>, not null
     */
    public static Criterion flagIsSet(Flag flag) {
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
    public static Criterion flagIsUnSet(Flag flag) {
        return new FlagCriterion(flag, BooleanOperator.unset());
    }

    public static Criterion flag(Flag flag, boolean isSet) {
        return new FlagCriterion(flag, new BooleanOperator(isSet));
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
    public static Criterion flagSet(String flag, boolean isSet) {
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
    public static Criterion flagIsSet(String flag) {
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
    public static Criterion flagIsUnSet(String flag) {
        return new CustomFlagCriterion(flag, BooleanOperator.unset());
    }

    /**
     * Creates a filter matching all messages.
     * 
     * @return <code>Criterion</code>, not null
     */
    public static Criterion all() {
        return AllCriterion.all();
    }

    private final Set<MessageUid> recentMessageUids = new HashSet<>();

    private final List<Criterion> criterias;

    private List<Sort> sorts = Collections.singletonList(new Sort(Sort.SortClause.Uid, Sort.Order.NATURAL));

    public SearchQuery(Criterion... criterias) {
        this(new ArrayList<>(Arrays.asList(criterias)));
    }

    public SearchQuery() {
        this(new ArrayList<>());
    }

    private SearchQuery(List<Criterion> criterias) {
        this.criterias = criterias;
    }

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
        if (sorts == null || sorts.isEmpty()) {
            throw new IllegalArgumentException("There must be at least one Sort");
        }
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
     * @return mutable <code>Set</code> of <code>MessageUid</code> UIDS
     */
    public Set<MessageUid> getRecentMessageUids() {
        return recentMessageUids;
    }

    /**
     * Adds all the uids to the collection of recents.
     * 
     * @param uids
     *            not null
     */
    public void addRecentMessageUids(Collection<MessageUid> uids) {
        recentMessageUids.addAll(uids);
    }

    @Override
    public String toString() {
        return "Search:" + criterias.toString();
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(criterias);
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof SearchQuery) {
            SearchQuery that = (SearchQuery) obj;

            return Objects.equal(this.criterias, that.criterias);
        }
        return false;
    }

    /**
     * Numbers within a particular range. Range includes both high and low
     * boundaries. May be a single value. {@link Long#MAX_VALUE} represents
     * unlimited in either direction.
     */
    public static class NumericRange implements Serializable {
        private static final long serialVersionUID = 1L;

        private final long lowValue;

        private final long highValue;

        public NumericRange(long value) {
            super();
            this.lowValue = value;
            this.highValue = value;
        }

        public NumericRange(long lowValue, long highValue) {
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

        @Override
        public int hashCode() {
            return Objects.hashCode(lowValue, highValue);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof NumericRange) {
                NumericRange that = (NumericRange) obj;
                return Objects.equal(this.lowValue, that.lowValue)
                    && Objects.equal(this.highValue, that.highValue);
            }
            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("lowValue", lowValue)
                .add("highValue", highValue)
                .toString();
        }

    }

    /**
     * Numbers within a particular range. Range includes both high and low
     * boundaries. May be a single value. {@link Long#MAX_VALUE} represents
     * unlimited in either direction.
     */
    public static class UidRange implements Serializable {

        private final MessageUid lowValue;
        private final MessageUid highValue;

        public UidRange(MessageUid value) {
            super();
            this.lowValue = value;
            this.highValue = value;
        }

        public UidRange(MessageUid lowValue, MessageUid highValue) {
            super();
            this.lowValue = lowValue;
            this.highValue = highValue;
        }

        public MessageUid getHighValue() {
            return highValue;
        }

        public MessageUid getLowValue() {
            return lowValue;
        }

        /**
         * Is the given value in this range?
         * 
         * @param value
         *            value to be tested
         * @return true if the value is in range, false otherwise
         */
        public boolean isIn(MessageUid value) {
            return lowValue.compareTo(value) <= 0 && highValue.compareTo(value) >= 0;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(lowValue, highValue);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof UidRange) {
                UidRange that = (UidRange) obj;
                return Objects.equal(this.lowValue, that.lowValue)
                    && Objects.equal(this.highValue, that.highValue);
            }
            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("lowValue", lowValue)
                .add("highValue", highValue)
                .toString();
        }

    }
    
    /**
     * Marker superclass for criteria.
     */
    public abstract static class Criterion implements Serializable {
        private static final long serialVersionUID = 1L;

    }

    public enum Conjunction {
        AND, OR, NOR
    }

    /**
     * Conjunction applying to the contained criteria. {@link #getType}
     * indicates how the conjoined criteria should be related.
     */
    public static class ConjunctionCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        private final Conjunction type;

        private final List<Criterion> criteria;

        public ConjunctionCriterion(Conjunction type, List<Criterion> criteria) {
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

        @Override
        public int hashCode() {
            return Objects.hashCode(criteria);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ConjunctionCriterion) {
                ConjunctionCriterion that = (ConjunctionCriterion) obj;

                return Objects.equal(this.criteria, that.criteria);
            }
            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("criteria", criteria)
                .add("type", type)
                .toString();
        }

    }

    /**
     * Any message.
     */
    public static class AllCriterion extends Criterion {
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

        @Override
        public String toString() {
            return "AllCriterion";
        }
    }

    public enum Scope {
        /** Only message body content */
        BODY,

        /** Headers: From, To, Cc, Bcc & Subjects
         *  plus text/plain & text/html part
         */
        TEXT,

        /** Full message content including headers and attachments */
        FULL,
        /** Attachment content */
        ATTACHMENTS
    }

    /**
     * Message text.
     */
    public static class TextCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        private final Scope type;

        private final ContainsOperator operator;

        private TextCriterion(String value, Scope type) {
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

        @Override
        public int hashCode() {
            return Objects.hashCode(operator);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof TextCriterion) {
                TextCriterion that = (TextCriterion) obj;

                return Objects.equal(this.operator, that.operator);
            }
            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("operator", operator)
                .toString();
        }
    }

    /**
     * Header value content search.
     */
    public static class HeaderCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        private final HeaderOperator operator;

        private final String headerName;

        private HeaderCriterion(String headerName, HeaderOperator operator) {
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

        @Override
        public int hashCode() {
            return Objects.hashCode(headerName, operator);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof HeaderCriterion) {
                HeaderCriterion that = (HeaderCriterion) obj;

                return Objects.equal(this.operator, that.operator)
                    && Objects.equal(this.headerName, that.headerName);
            }
            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("operator", operator)
                .add("headerName", headerName)
                .toString();
        }

    }

    /**
     * Filters on the internal date.
     */
    public static class InternalDateCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        private final DateOperator operator;

        public InternalDateCriterion(DateOperator operator) {
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

        @Override
        public int hashCode() {
            return Objects.hashCode(operator);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof InternalDateCriterion) {
                InternalDateCriterion that = (InternalDateCriterion) obj;

                return Objects.equal(this.operator, that.operator);
            }
            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("operator", operator)
                .toString();
        }
    }

    /**
     * Filters on the mod-sequence of the messages.
     */
    public static class ModSeqCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        private final NumericOperator operator;

        private ModSeqCriterion(NumericOperator operator) {
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

        @Override
        public int hashCode() {
            return Objects.hashCode(operator);
        }


        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ModSeqCriterion) {
                ModSeqCriterion that = (ModSeqCriterion) obj;

                return Objects.equal(this.operator, that.operator);
            }
            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("operator", operator)
                .toString();
        }
    }

    public static class SizeCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        private final NumericOperator operator;

        private SizeCriterion(NumericOperator operator) {
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

        @Override
        public int hashCode() {
            return Objects.hashCode(operator);
        }


        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SizeCriterion) {
                SizeCriterion that = (SizeCriterion) obj;

                return Objects.equal(this.operator, that.operator);
            }
            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("operator", operator)
                .toString();
        }
    }

    /**
     * Filters on a custom flag valuation.
     */
    public static class CustomFlagCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        private final String flag;

        private final BooleanOperator operator;

        private CustomFlagCriterion(String flag, BooleanOperator operator) {
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

        @Override
        public int hashCode() {
            return Objects.hashCode(flag, operator);
        }


        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CustomFlagCriterion) {
                CustomFlagCriterion that = (CustomFlagCriterion) obj;

                return Objects.equal(this.operator, that.operator)
                    && Objects.equal(this.flag, that.flag);
            }
            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("operator", operator)
                .add("flag", flag)
                .toString();
        }
    }

    /***
     * Filter on attachment presence
     */
    public static class AttachmentCriterion extends Criterion {
        private final BooleanOperator operator;

        private AttachmentCriterion(BooleanOperator operator) {
            this.operator = operator;
        }

        /**
         * Gets the test to be preformed.
         *
         * @return the <code>BooleanOperator</code>, not null
         */
        public BooleanOperator getOperator() {
            return operator;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(operator);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof AttachmentCriterion) {
                AttachmentCriterion that = (AttachmentCriterion) obj;

                return Objects.equal(this.operator, that.operator);
            }

            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("operator", operator)
                .toString();
        }
    }


    /**
     * Filters on a standard flag.
     */
    public static class FlagCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        // Flags not Flag because Flags are serializable and Flag is not
        private final Flags flag;        
        private final BooleanOperator operator;

        private FlagCriterion(Flag flag, BooleanOperator operator) {
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

        @Override
        public int hashCode() {
            return Objects.hashCode(flag, operator);
        }


        @Override
        public boolean equals(Object obj) {
            if (obj instanceof FlagCriterion) {
                FlagCriterion that = (FlagCriterion) obj;

                return Objects.equal(this.operator, that.operator)
                    && Objects.equal(this.flag, that.flag);
            }
            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("operator", operator)
                .add("flag", flag)
                .toString();
        }
    }

    /**
     * Filters on message identity.
     */
    public static class UidCriterion extends Criterion {
        private static final long serialVersionUID = 1L;

        private final UidInOperator operator;

        public UidCriterion(UidRange[] ranges) {
            super();
            this.operator = new UidInOperator(ranges);
        }

        /**
         * Gets the filtering operation.
         */
        public UidInOperator getOperator() {
            return operator;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(operator);
        }


        @Override
        public boolean equals(Object obj) {
            if (obj instanceof UidCriterion) {
                UidCriterion that = (UidCriterion) obj;

                return Objects.equal(this.operator, that.operator);
            }
            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("operator", operator)
                .toString();
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

    public static class AddressOperator implements HeaderOperator {
        private static final long serialVersionUID = 1L;

        private final String address;

        public AddressOperator(String address) {
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

        @Override
        public int hashCode() {
            return Objects.hashCode(address);
        }


        @Override
        public boolean equals(Object obj) {
            if (obj instanceof AddressOperator) {
                AddressOperator that = (AddressOperator) obj;

                return Objects.equal(this.address, that.address);
            }
            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("address", address)
                .toString();
        }
    }

    /**
     * Contained value search.
     */
    public static class ContainsOperator implements HeaderOperator {
        private static final long serialVersionUID = 1L;

        private final String value;

        public ContainsOperator(String value) {
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

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }


        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ContainsOperator) {
                ContainsOperator that = (ContainsOperator) obj;

                return Objects.equal(this.value, that.value);
            }
            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("value", value)
                .toString();
        }
    }

    /**
     * Existance search.
     */
    public static class ExistsOperator implements HeaderOperator {
        private static final long serialVersionUID = 1L;

        private static final ExistsOperator EXISTS = new ExistsOperator();

        public static ExistsOperator exists() {
            return EXISTS;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ExistsOperator;
        }

        @Override
        public int hashCode() {
            return 42;
        }

        @Override
        public String toString() {
            return "ExistsCriterion";
        }

    }

    /**
     * Boolean value search.
     */
    public static class BooleanOperator implements Operator {
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

        private BooleanOperator(boolean set) {
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

        @Override
        public int hashCode() {
            return Objects.hashCode(set);
        }


        @Override
        public boolean equals(Object obj) {
            if (obj instanceof BooleanOperator) {
                BooleanOperator that = (BooleanOperator) obj;

                return Objects.equal(this.set, that.set);
            }
            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("set", set)
                .toString();
        }

    }

    public enum NumericComparator {
        EQUALS, LESS_THAN, GREATER_THAN
    }

    /**
     * Searches numeric values.
     */
    public static class NumericOperator implements Operator {
        private static final long serialVersionUID = 1L;

        private final long value;

        private final NumericComparator type;

        private NumericOperator(long value, NumericComparator type) {
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

        @Override
        public int hashCode() {
            return Objects.hashCode(value, type);
        }


        @Override
        public boolean equals(Object obj) {
            if (obj instanceof NumericOperator) {
                NumericOperator that = (NumericOperator) obj;

                return Objects.equal(this.value, that.value)
                    && Objects.equal(this.type, that.type);
            }
            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("value", value)
                .add("type", type)
                .toString();
        }
    }

    public enum DateComparator {
        BEFORE, AFTER, ON
    }

    /**
     * Operates on a date.
     */
    public static class DateOperator implements HeaderOperator {
        private static final long serialVersionUID = 1L;

        public static final int BEFORE = 1;

        public static final int AFTER = 2;

        public static final int ON = 3;

        private final DateComparator type;

        private final Date date;

        private final DateResolution dateResolution;

        public DateOperator(DateComparator type, Date date, DateResolution dateResolution) {
            Preconditions.checkNotNull(date);
            Preconditions.checkNotNull(dateResolution);
            this.type = type;
            this.date = date;
            this.dateResolution = dateResolution;
        }

        public Date getDate() {
            return date;
        }

        public DateResolution getDateResultion() {
            return dateResolution;
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

        @Override
        public int hashCode() {
            return Objects.hashCode(dateResolution, type);
        }


        @Override
        public boolean equals(Object obj) {
            if (obj instanceof DateOperator) {
                DateOperator that = (DateOperator) obj;

                return Objects.equal(this.dateResolution, that.dateResolution)
                    && Objects.equal(this.type, that.type);
            }
            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("date", date)
                .add("dateResolution", dateResolution)
                .add("type", type)
                .toString();
        }

    }

    /**
     * Search for numbers within set of ranges.
     */
    public static class InOperator implements Operator {
        private static final long serialVersionUID = 1L;

        private final NumericRange[] range;

        public InOperator(NumericRange[] range) {
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

        @Override
        public int hashCode() {
            return range.length;
        }


        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final InOperator other = (InOperator) obj;
            if (!Arrays.equals(range, other.range)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("range", Arrays.toString(range))
                .toString();
        }

    }
    
    /**
     * Search for uids within set of ranges.
     */
    public static class UidInOperator implements Operator {
        private static final long serialVersionUID = 1L;

        private final UidRange[] ranges;

        public UidInOperator(UidRange[] ranges) {
            super();
            this.ranges = ranges;
        }

        /**
         * Gets the filtering ranges. Values falling within these ranges will be
         * selected.
         * 
         * @return the <code>UidRange</code>'s search on, not null
         */
        public UidRange[] getRange() {
            return ranges;
        }

        
        @Override
        public int hashCode() {
            return Arrays.hashCode(ranges);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof UidInOperator) {
                UidInOperator other = (UidInOperator) obj;
                return Arrays.equals(this.ranges, other.ranges);
            }
            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("ranges", Arrays.toString(ranges))
                .toString();
        }

    }
}
