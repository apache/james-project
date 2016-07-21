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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
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

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedSearchException;
import org.apache.james.mailbox.model.MessageResult.Header;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.mailbox.model.SearchQuery.NumericRange;
import org.apache.james.mailbox.store.ResultUtils;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.comparator.CombinedComparator;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.MimeIOException;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Group;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.dom.datetime.DateTime;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.mime4j.field.address.AddressFormatter;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.mime4j.field.datetime.parser.DateTimeParser;
import org.apache.james.mime4j.field.datetime.parser.ParseException;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.message.HeaderImpl;
import org.apache.james.mime4j.utils.search.MessageMatcher;

import com.google.common.collect.Lists;

/**
 * Utility methods to help perform search operations.
 */
public class MessageSearches implements Iterable<Long> {

    private Iterator<MailboxMessage> messages;
    private SearchQuery query;
    private MailboxSession session;

    public MessageSearches(Iterator<MailboxMessage> messages, SearchQuery query, MailboxSession session) {
        this.messages = messages;
        this.query = query;
        this.session = session;
    }

    /**
     * Empty constructor only for tests (which test isMatch())
     */
    public MessageSearches() {
    }

    private Collection<Long> search() {
        TreeSet<MailboxMessage> matched = new TreeSet<MailboxMessage>(CombinedComparator.create(query.getSorts()));
        while (messages.hasNext()) {
            MailboxMessage m = messages.next();
            try {
                if (isMatch(query, m)) {
                    matched.add(m);
                }
            } catch (MailboxException e) {
                if (session != null && session.getLog() != null) {
                    session.getLog().debug("Unable to search message " + m.getUid(), e);
                }
            }
        }
        Set<Long> uids = new HashSet<Long>();
        Iterator<MailboxMessage> matchedIt = matched.iterator();
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
     *            <code>MailboxMessage</code>, not null
     * @return <code>true</code> if the row matches the given criteria,
     *         <code>false</code> otherwise
     * @throws MailboxException
     */
    private boolean isMatch(SearchQuery query, MailboxMessage message) throws MailboxException {
        final List<SearchQuery.Criterion> criteria = query.getCriterias();
        final Collection<Long> recentMessageUids = query.getRecentMessageUids();
        boolean result = true;
        if (criteria != null) {
            for (SearchQuery.Criterion criterion : criteria) {
                if (!isMatch(criterion, message, recentMessageUids)) {
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
     *            <code>MailboxMessage</code>, not null
     * @param recentMessageUids
     *            collection of recent message uids
     * @return <code>true</code> if the row matches the given criterion,
     *         <code>false</code> otherwise
     * @throws MailboxException
     */
    public boolean isMatch(SearchQuery.Criterion criterion, MailboxMessage message,
            final Collection<Long> recentMessageUids) throws MailboxException {
        final boolean result;
        if (criterion instanceof SearchQuery.InternalDateCriterion) {
            result = matches((SearchQuery.InternalDateCriterion) criterion, message);
        } else if (criterion instanceof SearchQuery.SizeCriterion) {
            result = matches((SearchQuery.SizeCriterion) criterion, message);
        } else if (criterion instanceof SearchQuery.HeaderCriterion) {
            try {
                result = matches((SearchQuery.HeaderCriterion) criterion, message);
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
            result = matches((SearchQuery.TextCriterion) criterion, message);
        } else if (criterion instanceof SearchQuery.AllCriterion) {
            result = true;
        } else if (criterion instanceof SearchQuery.ConjunctionCriterion) {
            result = matches((SearchQuery.ConjunctionCriterion) criterion, message, recentMessageUids);
        } else if (criterion instanceof SearchQuery.ModSeqCriterion) {
            result = matches((SearchQuery.ModSeqCriterion) criterion, message);
        } else {
            throw new UnsupportedSearchException();
        }
        return result;
    }

    private boolean matches(SearchQuery.TextCriterion criterion, MailboxMessage message)
            throws MailboxException {
        try {
            final SearchQuery.ContainsOperator operator = criterion.getOperator();
            final String value = operator.getValue();
            switch (criterion.getType()) {
            case BODY:
                return bodyContains(value, message);
            case TEXT:
                return textContains(value, message);
            case FULL:
                return messageContains(value, message);
            }
            throw new UnsupportedSearchException();
        } catch (IOException e) {
            throw new MailboxException("Unable to parse message", e);
        } catch (MimeException e) {
            throw new MailboxException("Unable to parse message", e);
        }
    }

    private boolean bodyContains(String value, MailboxMessage message) throws IOException, MimeException {
        final InputStream input = message.getFullContent();
        return isInMessage(value, input, false);
    }

    private boolean isInMessage(String value, InputStream input, boolean header) throws IOException, MimeException {
        MessageMatcher.MessageMatcherBuilder builder = MessageMatcher.builder()
            .searchContents(Lists.<CharSequence>newArrayList(value))
            .caseInsensitive(true)
            .includeHeaders(header);
        if (session != null && session.getLog() != null) {
            builder.logger(session.getLog());
        }
        return builder.build().messageMatches(input);
    }

    private boolean messageContains(String value, MailboxMessage message) throws IOException, MimeException {
        final InputStream input = message.getFullContent();
        return isInMessage(value, input, true);
    }

    private boolean textContains(String value, MailboxMessage message) throws IOException, MimeException, MailboxException {
        InputStream bodyContent = message.getBodyContent();
        return isInMessage(value, new SequenceInputStream(textHeaders(message), bodyContent), true);
    }

    private InputStream textHeaders(MailboxMessage message) throws MimeIOException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new DefaultMessageWriter()
            .writeHeader(buildTextHeaders(message), out);
        return new ByteArrayInputStream(out.toByteArray());
    }

    private HeaderImpl buildTextHeaders(MailboxMessage message) throws IOException, MimeIOException {
        Message headersMessage = new DefaultMessageBuilder()
            .parseMessage(message.getHeaderContent());
        HeaderImpl headerImpl = new HeaderImpl();
        addFrom(headerImpl, headersMessage.getFrom());
        addAddressList(headerImpl, headersMessage.getTo());
        addAddressList(headerImpl, headersMessage.getCc());
        addAddressList(headerImpl, headersMessage.getBcc());
        headerImpl.addField(Fields.subject(headersMessage.getSubject()));
        return headerImpl;
    }

    private void addFrom(HeaderImpl headerImpl, MailboxList from) {
        if (from != null) {
            headerImpl.addField(Fields.from(Lists.newArrayList(from.iterator())));
        }
    }

    private void addAddressList(HeaderImpl headerImpl, AddressList addressList) {
        if (addressList != null) {
            headerImpl.addField(Fields.to(Lists.newArrayList(addressList.iterator())));
        }
    }
    private boolean matches(SearchQuery.ConjunctionCriterion criterion, MailboxMessage message,
            final Collection<Long> recentMessageUids) throws MailboxException {
        final List<SearchQuery.Criterion> criteria = criterion.getCriteria();
        switch (criterion.getType()) {
        case NOR:
            return nor(criteria, message, recentMessageUids);
        case OR:
            return or(criteria, message, recentMessageUids);
        case AND:
            return and(criteria, message, recentMessageUids);
        default:
            return false;
        }
    }

    private boolean and(List<SearchQuery.Criterion> criteria, MailboxMessage message,
            final Collection<Long> recentMessageUids) throws MailboxException {
        boolean result = true;
        for (SearchQuery.Criterion criterion : criteria) {
            boolean matches = isMatch(criterion, message, recentMessageUids);
            if (!matches) {
                result = false;
                break;
            }
        }
        return result;
    }

    private boolean or(List<SearchQuery.Criterion> criteria, MailboxMessage message,
            final Collection<Long> recentMessageUids) throws MailboxException {
        boolean result = false;
        for (SearchQuery.Criterion criterion : criteria) {
            boolean matches = isMatch(criterion, message, recentMessageUids);
            if (matches) {
                result = true;
                break;
            }
        }
        return result;
    }

    private boolean nor(List<SearchQuery.Criterion> criteria, MailboxMessage message,
            final Collection<Long> recentMessageUids) throws MailboxException {
        boolean result = true;
        for (SearchQuery.Criterion criterion : criteria) {
            boolean matches = isMatch(criterion, message, recentMessageUids);
            if (matches) {
                result = false;
                break;
            }
        }
        return result;
    }

    private boolean matches(SearchQuery.FlagCriterion criterion, MailboxMessage message,
            Collection<Long> recentMessageUids) {
        SearchQuery.BooleanOperator operator = criterion.getOperator();
        boolean isSet = operator.isSet();
        Flags.Flag flag = criterion.getFlag();
        boolean result;
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

    private boolean matches(SearchQuery.CustomFlagCriterion criterion, MailboxMessage message,
            Collection<Long> recentMessageUids) {
        SearchQuery.BooleanOperator operator = criterion.getOperator();
        boolean isSet = operator.isSet();
        String flag = criterion.getFlag();
        return isSet == message.createFlags().contains(flag);
    }

    private boolean matches(SearchQuery.UidCriterion criterion, MailboxMessage message) {
        SearchQuery.InOperator operator = criterion.getOperator();
        NumericRange[] ranges = operator.getRange();
        long uid = message.getUid();
        boolean result = false;
        for (NumericRange numericRange : ranges) {
            if (numericRange.isIn(uid)) {
                result = true;
                break;
            }
        }
        return result;
    }

    private boolean matches(SearchQuery.HeaderCriterion criterion, MailboxMessage message)
            throws MailboxException, IOException {
        SearchQuery.HeaderOperator operator = criterion.getOperator();
        String headerName = criterion.getHeaderName();
        boolean result;
        if (operator instanceof SearchQuery.DateOperator) {
            result = matches((SearchQuery.DateOperator) operator, headerName, message);
        } else if (operator instanceof SearchQuery.ContainsOperator) {
            result = matches((SearchQuery.ContainsOperator) operator, headerName, message);
        } else if (operator instanceof SearchQuery.ExistsOperator) {
            result = exists(headerName, message);
        } else if (operator instanceof SearchQuery.AddressOperator) {
            result = matchesAddress((SearchQuery.AddressOperator) operator, headerName, message);
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
    private boolean matchesAddress(SearchQuery.AddressOperator operator, String headerName,
                                   MailboxMessage message) throws MailboxException, IOException {
        String text = operator.getAddress().toUpperCase(Locale.ENGLISH);
        List<Header> headers = ResultUtils.createHeaders(message);
        for (Header header : headers) {
            String name = header.getName();
            if (headerName.equalsIgnoreCase(name)) {
                String value = header.getValue();
                AddressList aList = LenientAddressParser.DEFAULT.parseAddressList(value);
                for ( Address address : aList) {
                    if (address instanceof Mailbox) {
                        if (AddressFormatter.DEFAULT.encode((Mailbox) address).toUpperCase(Locale.ENGLISH)
                            .contains(text)) {
                            return true;
                        }
                    } else if (address instanceof Group) {
                        MailboxList mList = ((Group) address).getMailboxes();
                        for (Mailbox mailbox : mList) {
                            if (AddressFormatter.DEFAULT.encode(mailbox).toUpperCase(Locale.ENGLISH)
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

    private boolean exists(String headerName, MailboxMessage message) throws MailboxException, IOException {
        boolean result = false;
        List<Header> headers = ResultUtils.createHeaders(message);

        for (Header header : headers) {
            String name = header.getName();
            if (headerName.equalsIgnoreCase(name)) {
                result = true;
                break;
            }
        }
        return result;
    }

    private boolean matches(SearchQuery.ContainsOperator operator, String headerName,
            MailboxMessage message) throws MailboxException, IOException {
        String text = operator.getValue().toUpperCase();
        boolean result = false;
        List<Header> headers = ResultUtils.createHeaders(message);
        for (Header header : headers) {
            String name = header.getName();
            if (headerName.equalsIgnoreCase(name)) {
                String value = header.getValue();
                if (value != null) {
                    if (value.toUpperCase().contains(text)) {
                        result = true;
                        break;
                    }
                }
            }
        }
        return result;
    }

    private boolean matches(SearchQuery.DateOperator operator, String headerName, MailboxMessage message)
            throws MailboxException {

        Date date = operator.getDate();
        DateResolution res = operator.getDateResultion();
        try {
            final String value = headerValue(headerName, message);
            if (value == null) {
                return false;
            } else {
                try {
                    Date isoFieldValue = toISODate(value);
                    SearchQuery.DateComparator type = operator.getType();
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

    private String headerValue(String headerName, MailboxMessage message) throws MailboxException, IOException {
        List<Header> headers = ResultUtils.createHeaders(message);
        String value = null;
        for (Header header : headers) {
            String name = header.getName();
            if (headerName.equalsIgnoreCase(name)) {
                value = header.getValue();
                break;
            }
        }
        return value;
    }

    private Date toISODate(String value) throws ParseException {
        StringReader reader = new StringReader(value);
        DateTime dateTime = new DateTimeParser(reader).parseAll();
        Calendar cal = getGMT();
        cal.set(dateTime.getYear(), dateTime.getMonth() - 1, dateTime.getDay(), dateTime.getHour(),
                dateTime.getMinute(), dateTime.getSecond());
        return cal.getTime();
    }

    private boolean matches(SearchQuery.SizeCriterion criterion, MailboxMessage message) throws UnsupportedSearchException {
        SearchQuery.NumericOperator operator = criterion.getOperator();
        long size = message.getFullContentOctets();
        long value = operator.getValue();
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

    private boolean matches(SearchQuery.ModSeqCriterion criterion, MailboxMessage message)
            throws UnsupportedSearchException {
        SearchQuery.NumericOperator operator = criterion.getOperator();
        long modSeq = message.getModSeq();
        long value = operator.getValue();
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

    private boolean matches(SearchQuery.InternalDateCriterion criterion, MailboxMessage message)
            throws UnsupportedSearchException {
        SearchQuery.DateOperator operator = criterion.getOperator();
        return matchesInternalDate(operator, message);
    }

    private boolean matchesInternalDate(SearchQuery.DateOperator operator, MailboxMessage message)
            throws UnsupportedSearchException {
        Date date = operator.getDate();
        DateResolution res = operator.getDateResultion();
        Date internalDate = message.getInternalDate();
        SearchQuery.DateComparator type = operator.getType();
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

    private boolean on(Date date1, Date date2, DateResolution res) {
        String d1 = createDateString(date1, res);
        String d2 = createDateString(date2, res);
        return d1.compareTo(d2) == 0;
    }

    private boolean before(Date date1, Date date2, DateResolution res) {
        String d1 = createDateString(date1, res);
        String d2 = createDateString(date2, res);

        return d1.compareTo(d2) < 0;
    }

    private boolean after(Date date1, Date date2, DateResolution res) {
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
