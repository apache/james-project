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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Stream;

import javax.mail.Flags;

import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedSearchException;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.mailbox.model.SearchQuery.UidRange;
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
import org.apache.james.mime4j.message.HeaderImpl;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.james.mime4j.utils.search.MessageMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Utility methods to help perform search operations.
 */
public class MessageSearches implements Iterable<SimpleMessageSearchIndex.SearchResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageSearches.class);

    private final Iterator<MailboxMessage> messages;
    private final SearchQuery query;
    private final TextExtractor textExtractor;
    private final AttachmentContentLoader attachmentContentLoader;
    private final MailboxSession mailboxSession;

    public MessageSearches(Iterator<MailboxMessage> messages, SearchQuery query, TextExtractor textExtractor, AttachmentContentLoader attachmentContentLoader, MailboxSession mailboxSession) {
        this.messages = messages;
        this.query = query;
        this.textExtractor = textExtractor;
        this.attachmentContentLoader = attachmentContentLoader;
        this.mailboxSession = mailboxSession;
    }

    @Override
    public Iterator<SimpleMessageSearchIndex.SearchResult> iterator() {
        ImmutableList.Builder<MailboxMessage> builder = ImmutableList.builder();
        while (messages.hasNext()) {
            MailboxMessage m = messages.next();
            try {
                if (isMatch(m)) {
                    builder.add(m);
                }
            } catch (MailboxException e) {
                LOGGER.error("Unable to search message {}", m.getUid(), e);
            }
        }
        return builder.build()
            .stream()
            .sorted(CombinedComparator.create(query.getSorts()))
            .map(mailboxMessage -> new SimpleMessageSearchIndex.SearchResult(
                Optional.of(mailboxMessage.getMessageId()),
                mailboxMessage.getMailboxId(),
                mailboxMessage.getUid()))
            .iterator();
    }

    /**
     * Does the row match the given criteria?
     *
     * @param message
     *            <code>MailboxMessage</code>, not null
     * @return <code>true</code> if the row matches the given criteria,
     *         <code>false</code> otherwise
     */
    private boolean isMatch(MailboxMessage message) throws MailboxException {
        final List<SearchQuery.Criterion> criteria = query.getCriteria();
        final Collection<MessageUid> recentMessageUids = query.getRecentMessageUids();
        if (criteria != null) {
            for (SearchQuery.Criterion criterion : criteria) {
                if (!isMatch(criterion, message, recentMessageUids)) {
                    return false;
                }
            }
        }
        return true;
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
     */
    public boolean isMatch(SearchQuery.Criterion criterion, MailboxMessage message,
            final Collection<MessageUid> recentMessageUids) throws MailboxException {
        if (criterion instanceof SearchQuery.InternalDateCriterion) {
            return matches((SearchQuery.InternalDateCriterion) criterion, message);
        } else if (criterion instanceof SearchQuery.SizeCriterion) {
            return matches((SearchQuery.SizeCriterion) criterion, message);
        } else if (criterion instanceof SearchQuery.HeaderCriterion) {
            try {
                return matches((SearchQuery.HeaderCriterion) criterion, message);
            } catch (IOException e) {
                throw new MailboxException("Unable to search header", e);
            }
        } else if (criterion instanceof SearchQuery.UidCriterion) {
            return matches((SearchQuery.UidCriterion) criterion, message);
        } else if (criterion instanceof SearchQuery.MessageIdCriterion) {
            return ((SearchQuery.MessageIdCriterion) criterion).getMessageId().equals(message.getMessageId());
        } else if (criterion instanceof SearchQuery.FlagCriterion) {
            return matches((SearchQuery.FlagCriterion) criterion, message, recentMessageUids);
        } else if (criterion instanceof SearchQuery.CustomFlagCriterion) {
            return matches((SearchQuery.CustomFlagCriterion) criterion, message);
        } else if (criterion instanceof SearchQuery.TextCriterion) {
            return matches((SearchQuery.TextCriterion) criterion, message);
        } else if (criterion instanceof SearchQuery.AllCriterion) {
            return true;
        } else if (criterion instanceof SearchQuery.ConjunctionCriterion) {
            return matches((SearchQuery.ConjunctionCriterion) criterion, message, recentMessageUids);
        } else if (criterion instanceof SearchQuery.AttachmentCriterion) {
            return matches((SearchQuery.AttachmentCriterion) criterion, message);
        } else if (criterion instanceof SearchQuery.ModSeqCriterion) {
            return matches((SearchQuery.ModSeqCriterion) criterion, message);
        } else if (criterion instanceof SearchQuery.MimeMessageIDCriterion) {
            SearchQuery.MimeMessageIDCriterion mimeMessageIDCriterion = (SearchQuery.MimeMessageIDCriterion) criterion;
            return isMatch(mimeMessageIDCriterion.asHeaderCriterion(), message, recentMessageUids);
        } else if (criterion instanceof SearchQuery.SubjectCriterion) {
            SearchQuery.SubjectCriterion subjectCriterion = (SearchQuery.SubjectCriterion) criterion;
            return isMatch(subjectCriterion.asHeaderCriterion(), message, recentMessageUids);
        } else if (criterion instanceof SearchQuery.ThreadIdCriterion) {
            SearchQuery.ThreadIdCriterion threadIdCriterion = (SearchQuery.ThreadIdCriterion) criterion;
            return matches(threadIdCriterion, message);
        } else if (criterion instanceof SearchQuery.SaveDateCriterion) {
            return matches((SearchQuery.SaveDateCriterion) criterion, message);
        } else {
            throw new UnsupportedSearchException();
        }
    }

    private boolean matches(SearchQuery.TextCriterion criterion, MailboxMessage message)
            throws MailboxException {
        try {
            final SearchQuery.ContainsOperator operator = criterion.getOperator();
            final String value = operator.getValue();
            switch (criterion.getType()) {
            case BODY:
                return bodyContains(value, message);
            case FULL:
                return messageContains(value, message);
            case ATTACHMENTS:
                return attachmentsContain(value, message);
            case ATTACHMENT_FILE_NAME:
                return hasFileName(value, message);
            }
            throw new UnsupportedSearchException();
        } catch (IOException | MimeException e) {
            throw new MailboxException("Unable to parse message", e);
        }
    }

    private boolean bodyContains(String value, MailboxMessage message) throws IOException, MimeException {
        final InputStream input = message.getFullContent();
        return isInMessage(value, input, false);
    }

    private boolean isInMessage(String value, InputStream input, boolean header) throws IOException, MimeException {
        return MessageMatcher.builder()
            .searchContents(Lists.<CharSequence>newArrayList(value))
            .caseInsensitive(true)
            .includeHeaders(header)
            .logger(LOGGER)
            .build()
            .messageMatches(input);
    }

    private boolean messageContains(String value, MailboxMessage message) throws IOException, MimeException {
        final InputStream input = message.getFullContent();
        return isInMessage(value, input, true);
    }

    private boolean attachmentsContain(String value, MailboxMessage message) throws IOException, MimeException {
        List<MessageAttachmentMetadata> attachments = message.getAttachments();
        return isInAttachments(value, attachments);
    }

    private boolean hasFileName(String value, MailboxMessage message) throws IOException, MimeException {
        return message.getAttachments()
            .stream()
            .map(MessageAttachmentMetadata::getName)
            .anyMatch(nameOptional -> nameOptional.map(value::equals).orElse(false));
    }

    private boolean isInAttachments(String value, List<MessageAttachmentMetadata> attachments) {
        return attachments.stream()
            .map(MessageAttachmentMetadata::getAttachment)
            .flatMap(attachment -> toAttachmentContent(attachment, mailboxSession))
            .anyMatch(string -> string.contains(value));
    }

    private Stream<String> toAttachmentContent(AttachmentMetadata attachment, MailboxSession mailboxSession) {
        try (InputStream rawData = attachmentContentLoader.load(attachment, mailboxSession)) {
            return textExtractor
                    .extractContent(
                        rawData,
                        attachment.getType())
                    .getTextualContent()
                    .stream();
        } catch (Exception e) {
            LOGGER.error("Error while parsing attachment content", e);
            return Stream.of();
        }
    }

    private HeaderImpl buildTextHeaders(MailboxMessage message) throws IOException, MimeIOException {
        DefaultMessageBuilder defaultMessageBuilder = new DefaultMessageBuilder();
        defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
        Message headersMessage = defaultMessageBuilder
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
            final Collection<MessageUid> recentMessageUids) throws MailboxException {
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
                        Collection<MessageUid> recentMessageUids) throws MailboxException {
        for (SearchQuery.Criterion criterion : criteria) {
            boolean matches = isMatch(criterion, message, recentMessageUids);
            if (!matches) {
                return false;
            }
        }
        return true;
    }

    private boolean or(List<SearchQuery.Criterion> criteria, MailboxMessage message,
                       Collection<MessageUid> recentMessageUids) throws MailboxException {
        for (SearchQuery.Criterion criterion : criteria) {
            boolean matches = isMatch(criterion, message, recentMessageUids);
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private boolean nor(List<SearchQuery.Criterion> criteria, MailboxMessage message,
                        Collection<MessageUid> recentMessageUids) throws MailboxException {
        for (SearchQuery.Criterion criterion : criteria) {
            boolean matches = isMatch(criterion, message, recentMessageUids);
            if (matches) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(SearchQuery.FlagCriterion criterion, MailboxMessage message,
                            Collection<MessageUid> recentMessageUids) {
        SearchQuery.BooleanOperator operator = criterion.getOperator();
        boolean isSet = operator.isSet();
        Flags.Flag flag = criterion.getFlag();
        if (flag == Flags.Flag.ANSWERED) {
            return isSet == message.isAnswered();
        } else if (flag == Flags.Flag.SEEN) {
            return isSet == message.isSeen();
        } else if (flag == Flags.Flag.DRAFT) {
            return isSet == message.isDraft();
        } else if (flag == Flags.Flag.FLAGGED) {
            return isSet == message.isFlagged();
        } else if (flag == Flags.Flag.RECENT) {
            final MessageUid uid = message.getUid();
            return isSet == recentMessageUids.contains(uid);
        } else if (flag == Flags.Flag.DELETED) {
            return isSet == message.isDeleted();
        } else {
            return false;
        }
    }

    private boolean matches(SearchQuery.CustomFlagCriterion criterion, MailboxMessage message) {
        SearchQuery.BooleanOperator operator = criterion.getOperator();
        boolean isSet = operator.isSet();
        String flag = criterion.getFlag();
        return isSet == message.createFlags().contains(flag);
    }

    private boolean matches(SearchQuery.UidCriterion criterion, MailboxMessage message) {
        SearchQuery.UidInOperator operator = criterion.getOperator();
        UidRange[] ranges = operator.getRange();
        MessageUid uid = message.getUid();
        return Arrays.stream(ranges)
            .anyMatch(numericRange -> numericRange.isIn(uid));
    }

    private boolean matches(SearchQuery.HeaderCriterion criterion, MailboxMessage message)
            throws MailboxException, IOException {
        SearchQuery.HeaderOperator operator = criterion.getOperator();
        String headerName = criterion.getHeaderName();
        if (operator instanceof SearchQuery.DateOperator) {
            return matches((SearchQuery.DateOperator) operator, headerName, message);
        } else if (operator instanceof SearchQuery.ContainsOperator) {
            return matches((SearchQuery.ContainsOperator) operator, headerName, message);
        } else if (operator instanceof SearchQuery.ExistsOperator) {
            return exists(headerName, message);
        } else if (operator instanceof SearchQuery.AddressOperator) {
            return matchesAddress((SearchQuery.AddressOperator) operator, headerName, message);
        } else {
            throw new UnsupportedSearchException();
        }
    }

    /**
     * Match against a {@link AddressType} header
     * @return containsAddress
     */
    private boolean matchesAddress(SearchQuery.AddressOperator operator, String headerName,
                                   MailboxMessage message) throws MailboxException, IOException {
        String text = operator.getAddress();
        List<Header> headers = ResultUtils.createHeaders(message);
        for (Header header : headers) {
            String name = header.getName();
            if (headerName.equalsIgnoreCase(name)) {
                String value = header.getValue();
                AddressList addressList = LenientAddressParser.DEFAULT.parseAddressList(value);
                if (matchesAddress(addressList, text)) {
                    return true;
                }

                // Also try to match against raw header now
                return value.toUpperCase(Locale.US).contains(text.toUpperCase(Locale.US));
            }
        }
        return false;
    }

    private boolean matchesAddress(AddressList addressList, String valueToMatch) {
        for (Address address : addressList) {
            if (address instanceof Mailbox) {
                if (doesMailboxContains((Mailbox) address, valueToMatch)) {
                    return true;
                }
            } else if (address instanceof Group) {
                MailboxList mList = ((Group) address).getMailboxes();
                for (Mailbox mailbox : mList) {
                    if (doesMailboxContains(mailbox, valueToMatch)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean doesMailboxContains(Mailbox mailbox, String searchedText) {
        String mailboxAsString = encodeAndUnscramble(mailbox);
        return mailboxAsString.toUpperCase(Locale.US)
            .contains(searchedText.toUpperCase(Locale.US));
    }

    private String encodeAndUnscramble(Mailbox mailbox) {
        return MimeUtil.unscrambleHeaderValue(
            AddressFormatter.DEFAULT.encode(mailbox));
    }

    private boolean exists(String headerName, MailboxMessage message) throws MailboxException, IOException {
        List<Header> headers = ResultUtils.createHeaders(message);

        return headers.stream()
            .map(Header::getName)
            .anyMatch(headerName::equalsIgnoreCase);
    }

    private boolean matches(SearchQuery.ContainsOperator operator, String headerName,
            MailboxMessage message) throws MailboxException, IOException {
        String text = operator.getValue().toUpperCase(Locale.US);
        List<Header> headers = ResultUtils.createHeaders(message);
        for (Header header : headers) {
            String name = header.getName();
            if (headerName.equalsIgnoreCase(name)) {
                String value = MimeUtil.unscrambleHeaderValue(header.getValue());
                if (value != null) {
                    if (value.toUpperCase(Locale.US).contains(text)) {
                        return true;
                    }
                }
            }
        }
        return false;
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
        for (Header header : headers) {
            String name = header.getName();
            if (headerName.equalsIgnoreCase(name)) {
                return MimeUtil.unscrambleHeaderValue(header.getValue());
            }
        }
        return null;
    }

    private Date toISODate(String value) throws ParseException {
        StringReader reader = new StringReader(value);
        DateTime dateTime = new DateTimeParser(reader).parseAll();
        Calendar cal = getGMT(dateTime.getTimeZone());
        cal.set(dateTime.getYear(), dateTime.getMonth() - 1, dateTime.getDay(), dateTime.getHour(),
                dateTime.getMinute(), dateTime.getSecond());
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }


    private boolean matches(SearchQuery.AttachmentCriterion criterion, MailboxMessage message) throws UnsupportedSearchException {
        boolean mailHasAttachments = MessageAttachmentMetadata.hasNonInlinedAttachment(message.getAttachments());
        return mailHasAttachments == criterion.getOperator().isSet();
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
        ModSeq modSeq = message.getModSeq();
        long value = operator.getValue();
        switch (operator.getType()) {
        case LESS_THAN:
            return modSeq.asLong() < value;
        case GREATER_THAN:
            return modSeq.asLong() > value;
        case EQUALS:
            return modSeq.asLong() == value;
        default:
            throw new UnsupportedSearchException();
        }
    }

    private boolean matches(SearchQuery.InternalDateCriterion criterion, MailboxMessage message)
            throws UnsupportedSearchException {
        SearchQuery.DateOperator operator = criterion.getOperator();
        return matchesInternalDate(operator, message);
    }

    private boolean matches(SearchQuery.SaveDateCriterion criterion, MailboxMessage message) throws UnsupportedSearchException {
        SearchQuery.DateOperator operator = criterion.getOperator();
        return matchesSaveDate(operator, message);
    }

    private boolean matches(SearchQuery.ThreadIdCriterion criterion, MailboxMessage message) {
        return message.getThreadId().equals(criterion.getThreadId());
    }

    private boolean matchesInternalDate(SearchQuery.DateOperator operator, MailboxMessage message)
            throws UnsupportedSearchException {
        Date date = operator.getDate();
        DateResolution dateResultion = operator.getDateResultion();
        Date internalDate = message.getInternalDate();
        SearchQuery.DateComparator type = operator.getType();
        switch (type) {
        case ON:
            return on(internalDate, date, dateResultion);
        case BEFORE:
            return before(internalDate, date, dateResultion);
        case AFTER:
            return after(internalDate, date, dateResultion);
        default:
            throw new UnsupportedSearchException();
        }
    }

    private boolean matchesSaveDate(SearchQuery.DateOperator operator, MailboxMessage message) throws UnsupportedSearchException {
        Date date = operator.getDate();
        DateResolution dateResultion = operator.getDateResultion();
        Optional<Date> saveDate = message.getSaveDate();
        SearchQuery.DateComparator type = operator.getType();
        switch (type) {
            case ON:
                return on(saveDate, date, dateResultion);
            case BEFORE:
                return before(saveDate, date, dateResultion);
            case AFTER:
                return after(saveDate, date, dateResultion);
            default:
                throw new UnsupportedSearchException();
        }
    }

    private boolean on(Date date1, Date date2, DateResolution dateResolution) {
        String d1 = createDateString(date1, dateResolution);
        String d2 = createDateString(date2, dateResolution);
        return d1.compareTo(d2) == 0;
    }

    private boolean on(Optional<Date> thisDate, Date thatDate, DateResolution dateResolution) {
        return thisDate.map(date -> on(date, thatDate, dateResolution))
            .orElse(false);
    }

    private boolean before(Date date1, Date date2, DateResolution dateResolution) {
        String d1 = createDateString(date1, dateResolution);
        String d2 = createDateString(date2, dateResolution);

        return d1.compareTo(d2) < 0;
    }

    private boolean before(Optional<Date> thisDate, Date thatDate, DateResolution dateResolution) {
        return thisDate.map(date -> before(date, thatDate, dateResolution))
            .orElse(false);
    }

    private boolean after(Date date1, Date date2, DateResolution dateResolution) {
        String d1 = createDateString(date1, dateResolution);
        String d2 = createDateString(date2, dateResolution);

        return d1.compareTo(d2) > 0;
    }

    private boolean after(Optional<Date> thisDate, Date thatDate, DateResolution dateResolution) {
        return thisDate.map(date -> after(date, thatDate, dateResolution))
            .orElse(false);
    }

    private String createDateString(Date date, DateResolution dateResolution) {
        SimpleDateFormat format = createFormat(dateResolution);
        format.setCalendar(getGMT());
        return format.format(date);
    }

    private SimpleDateFormat createFormat(DateResolution dateResolution) {
        switch (dateResolution) {
            case Year:
                return new SimpleDateFormat("yyyy");
            case Month:
                return new SimpleDateFormat("yyyyMM");
            case Day:
                return new SimpleDateFormat("yyyyMMdd");
            case Hour:
                return new SimpleDateFormat("yyyyMMddkk");
            case Minute:
                return new SimpleDateFormat("yyyyMMddkkmm");
            case Second:
                return new SimpleDateFormat("yyyyMMddkkmmss");
            default:
                return new SimpleDateFormat("yyyyMMddkkmmssSSS");
        }
    }

    private Calendar getGMT() {
        return Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.ENGLISH);
    }

    private Calendar getGMT(int timeZone) {
        return Calendar.getInstance(TimeZone.getTimeZone(String.format("GMT%+04d", timeZone)), Locale.ENGLISH);
    }

}
