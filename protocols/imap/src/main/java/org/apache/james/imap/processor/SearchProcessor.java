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

package org.apache.james.imap.processor;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.mail.Flags.Flag;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.request.DayMonthYear;
import org.apache.james.imap.api.message.request.SearchKey;
import org.apache.james.imap.api.message.request.SearchOperation;
import org.apache.james.imap.api.message.request.SearchResultOption;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SearchResUtil;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.request.SearchRequest;
import org.apache.james.imap.message.response.ESearchResponse;
import org.apache.james.imap.message.response.SearchResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MetaData;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.Criterion;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class SearchProcessor extends AbstractMailboxProcessor<SearchRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchProcessor.class);

    protected static final String SEARCH_MODSEQ = "SEARCH_MODSEQ";
    private static final List<String> CAPS = ImmutableList.of("WITHIN", "ESEARCH", "SEARCHRES");
    
    public SearchProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(SearchRequest.class, next, mailboxManager, factory, metricFactory);
    }

    @Override
    protected void doProcess(SearchRequest request, ImapSession session, String tag, ImapCommand command, Responder responder) {
        final SearchOperation operation = request.getSearchOperation();
        final SearchKey searchKey = operation.getSearchKey();
        final boolean useUids = request.isUseUids();
        List<SearchResultOption> resultOptions = operation.getResultOptions();

        try {

            final MessageManager mailbox = getSelectedMailbox(session);

            final SearchQuery query = toQuery(searchKey, session);
            MailboxSession msession = ImapSessionUtils.getMailboxSession(session);

            final Collection<MessageUid> uids = performUidSearch(mailbox, query, msession);
            final Collection<Long> results = asResults(session, useUids, uids);

            // Check if the search did contain the MODSEQ searchkey. If so we need to include the highest mod in the response.
            //
            // See RFC4551: 3.4. MODSEQ Search Criterion in SEARCH
            final Long highestModSeq;
            if (session.getAttribute(SEARCH_MODSEQ) != null) {
                MetaData metaData = mailbox.getMetaData(false, msession, MessageManager.MetaData.FetchGroup.NO_COUNT);
                highestModSeq = findHighestModSeq(msession, mailbox, MessageRange.toRanges(uids), metaData.getHighestModSeq());
                
                // Enable CONDSTORE as this is a CONDSTORE enabling command
                condstoreEnablingCommand(session, responder,  metaData, true);                
                
            } else {
                highestModSeq = null;
            }
            final long[] ids = toArray(results);

            final ImapResponseMessage response;
            if (resultOptions == null || resultOptions.isEmpty()) {
                response = new SearchResponse(ids, highestModSeq);
            } else {
                List<Long> idList = new ArrayList<>(ids.length);
                for (long id : ids) {
                    idList.add(id);
                }

                List<IdRange> idsAsRanges = new ArrayList<>();
                for (Long id: idList) {
                    idsAsRanges.add(new IdRange(id));
                }
                IdRange[] idRanges = IdRange.mergeRanges(idsAsRanges).toArray(new IdRange[0]);
                
                List<UidRange> uidsAsRanges = new ArrayList<>();
                for (MessageUid uid: uids) {
                    uidsAsRanges.add(new UidRange(uid));
                }
                UidRange[] uidRanges = UidRange.mergeRanges(uidsAsRanges).toArray(new UidRange[0]);
                
                boolean esearch = false;
                for (SearchResultOption resultOption : resultOptions) {
                    if (SearchResultOption.SAVE != resultOption) {
                        esearch = true;
                        break;
                    }
                }
                
                if (esearch) {
                    long min = -1;
                    long max = -1;
                    long count = ids.length;

                    if (ids.length > 0) {
                        min = ids[0];
                        max = ids[ids.length - 1];
                    } 
                   
                    
                    // Save the sequence-set for later usage. This is part of SEARCHRES 
                    if (resultOptions.contains(SearchResultOption.SAVE)) {
                        if (resultOptions.contains(SearchResultOption.ALL) || resultOptions.contains(SearchResultOption.COUNT)) {
                            // if the options contain ALL or COUNT we need to save the complete sequence-set
                            SearchResUtil.saveSequenceSet(session, idRanges);
                        } else {
                            List<IdRange> savedRanges = new ArrayList<>();
                            if (resultOptions.contains(SearchResultOption.MIN)) {
                                // Store the MIN
                                savedRanges.add(new IdRange(min));  
                            } 
                            if (resultOptions.contains(SearchResultOption.MAX)) {
                                // Store the MAX
                                savedRanges.add(new IdRange(max));
                            }
                            SearchResUtil.saveSequenceSet(session, savedRanges.toArray(new IdRange[0]));
                        }
                    }
                    response = new ESearchResponse(min, max, count, idRanges, uidRanges, highestModSeq, tag, useUids, resultOptions);
                } else {
                    // Just save the returned sequence-set as this is not SEARCHRES + ESEARCH
                    SearchResUtil.saveSequenceSet(session, idRanges);
                    response = new SearchResponse(ids, highestModSeq);

                }
            }

            responder.respond(response);

            boolean omitExpunged = (!useUids);
            unsolicitedResponses(session, responder, omitExpunged, useUids);
            okComplete(command, tag, responder);
        } catch (MessageRangeException e) {
            LOGGER.debug("Search failed in mailbox {} because of an invalid sequence-set ", session.getSelected().getMailboxId(), e);
            taggedBad(command, tag, responder, HumanReadableText.INVALID_MESSAGESET);
        } catch (MailboxException e) {
            LOGGER.error("Search failed in mailbox {}", session.getSelected().getMailboxId(), e);
            no(command, tag, responder, HumanReadableText.SEARCH_FAILED);
            
            if (resultOptions.contains(SearchResultOption.SAVE)) {
                // Reset the saved sequence-set on a BAD response if the SAVE option was used.
                //
                // See RFC5182 2.1.Normative Description of the SEARCHRES Extension
                SearchResUtil.resetSavedSequenceSet(session);
            }
        } finally {
            session.setAttribute(SEARCH_MODSEQ, null);
        }
    }

    private Collection<Long> asResults(ImapSession session, boolean useUids, Collection<MessageUid> uids) {
        if (useUids) {
            return uids.stream()
                .map(MessageUid::asLong)
                .collect(Guavate.toImmutableList());
        } else {
            return uids.stream()
                .map(uid -> session.getSelected().msn(uid))
                .map(Integer::longValue)
                .filter(msn -> msn != SelectedMailbox.NO_SUCH_MESSAGE)
                .collect(Guavate.toImmutableList());
        }
    }

    private Collection<MessageUid> performUidSearch(MessageManager mailbox, SearchQuery query, MailboxSession msession) throws MailboxException {
        try (Stream<MessageUid> stream = mailbox.search(query, msession)) {
            return stream.collect(Guavate.toImmutableList());
        }
    }

    private long[] toArray(Collection<Long> results) {
        return results.stream().mapToLong(x -> x).toArray();
    }

    /**
     * Find the highest mod-sequence number in the given {@link MessageRange}'s.
     * 
     * @param session
     * @param mailbox
     * @param ranges
     * @param currentHighest
     * @return highestModSeq
     * @throws MailboxException
     */
    private Long findHighestModSeq(MailboxSession session, MessageManager mailbox, List<MessageRange> ranges, long currentHighest) throws MailboxException {
        Long highestModSeq = null;
        
        // Reverse loop over the ranges as its more likely that we find a match at the end
        int size = ranges.size();
        for (int i = size - 1; i > 0; i--) {
            MessageResultIterator results = mailbox.getMessages(ranges.get(i), FetchGroupImpl.MINIMAL, session);
            while (results.hasNext()) {
                long modSeq = results.next().getModSeq();
                if (highestModSeq == null || modSeq > highestModSeq) {
                    highestModSeq = modSeq;
                }
                if (highestModSeq == currentHighest) {
                    return highestModSeq;
                }
            }
            
        }
        return highestModSeq;
    }


    private SearchQuery toQuery(SearchKey key, ImapSession session) throws MessageRangeException {
        final SearchQuery result = new SearchQuery();
        final SelectedMailbox selected = session.getSelected();
        if (selected != null) {
            result.addRecentMessageUids(selected.getRecent());
        }
        final SearchQuery.Criterion criterion = toCriterion(key, session);
        result.andCriteria(criterion);
        return result;
    }

    private SearchQuery.Criterion toCriterion(SearchKey key, ImapSession session) throws MessageRangeException {
        final int type = key.getType();
        final DayMonthYear date = key.getDate();
        switch (type) {
        case SearchKey.TYPE_ALL:
            return SearchQuery.all();
        case SearchKey.TYPE_AND:
            return and(key.getKeys(), session);
        case SearchKey.TYPE_ANSWERED:
            return SearchQuery.flagIsSet(Flag.ANSWERED);
        case SearchKey.TYPE_BCC:
            return SearchQuery.address(AddressType.Bcc, key.getValue());
        case SearchKey.TYPE_BEFORE:
            return SearchQuery.internalDateBefore(date.toDate(), DateResolution.Day);
        case SearchKey.TYPE_BODY:
            return SearchQuery.bodyContains(key.getValue());
        case SearchKey.TYPE_CC:
            return SearchQuery.address(AddressType.Cc, key.getValue());
        case SearchKey.TYPE_DELETED:
            return SearchQuery.flagIsSet(Flag.DELETED);
        case SearchKey.TYPE_DRAFT:
            return SearchQuery.flagIsSet(Flag.DRAFT);
        case SearchKey.TYPE_FLAGGED:
            return SearchQuery.flagIsSet(Flag.FLAGGED);
        case SearchKey.TYPE_FROM:
            return SearchQuery.address(AddressType.From, key.getValue());
        case SearchKey.TYPE_HEADER:
            String value = key.getValue();
            // Check if header exists if the value is empty. See IMAP-311
            if (value == null || value.length() == 0) {
                return SearchQuery.headerExists(key.getName());
            } else {
                return SearchQuery.headerContains(key.getName(), value);
            }
        case SearchKey.TYPE_KEYWORD:
            return SearchQuery.flagIsSet(key.getValue());
        case SearchKey.TYPE_LARGER:
            return SearchQuery.sizeGreaterThan(key.getSize());
        case SearchKey.TYPE_NEW:
            return SearchQuery.and(SearchQuery.flagIsSet(Flag.RECENT), SearchQuery.flagIsUnSet(Flag.SEEN));
        case SearchKey.TYPE_NOT:
            return not(key.getKeys(), session);
        case SearchKey.TYPE_OLD:
            return SearchQuery.flagIsUnSet(Flag.RECENT);
        case SearchKey.TYPE_ON:
            return SearchQuery.internalDateOn(date.toDate(), DateResolution.Day);
        case SearchKey.TYPE_OR:
            return or(key.getKeys(), session);
        case SearchKey.TYPE_RECENT:
            return SearchQuery.flagIsSet(Flag.RECENT);
        case SearchKey.TYPE_SEEN:
            return SearchQuery.flagIsSet(Flag.SEEN);
        case SearchKey.TYPE_SENTBEFORE:
            return SearchQuery.headerDateBefore(ImapConstants.RFC822_DATE, date.toDate(), DateResolution.Day);
        case SearchKey.TYPE_SENTON:
            return SearchQuery.headerDateOn(ImapConstants.RFC822_DATE, date.toDate(), DateResolution.Day);
        case SearchKey.TYPE_SENTSINCE:
            // Include the date which is used as search param. See IMAP-293
            Criterion onCrit = SearchQuery.headerDateOn(ImapConstants.RFC822_DATE, date.toDate(), DateResolution.Day);
            Criterion afterCrit = SearchQuery.headerDateAfter(ImapConstants.RFC822_DATE, date.toDate(), DateResolution.Day);
            return SearchQuery.or(onCrit, afterCrit);
        case SearchKey.TYPE_SEQUENCE_SET:
            return sequence(key.getSequenceNumbers(), session);
        case SearchKey.TYPE_SINCE:
            // Include the date which is used as search param. See IMAP-293
            return SearchQuery.or(SearchQuery.internalDateOn(date.toDate(), DateResolution.Day), SearchQuery.internalDateAfter(date.toDate(), DateResolution.Day));
        case SearchKey.TYPE_SMALLER:
            return SearchQuery.sizeLessThan(key.getSize());
        case SearchKey.TYPE_SUBJECT:
            return SearchQuery.headerContains(ImapConstants.RFC822_SUBJECT, key.getValue());
        case SearchKey.TYPE_TEXT:
            return SearchQuery.mailContains(key.getValue());
        case SearchKey.TYPE_TO:
            return SearchQuery.address(AddressType.To, key.getValue());
        case SearchKey.TYPE_UID:
            return uids(key.getUidRanges(), session);
        case SearchKey.TYPE_UNANSWERED:
            return SearchQuery.flagIsUnSet(Flag.ANSWERED);
        case SearchKey.TYPE_UNDELETED:
            return SearchQuery.flagIsUnSet(Flag.DELETED);
        case SearchKey.TYPE_UNDRAFT:
            return SearchQuery.flagIsUnSet(Flag.DRAFT);
        case SearchKey.TYPE_UNFLAGGED:
            return SearchQuery.flagIsUnSet(Flag.FLAGGED);
        case SearchKey.TYPE_UNKEYWORD:
            return SearchQuery.flagIsUnSet(key.getValue());
        case SearchKey.TYPE_UNSEEN:
            return SearchQuery.flagIsUnSet(Flag.SEEN);
        case SearchKey.TYPE_OLDER:
            Date withinDate = createWithinDate(key);
            return SearchQuery.or(SearchQuery.internalDateOn(withinDate, DateResolution.Second), SearchQuery.internalDateBefore(withinDate, DateResolution.Second));
        case SearchKey.TYPE_YOUNGER:
            Date withinDate2 = createWithinDate(key);
            return SearchQuery.or(SearchQuery.internalDateOn(withinDate2, DateResolution.Second), SearchQuery.internalDateAfter(withinDate2, DateResolution.Second));
        case SearchKey.TYPE_MODSEQ: 
            session.setAttribute(SEARCH_MODSEQ, true);
            long modSeq = key.getModSeq();
            return SearchQuery.or(SearchQuery.modSeqEquals(modSeq), SearchQuery.modSeqGreaterThan(modSeq));
        default:
            LOGGER.warn("Ignoring unknown search key {}", type);
            return SearchQuery.all();
        }
    }
    
    private Date createWithinDate(SearchKey key) {
        long seconds = key.getSeconds();
        long res = System.currentTimeMillis() - seconds * 1000;
        return new Date(res);
    }

    /**
     * Create a {@link Criterion} for the given sequence-sets. 
     * This include special handling which is needed for SEARCH to not return a BAD response on a invalid message-set. 
     * See IMAP-292 for more details.
     */
    private Criterion sequence(IdRange[] sequenceNumbers, ImapSession session) throws MessageRangeException {
        
        final SelectedMailbox selected = session.getSelected();

        // First of check if we have any messages in the mailbox
        // if not we don't need to go through all of this
        final List<SearchQuery.UidRange> ranges = new ArrayList<>();
        if (selected.existsCount() > 0) {
            for (IdRange range : sequenceNumbers) {
                long lowVal = range.getLowVal();
                long highVal = range.getHighVal();
                // Take care of "*" and "*:*" values by return the last
                // message in
                // the mailbox. See IMAP-289
                if (lowVal == Long.MAX_VALUE && highVal == Long.MAX_VALUE) {
                    MessageUid highUid = selected.getLastUid().orElse(MessageUid.MIN_VALUE);

                    ranges.add(new SearchQuery.UidRange(highUid));
                } else {
                    Optional<MessageUid> lowUid;
                    if (lowVal != Long.MIN_VALUE) {
                        lowUid = selected.uid((int) lowVal);
                    } else {
                        lowUid = selected.getFirstUid();
                    }

                    // The lowVal should never be
                    // SelectedMailbox.NO_SUCH_MESSAGE but we check for it
                    // just to be safe
                    if (lowUid.isPresent()) {
                        Optional<MessageUid> highUid = Optional.empty();
                        if (highVal != Long.MAX_VALUE) {
                            highUid = selected.uid((int) highVal);
                            if (!highUid.isPresent()) {
                                // we requested a message with a MSN higher
                                // then
                                // the current msg count. So just use the
                                // highest uid as max
                                highUid = selected.getLastUid();
                            }
                        } else {
                            highUid = selected.getLastUid();
                        }
                        ranges.add(new SearchQuery.UidRange(lowUid.orElse(MessageUid.MIN_VALUE), highUid.orElse(MessageUid.MAX_VALUE)));
                    }
                }
            }
        }

        return SearchQuery.uid(ranges.toArray(new SearchQuery.UidRange[0]));
    }
    
    /**
     * Create a {@link Criterion} for the given uid-sets. 
     * This include special handling which is needed for SEARCH to not return a BAD response on a invalid message-set. 
     * See IMAP-292 for more details.
     */
    private Criterion uids(UidRange[] uids, ImapSession session) throws MessageRangeException {
        
        final SelectedMailbox selected = session.getSelected();

        // First of check if we have any messages in the mailbox
        // if not we don't need to go through all of this
        final List<SearchQuery.UidRange> ranges = new ArrayList<>();
        if (selected.existsCount() > 0) {
            for (UidRange range : uids) {
                MessageUid lowVal = range.getLowVal();
                MessageUid highVal = range.getHighVal();
                // Take care of "*" and "*:*" values by return the last
                // message in
                // the mailbox. See IMAP-289
                if (lowVal.equals(MessageUid.MAX_VALUE) && highVal.equals(MessageUid.MAX_VALUE)) {
                    ranges.add(new SearchQuery.UidRange(selected.getLastUid().orElse(MessageUid.MIN_VALUE)));
                } else if (highVal.equals(MessageUid.MAX_VALUE) && selected.getLastUid().orElse(MessageUid.MIN_VALUE).compareTo(lowVal) < 0) {
                    // Sequence uid ranges which use
                    // *:<uid-higher-then-last-uid>
                    // MUST return at least the highest uid in the mailbox
                    // See IMAP-291
                    ranges.add(new SearchQuery.UidRange(selected.getLastUid().orElse(MessageUid.MIN_VALUE)));
                } else {
                    ranges.add(new SearchQuery.UidRange(lowVal, highVal));
                }
            }
        }

        return SearchQuery.uid(ranges.toArray(new SearchQuery.UidRange[0]));
    }

    private Criterion or(List<SearchKey> keys, ImapSession session) throws MessageRangeException {
        final SearchKey keyOne = keys.get(0);
        final SearchKey keyTwo = keys.get(1);
        final Criterion criterionOne = toCriterion(keyOne, session);
        final Criterion criterionTwo = toCriterion(keyTwo, session);
        return SearchQuery.or(criterionOne, criterionTwo);
    }

    private Criterion not(List<SearchKey> keys, ImapSession session) throws MessageRangeException {
        final SearchKey key = keys.get(0);
        final Criterion criterion = toCriterion(key, session);
        return SearchQuery.not(criterion);
    }

    private Criterion and(List<SearchKey> keys, ImapSession session) throws MessageRangeException {
        final int size = keys.size();
        final List<Criterion> criteria = new ArrayList<>(size);
        for (SearchKey key : keys) {
            final Criterion criterion = toCriterion(key, session);
            criteria.add(criterion);
        }
        return SearchQuery.and(criteria);
    }

    @Override
    public List<String> getImplementedCapabilities(ImapSession session) {
        return CAPS;
    }

    @Override
    protected Closeable addContextToMDC(SearchRequest message) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "SEARCH")
            .addContext("useUid", message.isUseUids())
            .addContext("searchOperation", message.getSearchOperation())
            .build();
    }
}
