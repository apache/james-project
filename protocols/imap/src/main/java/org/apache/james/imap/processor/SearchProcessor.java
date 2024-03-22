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

import static org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode.IGNORE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.Flags.Flag;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.request.DayMonthYear;
import org.apache.james.imap.api.message.request.SearchKey;
import org.apache.james.imap.api.message.request.SearchOperation;
import org.apache.james.imap.api.message.request.SearchResultOption;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SearchResUtil;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.request.SearchRequest;
import org.apache.james.imap.message.response.ESearchResponse;
import org.apache.james.imap.message.response.SearchResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MailboxMetaData;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.Criterion;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparators;
import it.unimi.dsi.fastutil.longs.LongList;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SearchProcessor extends AbstractMailboxProcessor<SearchRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchProcessor.class);

    protected static final String SEARCH_MODSEQ = "SEARCH_MODSEQ";
    private static final List<Capability> CAPS = ImmutableList.of(Capability.of("WITHIN"),
        Capability.of("ESEARCH"),
        Capability.of("SEARCHRES"),
        Capability.of("PARTIAL"));

    @Inject
    public SearchProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                           MetricFactory metricFactory) {
        super(SearchRequest.class, mailboxManager, factory, metricFactory);
    }

    @Override
    protected Mono<Void> processRequestReactive(SearchRequest request, ImapSession session, Responder responder) {
        SearchOperation operation = request.getSearchOperation();
        SearchKey searchKey = operation.getSearchKey();

        try {
            MailboxSession msession = session.getMailboxSession();
            SearchQuery query = toQuery(searchKey, session);
            boolean useUids = request.isUseUids();
            boolean omitExpunged = (!useUids);
            return getSelectedMailboxReactive(session)
                .flatMap(Throwing.function(mailbox -> performUidSearch(mailbox, query, msession)
                    .flatMap(uids -> computeHighestModSeqIfNeeded(session, responder, mailbox, msession, uids)
                        .doOnNext(highestModSeq -> {
                            ImapResponseMessage response = toResponse(request, session, uids, highestModSeq);
                            responder.respond(response);
                        }))
                    .then(unsolicitedResponses(session, responder, omitExpunged, useUids))))
                .then(Mono.fromRunnable(() -> {
                    okComplete(request, responder);
                    session.setAttribute(SEARCH_MODSEQ, null);
                }))
                .then()
                .onErrorResume(MessageRangeException.class, e -> {
                    no(request, responder, HumanReadableText.SEARCH_FAILED);

                    if (request.getSearchOperation().getResultOptions().contains(SearchResultOption.SAVE)) {
                        // Reset the saved sequence-set on a BAD response if the SAVE option was used.
                        //
                        // See RFC5182 2.1.Normative Description of the SEARCHRES Extension
                        SearchResUtil.resetSavedSequenceSet(session);
                    }
                    return ReactorUtils.logAsMono(() -> LOGGER.error("Search failed in mailbox {}", session.getSelected().getMailboxId(), e));
                });
        } catch (MessageRangeException e) {
            return ReactorUtils.logAsMono(() -> LOGGER.debug("Search failed in mailbox {} because of an invalid sequence-set ", session.getSelected().getMailboxId(), e))
                .then(Mono.fromRunnable(() -> taggedBad(request, responder, HumanReadableText.INVALID_MESSAGESET)));
        }
    }

    private Mono<Optional<ModSeq>> computeHighestModSeqIfNeeded(ImapSession session, Responder responder, MessageManager mailbox, MailboxSession msession, Collection<MessageUid> uids) {
        // Check if the search did contain the MODSEQ searchkey. If so we need to include the highest mod in the response.
        //
        // See RFC4551: 3.4. MODSEQ Search Criterion in SEARCH
        if (session.getAttribute(SEARCH_MODSEQ) != null) {
            try {
                return mailbox.getMetaDataReactive(IGNORE, msession, EnumSet.of(MailboxMetaData.Item.HighestModSeq))
                    .flatMap(metaData -> {
                        // Enable CONDSTORE as this is a CONDSTORE enabling command
                        condstoreEnablingCommand(session, responder,  metaData, true);
                        return findHighestModSeq(msession, mailbox, MessageRange.toRanges(uids), metaData.getHighestModSeq());
                    })
                    .switchIfEmpty(Mono.empty());
            } catch (MailboxException e) {
                throw new RuntimeException(e);
            }
        } else {
            return Mono.just(Optional.empty());
        }
    }

    private ImapResponseMessage toResponse(SearchRequest request, ImapSession session, Collection<MessageUid> uids, Optional<ModSeq> highestModSeq) {
        LongList ids = asResults(session, request.isUseUids(), uids);

        List<SearchResultOption> resultOptions = request.getSearchOperation().getResultOptions();
        if (resultOptions == null || resultOptions.isEmpty()) {
            return new SearchResponse(ids, highestModSeq.orElse(null));
        } else {
            return handleResultOptions(request, session, highestModSeq.orElse(null), ids);
        }
    }

    private ImapResponseMessage handleResultOptions(SearchRequest request, ImapSession session, ModSeq highestModSeq, LongList ids) {
        List<SearchResultOption> resultOptions = request.getSearchOperation().getResultOptions();

        IdRange[] idRanges = asRanges(
            request.getSearchOperation()
                .getPartialRange()
                .map(partialRange -> partialRange.filter(ids))
                .orElse(ids));

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
            long count = ids.size();

            if (!ids.isEmpty()) {
                min = ids.getLong(0);
                max = ids.getLong(ids.size() - 1);
            }

            // Save the sequence-set for later usage. This is part of SEARCHRES
            if (resultOptions.contains(SearchResultOption.SAVE)) {
                if (resultOptions.contains(SearchResultOption.ALL) || resultOptions.contains(SearchResultOption.COUNT) || resultOptions.contains(SearchResultOption.PARTIAL)) {
                    // if the options contain ALL or COUNT we need to save the complete sequence-set
                    SearchResUtil.saveSequenceSet(session, idRanges);
                } else {
                    List<IdRange> savedRanges = new ArrayList<>();
                    if (resultOptions.contains(SearchResultOption.MIN) && min > 0) {
                        // Store the MIN
                        savedRanges.add(new IdRange(min));
                    }
                    if (resultOptions.contains(SearchResultOption.MAX) && max > 0) {
                        // Store the MAX
                        savedRanges.add(new IdRange(max));
                    }
                    SearchResUtil.saveSequenceSet(session, savedRanges.toArray(IdRange[]::new));
                }
            }
            return new ESearchResponse(min, max, count,
                allRange(resultOptions, idRanges),
                partialRange(resultOptions, idRanges), highestModSeq, request.getTag(), request.isUseUids(), resultOptions, request.getSearchOperation().getPartialRange());
        } else {
            // Just save the returned sequence-set as this is not SEARCHRES + ESEARCH
            SearchResUtil.saveSequenceSet(session, idRanges);
            return new SearchResponse(ids, highestModSeq);
        }
    }

    private IdRange[] allRange(List<SearchResultOption> resultOptions, IdRange[] idRanges) {
        if (resultOptions.contains(SearchResultOption.PARTIAL)) {
            return null;
        }
        return idRanges;
    }

    private IdRange[] partialRange(List<SearchResultOption> resultOptions, IdRange[] idRanges) {
        if (resultOptions.contains(SearchResultOption.PARTIAL)) {
            return idRanges;
        }
        return null;
    }

    /**
     * Optimization of IdRange.mergeRanges(idsAsRanges) for list of long
     */
    private IdRange[] asRanges(LongList ids) {
        ids.sort(LongComparators.NATURAL_COMPARATOR);
        List<IdRange> idsAsRanges = new ArrayList<>();
        long lowBound = -1;
        long highBound = -1;
        for (int i = 0; i < ids.size(); i++) {
            long id = ids.getLong(i);
            // Initialize
            if (lowBound == -1) {
                lowBound = id;
                highBound = id;
                continue;
            }
            if (id == highBound + 1) {
                highBound = id;
                continue;
            }
            idsAsRanges.add(new IdRange(lowBound, highBound));
            lowBound = id;
            highBound = id;
        }
        if (lowBound != -1 && highBound != -1) {
            idsAsRanges.add(new IdRange(lowBound, highBound));
        }
        IdRange[] result = new IdRange[idsAsRanges.size()];
        return idsAsRanges.toArray(result);
    }

    private LongList asResults(ImapSession session, boolean useUids, Collection<MessageUid> uids) {
        LongList result = new LongArrayList(uids.size());
        // Avoid using streams here as the overhead for large search responses is massive.
        if (useUids) {
            for (MessageUid uid: uids) {
                result.add(uid.asLong());
            }
        } else {
            for (MessageUid uid: uids) {
                session.getSelected().msn(uid)
                    .ifPresent(l -> result.add(l.asInt()));
            }
        }
        return result;
    }

    private Mono<Collection<MessageUid>> performUidSearch(MessageManager mailbox, SearchQuery query, MailboxSession msession) throws MailboxException {
        return Flux.from(mailbox.search(query, msession))
            .collect(ImmutableList.toImmutableList());
    }

    /**
     * Find the highest mod-sequence number in the given {@link MessageRange}'s.
     */
    private Mono<Optional<ModSeq>> findHighestModSeq(MailboxSession session, MessageManager mailbox, List<MessageRange> ranges, ModSeq currentHighest) {
        // Reverse loop over the ranges as its more likely that we find a match at the end
        ArrayList<MessageRange> rangesCopy = new ArrayList<>(ranges);
        Collections.reverse(rangesCopy);
        return Flux.fromIterable(rangesCopy)
            .concatMap(range -> Flux.from(mailbox.listMessagesMetadata(range, session)))
            .map(ComposedMessageIdWithMetaData::getModSeq)
            .takeUntil(modseq -> modseq.equals(currentHighest))
            .reduce((a, b) -> {
                if (a.compareTo(b) > 0) {
                    return a;
                }
                return b;
            }).map(Optional::of)
            .switchIfEmpty(Mono.fromCallable(Optional::empty));
    }

    private SearchQuery toQuery(SearchKey key, ImapSession session) throws MessageRangeException {
        SearchQuery.Criterion criterion = toCriterion(key, session);
        SearchQuery.Builder builder = SearchQuery.builder();
        SelectedMailbox selected = session.getSelected();
        if (selected != null) {
            builder.addRecentMessageUids(selected.getRecent());
        }
        return builder.andCriterion(criterion)
            .build();
    }

    private SearchQuery.Criterion toCriterion(SearchKey key, ImapSession session) throws MessageRangeException {
        final SearchKey.Type type = key.getType();
        final DayMonthYear date = key.getDate();
        switch (type) {
        case TYPE_ALL:
            return SearchQuery.all();
        case TYPE_AND:
            return and(key.getKeys(), session);
        case TYPE_ANSWERED:
            return SearchQuery.flagIsSet(Flag.ANSWERED);
        case TYPE_BCC:
            return SearchQuery.address(AddressType.Bcc, key.getValue());
        case TYPE_BEFORE:
            return SearchQuery.internalDateBefore(date.toDate(), DateResolution.Day);
        case TYPE_BODY:
            return SearchQuery.bodyContains(key.getValue());
        case TYPE_CC:
            return SearchQuery.address(AddressType.Cc, key.getValue());
        case TYPE_DELETED:
            return SearchQuery.flagIsSet(Flag.DELETED);
        case TYPE_DRAFT:
            return SearchQuery.flagIsSet(Flag.DRAFT);
        case TYPE_FLAGGED:
            return SearchQuery.flagIsSet(Flag.FLAGGED);
        case TYPE_FROM:
            return SearchQuery.address(AddressType.From, key.getValue());
        case TYPE_HEADER:
            String value = key.getValue();
            // Check if header exists if the value is empty. See IMAP-311
            if (value == null || value.length() == 0) {
                return SearchQuery.headerExists(key.getName());
            } else {
                return SearchQuery.headerContains(key.getName(), value);
            }
        case TYPE_KEYWORD:
            return SearchQuery.flagIsSet(key.getValue());
        case TYPE_LARGER:
            return SearchQuery.sizeGreaterThan(key.getSize());
        case TYPE_NEW:
            return SearchQuery.and(SearchQuery.flagIsSet(Flag.RECENT), SearchQuery.flagIsUnSet(Flag.SEEN));
        case TYPE_NOT:
            return not(key.getKeys(), session);
        case TYPE_OLD:
            return SearchQuery.flagIsUnSet(Flag.RECENT);
        case TYPE_ON:
            return SearchQuery.internalDateOn(date.toDate(), DateResolution.Day);
        case TYPE_OR:
            return or(key.getKeys(), session);
        case TYPE_RECENT:
            return SearchQuery.flagIsSet(Flag.RECENT);
        case TYPE_SEEN:
            return SearchQuery.flagIsSet(Flag.SEEN);
        case TYPE_SENTBEFORE:
            return SearchQuery.headerDateBefore(ImapConstants.RFC822_DATE, date.toDate(), DateResolution.Day);
        case TYPE_SENTON:
            return SearchQuery.headerDateOn(ImapConstants.RFC822_DATE, date.toDate(), DateResolution.Day);
        case TYPE_SENTSINCE:
            // Include the date which is used as search param. See IMAP-293
            Criterion onCrit = SearchQuery.headerDateOn(ImapConstants.RFC822_DATE, date.toDate(), DateResolution.Day);
            Criterion afterCrit = SearchQuery.headerDateAfter(ImapConstants.RFC822_DATE, date.toDate(), DateResolution.Day);
            return SearchQuery.or(onCrit, afterCrit);
        case TYPE_SEQUENCE_SET:
            return sequence(key.getSequenceNumbers(), session);
        case TYPE_SINCE:
            // Include the date which is used as search param. See IMAP-293
            return SearchQuery.or(SearchQuery.internalDateOn(date.toDate(), DateResolution.Day), SearchQuery.internalDateAfter(date.toDate(), DateResolution.Day));
        case TYPE_SMALLER:
            return SearchQuery.sizeLessThan(key.getSize());
        case TYPE_SUBJECT:
            return SearchQuery.subject(key.getValue());
        case TYPE_TEXT:
            return SearchQuery.mailContains(key.getValue());
        case TYPE_TO:
            return SearchQuery.address(AddressType.To, key.getValue());
        case TYPE_UID:
            return uids(key.getUidRanges(), session);
        case TYPE_UNANSWERED:
            return SearchQuery.flagIsUnSet(Flag.ANSWERED);
        case TYPE_UNDELETED:
            return SearchQuery.flagIsUnSet(Flag.DELETED);
        case TYPE_UNDRAFT:
            return SearchQuery.flagIsUnSet(Flag.DRAFT);
        case TYPE_UNFLAGGED:
            return SearchQuery.flagIsUnSet(Flag.FLAGGED);
        case TYPE_UNKEYWORD:
            return SearchQuery.flagIsUnSet(key.getValue());
        case TYPE_UNSEEN:
            return SearchQuery.flagIsUnSet(Flag.SEEN);
        case TYPE_OLDER:
            Date withinDate = createWithinDate(key);
            return SearchQuery.or(SearchQuery.internalDateOn(withinDate, DateResolution.Second), SearchQuery.internalDateBefore(withinDate, DateResolution.Second));
        case TYPE_YOUNGER:
            Date withinDate2 = createWithinDate(key);
            return SearchQuery.or(SearchQuery.internalDateOn(withinDate2, DateResolution.Second), SearchQuery.internalDateAfter(withinDate2, DateResolution.Second));
        case TYPE_MODSEQ: 
            session.setAttribute(SEARCH_MODSEQ, true);
            long modSeq = key.getModSeq();
            return SearchQuery.or(SearchQuery.modSeqEquals(modSeq), SearchQuery.modSeqGreaterThan(modSeq));
        case TYPE_THREADID:
            return SearchQuery.threadId(ThreadId.fromBaseMessageId(getMailboxManager().getMessageIdFactory().fromString(key.getThreadId())));
        case TYPE_EMAILID:
            return SearchQuery.hasMessageId(getMailboxManager().getMessageIdFactory().fromString(key.getMessageId()));
        case TYPE_SAVEDBEFORE:
            return SearchQuery.saveDateBefore(date.toDate(), DateResolution.Day);
        case TYPE_SAVEDON:
            return SearchQuery.saveDateOn(date.toDate(), DateResolution.Day);
        case TYPE_SAVEDSINCE:
            return SearchQuery.or(SearchQuery.saveDateOn(date.toDate(), DateResolution.Day), SearchQuery.saveDateAfter(date.toDate(), DateResolution.Day));
        case TYPE_SAVEDATESUPPORTED:
            return SearchQuery.saveDateSupported();
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

        return SearchQuery.uid(ranges.toArray(SearchQuery.UidRange[]::new));
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

        return SearchQuery.uid(ranges.toArray(SearchQuery.UidRange[]::new));
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
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return CAPS;
    }

    @Override
    protected MDCBuilder mdc(SearchRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "SEARCH")
            .addToContext("useUid", Boolean.toString(request.isUseUids()))
            .addToContext("searchOperation", request.getSearchOperation().toString());
    }
}
