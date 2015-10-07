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

package org.apache.james.mailbox.store.search;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import javax.mail.Flags;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedSearchException;
import org.apache.james.mailbox.model.MessageResult.Header;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.mailbox.model.SearchQuery.NumericRange;
import org.apache.james.mailbox.store.ResultUtils;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.search.comparator.CombinedComparator;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Group;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.dom.datetime.DateTime;
import org.apache.james.mime4j.field.address.AddressFormatter;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.mime4j.field.datetime.parser.DateTimeParser;
import org.apache.james.mime4j.field.datetime.parser.ParseException;
import org.slf4j.Logger;

/**
 * Utility methods to help perform search operations.
 */
public class MessageSearches implements Iterable<Long> {

    private Iterator<Message<?>> messages;
    private SearchQuery query;
    private Logger log;

    public MessageSearches(Iterator<Message<?>> messages, SearchQuery query) {
        this(messages, query, null);
    }

    public MessageSearches(Iterator<Message<?>> messages, SearchQuery query, Logger log) {
        this.messages = messages;
        this.query = query;
        this.log = log;
    }

    /**
     * Empty constructor only for tests (which test isMatch())
     */
    public MessageSearches() {
    }

    private Collection<Long> search() {
        TreeSet<Message<?>> matched = new TreeSet<Message<?>>(CombinedComparator.create(query.getSorts()));
        while (messages.hasNext()) {
            Message<?> m = messages.next();
            try {
                if (isMatch(query, m, log)) {
                    matched.add(m);
                }
            } catch (MailboxException e) {
                log.debug("Unable to search message " + m.getUid(), e);
            }
        }
        Set<Long> uids = new HashSet<Long>();
        Iterator<Message<?>> matchedIt = matched.iterator();
        while (matchedIt.hasNext()) {
            uids.add(matchedIt.next().getUid());
        }
        return uids;
    }

    /**
     * Does the row match the given criteria?
     * 
     * @param query
     *            <code>SearchQuery</code>, not null
     * @param message
     *            <code>Message</code>, not null
     * @param log
     *            the logger to use
     * @return <code>true</code> if the row matches the given criteria,
     *         <code>false</code> otherwise
     * @throws MailboxException
     */
    protected boolean isMatch(final SearchQuery query, final Message<?> message, Logger log) throws MailboxException {
        final List<SearchQuery.Criterion> criteria = query.getCriterias();
        final Collection<Long> recentMessageUids = query.getRecentMessageUids();
        boolean result = true;
        if (criteria != null) {
            for (Iterator<SearchQuery.Criterion> it = criteria.iterator(); it.hasNext();) {
                final SearchQuery.Criterion criterion = it.next();
                if (!isMatch(criterion, message, recentMessageUids, log)) {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Does the row match the given criterion?
     * 
     * @param criterion
     *            the criterion to use
     * @param message
     *            <code>Message</code>, not null
     * @param recentMessageUids
     *            collection of recent message uids
     * @param log
     *            the logger to use
     * @return <code>true</code> if the row matches the given criterion,
     *         <code>false</code> otherwise
     * @throws MailboxException
     */
    public boolean isMatch(SearchQuery.Criterion criterion, Message<?> message,
            final Collection<Long> recentMessageUids, Logger log) throws MailboxException {
        final boolean result;
        if (criterion instanceof SearchQuery.InternalDateCriterion) {
            result = matches((SearchQuery.InternalDateCriterion) criterion, message);
        } else if (criterion instanceof SearchQuery.SizeCriterion) {
            result = matches((SearchQuery.SizeCriterion) criterion, message);
        } else if (criterion instanceof SearchQuery.HeaderCriterion) {
            try {
                result = matches((SearchQuery.HeaderCriterion) criterion, message, log);
            } catch (IOException e) {
                throw new MailboxException("Unable to search header", e);
            }
        } else if (criterion instanceof SearchQuery.UidCriterion) {
            result = matches((SearchQuery.UidCriterion) criterion, message);
        } else if (criterion instanceof SearchQuery.FlagCriterion) {
            result = matches((SearchQuery.FlagCriterion) criterion, message, recentMessageUids);
        } else if (criterion instanceof SearchQuery.CustomFlagCriterion) {
            result = matches((SearchQuery.CustomFlagCriterion) criterion, message, recentMessageUids);
        } else if (criterion instanceof SearchQuery.TextCriterion) {
            result = matches((SearchQuery.TextCriterion) criterion, message, log);
        } else if (criterion instanceof SearchQuery.AllCriterion) {
            result = true;
        } else if (criterion instanceof SearchQuery.ConjunctionCriterion) {
            result = matches((SearchQuery.ConjunctionCriterion) criterion, message, recentMessageUids, log);
        } else if (criterion instanceof SearchQuery.ModSeqCriterion) {
            result = matches((SearchQuery.ModSeqCriterion) criterion, message);
        } else {
            throw new UnsupportedSearchException();
        }
        return result;
    }

    protected boolean matches(SearchQuery.TextCriterion criterion, Message<?> message, Logger log)
            throws MailboxException {
        try {
            final SearchQuery.ContainsOperator operator = criterion.getOperator();
            final String value = operator.getValue();
            switch (criterion.getType()) {
            case BODY:
                return bodyContains(value, message, log);
            case FULL:
                return messageContains(value, message, log);
            default:
                throw new UnsupportedSearchException();
            }
        } catch (IOException e) {
            throw new MailboxException("Unable to parse message", e);
        } catch (MimeException e) {
            throw new MailboxException("Unable to parse message", e);
        }
    }

    protected boolean bodyContains(String value, Message<?> message, Logger log) throws IOException, MimeException {
        final InputStream input = message.getFullContent();
        final boolean result = isInMessage(value, input, false, log);
        return result;
    }

    protected boolean isInMessage(String value, final InputStream input, boolean header, Logger log)
            throws IOException, MimeException {
        final MessageSearcher searcher = new MessageSearcher(value, true, header);
        if (log != null) {
            searcher.setLogger(log);
        }
        final boolean result = searcher.isFoundIn(input);
        return result;
    }

    protected boolean messageContains(String value, Message<?> message, Logger log) throws IOException, MimeException {
        final InputStream input = message.getFullContent();
        final boolean result = isInMessage(value, input, true, log);
        return result;
    }

    private boolean matches(SearchQuery.ConjunctionCriterion criterion, Message<?> message,
            final Collection<Long> recentMessageUids, Logger log) throws MailboxException {
        final List<SearchQuery.Criterion> criteria = criterion.getCriteria();
        switch (criterion.getType()) {
        case NOR:
            return nor(criteria, message, recentMessageUids, log);
        case OR:
            return or(criteria, message, recentMessageUids, log);
        case AND:
            return and(criteria, message, recentMessageUids, log);
        default:
            return false;
        }
    }

    private boolean and(final List<SearchQuery.Criterion> criteria, final Message<?> message,
            final Collection<Long> recentMessageUids, Logger log) throws MailboxException {
        boolean result = true;
        for (Iterator<SearchQuery.Criterion> it = criteria.iterator(); it.hasNext();) {
            final SearchQuery.Criterion criterion = it.next();
            final boolean matches = isMatch(criterion, message, recentMessageUids, log);
            if (!matches) {
                result = false;
                break;
            }
        }
        return result;
    }

    private boolean or(final List<SearchQuery.Criterion> criteria, final Message<?> message,
            final Collection<Long> recentMessageUids, Logger log) throws MailboxException {
        boolean result = false;
        for (Iterator<SearchQuery.Criterion> it = criteria.iterator(); it.hasNext();) {
            final SearchQuery.Criterion criterion = it.next();
            final boolean matches = isMatch(criterion, message, recentMessageUids, log);
            if (matches) {
                result = true;
                break;
            }
        }
        return result;
    }

    private boolean nor(final List<SearchQuery.Criterion> criteria, final Message<?> message,
            final Collection<Long> recentMessageUids, Logger log) throws MailboxException {
        boolean result = true;
        for (Iterator<SearchQuery.Criterion> it = criteria.iterator(); it.hasNext();) {
            final SearchQuery.Criterion criterion = it.next();
            final boolean matches = isMatch(criterion, message, recentMessageUids, log);
            if (matches) {
                result = false;
                break;
            }
        }
        return result;
    }

    private boolean matches(SearchQuery.FlagCriterion criterion, Message<?> message,
            final Collection<Long> recentMessageUids) {
        final SearchQuery.BooleanOperator operator = criterion.getOperator();
        final boolean isSet = operator.isSet();
        final Flags.Flag flag = criterion.getFlag();
        final boolean result;
        if (flag == Flags.Flag.ANSWERED) {
            result = isSet == message.isAnswered();
        } else if (flag == Flags.Flag.SEEN) {
            result = isSet == message.isSeen();
        } else if (flag == Flags.Flag.DRAFT) {
            result = isSet == message.isDraft();
        } else if (flag == Flags.Flag.FLAGGED) {
            result = isSet == message.isFlagged();
        } else if (flag == Flags.Flag.RECENT) {
            final long uid = message.getUid();
            result = isSet == recentMessageUids.contains(Long.valueOf(uid));
        } else if (flag == Flags.Flag.DELETED) {
            result = isSet == message.isDeleted();
        } else {
            result = false;
        }
        return result;
    }

    private boolean matches(SearchQuery.CustomFlagCriterion criterion, Message<?> message,
            final Collection<Long> recentMessageUids) {
        final SearchQuery.BooleanOperator operator = criterion.getOperator();
        final boolean isSet = operator.isSet();
        final String flag = criterion.getFlag();
        final boolean result = isSet == message.createFlags().contains(flag);
        return result;
    }

    private boolean matches(SearchQuery.UidCriterion criterion, Message<?> message) {
        final SearchQuery.InOperator operator = criterion.getOperator();
        final NumericRange[] ranges = operator.getRange();
        final long uid = message.getUid();
        final int length = ranges.length;
        boolean result = false;
        for (int i = 0; i < length; i++) {
            final NumericRange numericRange = ranges[i];
            if (numericRange.isIn(uid)) {
                result = true;
                break;
            }
        }
        return result;
    }

    private boolean matches(SearchQuery.HeaderCriterion criterion, Message<?> message, Logger log)
            throws MailboxException, IOException {
        final SearchQuery.HeaderOperator operator = criterion.getOperator();
        final String headerName = criterion.getHeaderName();
        final boolean result;
        if (operator instanceof SearchQuery.DateOperator) {
            result = matches((SearchQuery.DateOperator) operator, headerName, message);
        } else if (operator instanceof SearchQuery.ContainsOperator) {
            result = matches((SearchQuery.ContainsOperator) operator, headerName, message);
        } else if (operator instanceof SearchQuery.ExistsOperator) {
            result = exists(headerName, message);
        } else if (operator instanceof SearchQuery.AddressOperator) {
            result = matchesAddress((SearchQuery.AddressOperator) operator, headerName, message, log);
        } else {
            throw new UnsupportedSearchException();
        }
        return result;
    }

    /**
     * Match against a {@link AddressType} header
     * 
     * @param operator
     * @param headerName
     * @param message
     * @return containsAddress
     * @throws MailboxException
     * @throws IOException
     */
    private boolean matchesAddress(final SearchQuery.AddressOperator operator, final String headerName,
            final Message<?> message, Logger log) throws MailboxException, IOException {
        final String text = operator.getAddress().toUpperCase(Locale.ENGLISH);
        final List<Header> headers = ResultUtils.createHeaders(message);
        for (Header header : headers) {
            final String name = header.getName();
            if (headerName.equalsIgnoreCase(name)) {
                final String value = header.getValue();
                AddressList aList = LenientAddressParser.DEFAULT.parseAddressList(value);
                for (int i = 0; i < aList.size(); i++) {
                    Address address = aList.get(i);
                    if (address instanceof Mailbox) {
                        if (AddressFormatter.DEFAULT.encode((Mailbox) address).toUpperCase(Locale.ENGLISH)
                                .contains(text)) {
                            return true;
                        }
                    } else if (address instanceof Group) {
                        MailboxList mList = ((Group) address).getMailboxes();
                        for (int a = 0; a < mList.size(); a++) {
                            if (AddressFormatter.DEFAULT.encode(mList.get(a)).toUpperCase(Locale.ENGLISH)
                                    .contains(text)) {
                                return true;
                            }
                        }
                    }
                }

                // Also try to match against raw header now
                return value.toUpperCase(Locale.ENGLISH).contains(text);
            }
        }
        return false;
    }

    private boolean exists(String headerName, Message<?> message) throws MailboxException, IOException {
        boolean result = false;
        final List<Header> headers = ResultUtils.createHeaders(message);

        for (Header header : headers) {
            final String name = header.getName();
            if (headerName.equalsIgnoreCase(name)) {
                result = true;
                break;
            }
        }
        return result;
    }

    private boolean matches(final SearchQuery.ContainsOperator operator, final String headerName,
            final Message<?> message) throws MailboxException, IOException {
        final String text = operator.getValue().toUpperCase();
        boolean result = false;
        final List<Header> headers = ResultUtils.createHeaders(message);
        for (Header header : headers) {
            final String name = header.getName();
            if (headerName.equalsIgnoreCase(name)) {
                final String value = header.getValue();
                if (value != null) {
                    if (value.toUpperCase().indexOf(text) > -1) {
                        result = true;
                        break;
                    }
                }
            }
        }
        return result;
    }

    private boolean matches(final SearchQuery.DateOperator operator, final String headerName, final Message<?> message)
            throws MailboxException {

        final Date date = operator.getDate();
        final DateResolution res = operator.getDateResultion();
        try {
            final String value = headerValue(headerName, message);
            if (value == null) {
                return false;
            } else {
                try {
                    final Date isoFieldValue = toISODate(value);
                    final SearchQuery.DateComparator type = operator.getType();
                    switch (type) {
                    case AFTER:
                        return after(isoFieldValue, date, res);
                    case BEFORE:
                        return before(isoFieldValue, date, res);
                    case ON:
                        return on(isoFieldValue, date, res);
                    default:
                        throw new UnsupportedSearchException();
                    }
                } catch (ParseException e) {
                    return false;
                }
            }
        } catch (IOException e) {
            return false;
        }
    }

    private String headerValue(final String headerName, final Message<?> message) throws MailboxException, IOException {
        final List<Header> headers = ResultUtils.createHeaders(message);
        String value = null;
        for (Header header : headers) {
            final String name = header.getName();
            if (headerName.equalsIgnoreCase(name)) {
                value = header.getValue();
                break;
            }
        }
        return value;
    }

    private Date toISODate(String value) throws ParseException {
        final StringReader reader = new StringReader(value);
        final DateTime dateTime = new DateTimeParser(reader).parseAll();
        Calendar cal = getGMT();
        cal.set(dateTime.getYear(), dateTime.getMonth() - 1, dateTime.getDay(), dateTime.getHour(),
                dateTime.getMinute(), dateTime.getSecond());
        return cal.getTime();
    }

    private boolean matches(SearchQuery.SizeCriterion criterion, Message<?> message) throws UnsupportedSearchException {
        final SearchQuery.NumericOperator operator = criterion.getOperator();
        final long size = message.getFullContentOctets();
        final long value = operator.getValue();
        switch (operator.getType()) {
        case LESS_THAN:
            return size < value;
        case GREATER_THAN:
            return size > value;
        case EQUALS:
            return size == value;
        default:
            throw new UnsupportedSearchException();
        }
    }

    private boolean matches(SearchQuery.ModSeqCriterion criterion, Message<?> message)
            throws UnsupportedSearchException {
        final SearchQuery.NumericOperator operator = criterion.getOperator();
        final long modSeq = message.getModSeq();
        final long value = operator.getValue();
        switch (operator.getType()) {
        case LESS_THAN:
            return modSeq < value;
        case GREATER_THAN:
            return modSeq > value;
        case EQUALS:
            return modSeq == value;
        default:
            throw new UnsupportedSearchException();
        }
    }

    private boolean matches(SearchQuery.InternalDateCriterion criterion, Message<?> message)
            throws UnsupportedSearchException {
        final SearchQuery.DateOperator operator = criterion.getOperator();
        final boolean result = matchesInternalDate(operator, message);
        return result;
    }

    private boolean matchesInternalDate(final SearchQuery.DateOperator operator, final Message<?> message)
            throws UnsupportedSearchException {
        final Date date = operator.getDate();
        final DateResolution res = operator.getDateResultion();
        final Date internalDate = message.getInternalDate();
        final SearchQuery.DateComparator type = operator.getType();
        switch (type) {
        case ON:
            return on(internalDate, date, res);
        case BEFORE:
            return before(internalDate, date, res);
        case AFTER:
            return after(internalDate, date, res);
        default:
            throw new UnsupportedSearchException();
        }
    }

    private boolean on(Date date1, final Date date2, DateResolution res) {
        String d1 = createDateString(date1, res);
        String d2 = createDateString(date2, res);
        return d1.compareTo(d2) == 0;
    }

    private boolean before(Date date1, final Date date2, DateResolution res) {
        String d1 = createDateString(date1, res);
        String d2 = createDateString(date2, res);

        return d1.compareTo(d2) < 0;
    }

    private boolean after(Date date1, final Date date2, DateResolution res) {
        String d1 = createDateString(date1, res);
        String d2 = createDateString(date2, res);

        return d1.compareTo(d2) > 0;
    }

    private String createDateString(Date date, DateResolution res) {
        SimpleDateFormat format;
        switch (res) {
        case Year:
            format = new SimpleDateFormat("yyyy");
            break;
        case Month:
            format = new SimpleDateFormat("yyyyMM");
            break;
        case Day:
            format = new SimpleDateFormat("yyyyMMdd");
            break;
        case Hour:
            format = new SimpleDateFormat("yyyyMMddhh");
            break;
        case Minute:
            format = new SimpleDateFormat("yyyyMMddhhmm");
            break;
        case Second:
            format = new SimpleDateFormat("yyyyMMddhhmmss");
            break;
        default:
            format = new SimpleDateFormat("yyyyMMddhhmmssSSS");

            break;
        }
        format.setCalendar(getGMT());
        return format.format(date);
    }

    private Calendar getGMT() {
        return Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.ENGLISH);
    }

    /**
     * Return a {@link Iterator} which holds all uids which matched, sorted
     * according to the SearchQuery
     * 
     */
    public Iterator<Long> iterator() {
        return search().iterator();
    }

}
