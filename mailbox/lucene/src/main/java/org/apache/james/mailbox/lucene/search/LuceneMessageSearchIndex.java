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
package org.apache.james.mailbox.lucene.search;

import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.ATTACHMENT_FILE_NAME_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.ATTACHMENT_TEXT_CONTENT_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.BASE_SUBJECT_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.BODY_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.FIRST_CC_MAILBOX_NAME_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.FIRST_FROM_MAILBOX_NAME_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.FIRST_TO_MAILBOX_NAME_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.FLAGS_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.HAS_ATTACHMENT_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.HEADERS_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.ID_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.INTERNAL_DATE_FIELD_DAY_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.INTERNAL_DATE_FIELD_HOUR_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.INTERNAL_DATE_FIELD_MILLISECOND_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.INTERNAL_DATE_FIELD_MINUTE_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.INTERNAL_DATE_FIELD_MONTH_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.INTERNAL_DATE_FIELD_SECOND_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.INTERNAL_DATE_FIELD_YEAR_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.MAILBOX_ID_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.MESSAGE_ID_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.MODSEQ_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.PREFIX_HEADER_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SAVE_DATE_FIELD_DAY_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SAVE_DATE_FIELD_HOUR_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SAVE_DATE_FIELD_MINUTE_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SAVE_DATE_FIELD_MONTH_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SAVE_DATE_FIELD_SECOND_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SAVE_DATE_FIELD_YEAR_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SENT_DATE_FIELD_DAY_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SENT_DATE_FIELD_HOUR_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SENT_DATE_FIELD_MINUTE_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SENT_DATE_FIELD_MONTH_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SENT_DATE_FIELD_SECOND_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SENT_DATE_FIELD_YEAR_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SENT_DATE_SORT_FIELD_MILLISECOND_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SIZE_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.THREAD_ID_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.UID_FIELD;
import static org.apache.james.mailbox.lucene.search.LuceneIndexableDocument.createFlagsIdField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxManager.SearchCapabilities;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedSearchException;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.SearchOptions;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AttachmentCriterion;
import org.apache.james.mailbox.model.SearchQuery.ContainsOperator;
import org.apache.james.mailbox.model.SearchQuery.Criterion;
import org.apache.james.mailbox.model.SearchQuery.CustomFlagCriterion;
import org.apache.james.mailbox.model.SearchQuery.DateOperator;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.mailbox.model.SearchQuery.FlagCriterion;
import org.apache.james.mailbox.model.SearchQuery.HeaderCriterion;
import org.apache.james.mailbox.model.SearchQuery.NumericOperator;
import org.apache.james.mailbox.model.SearchQuery.UidCriterion;
import org.apache.james.mailbox.model.SearchQuery.UidRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.SearchUtil;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexFormatTooOldException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortedSetSortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.grouping.GroupDocs;
import org.apache.lucene.search.grouping.GroupingSearch;
import org.apache.lucene.search.grouping.TermGroupSelector;
import org.apache.lucene.search.grouping.TopGroups;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Lucene based {@link ListeningMessageSearchIndex} which offers message searching via a Lucene index
 */
public class LuceneMessageSearchIndex extends ListeningMessageSearchIndex {
    private static final Logger log = LoggerFactory.getLogger(LuceneMessageSearchIndex.class);

    public static class LuceneMessageSearchIndexGroup extends org.apache.james.events.Group {

    }

    private static final Date MAX_DATE;
    private static final Date MIN_DATE;
    public static final org.apache.james.events.Group GROUP = new LuceneMessageSearchIndexGroup();

    static {
        Calendar cal = Calendar.getInstance();
        cal.set(9999, Calendar.DECEMBER, 31);
        MAX_DATE = cal.getTime();

        cal.set(0, Calendar.JANUARY, 1);
        MIN_DATE = cal.getTime();
    }

    /**
     * Default max queryBuilder results
     */
    private static final int DEFAULT_MAX_QUERY_RESULTS = 100000;

    private static final boolean INCLUDE_LOWER = true;
    private static final boolean INCLUDE_UPPER = true;

    private static final SortField UID_SORT = new SortField(UID_FIELD, SortField.Type.LONG);
    private static final SortField UID_SORT_REVERSE = new SortField(UID_FIELD, SortField.Type.LONG, true);

    private static final SortField SIZE_SORT = new SortField(SIZE_FIELD, SortField.Type.LONG);
    private static final SortField SIZE_SORT_REVERSE = new SortField(SIZE_FIELD, SortField.Type.LONG, true);

    private static final SortField FIRST_CC_MAILBOX_SORT = new SortedSetSortField(FIRST_CC_MAILBOX_NAME_FIELD, false);
    private static final SortField FIRST_CC_MAILBOX_SORT_REVERSE = new SortedSetSortField(FIRST_CC_MAILBOX_NAME_FIELD, true);

    private static final SortField FIRST_TO_MAILBOX_SORT = new SortedSetSortField(FIRST_TO_MAILBOX_NAME_FIELD, false);
    private static final SortField FIRST_TO_MAILBOX_SORT_REVERSE = new SortedSetSortField(FIRST_TO_MAILBOX_NAME_FIELD, true);

    private static final SortField FIRST_FROM_MAILBOX_SORT = new SortedSetSortField(FIRST_FROM_MAILBOX_NAME_FIELD, false);
    private static final SortField FIRST_FROM_MAILBOX_SORT_REVERSE = new SortedSetSortField(FIRST_FROM_MAILBOX_NAME_FIELD, true);


    private static final SortField ARRIVAL_MAILBOX_SORT = new SortField(INTERNAL_DATE_FIELD_MILLISECOND_RESOLUTION, SortField.Type.LONG);
    private static final SortField ARRIVAL_MAILBOX_SORT_REVERSE = new SortField(INTERNAL_DATE_FIELD_MILLISECOND_RESOLUTION, SortField.Type.LONG, true);

    private static final SortField BASE_SUBJECT_SORT = new SortedSetSortField(BASE_SUBJECT_FIELD, false);
    private static final SortField BASE_SUBJECT_SORT_REVERSE = new SortedSetSortField(BASE_SUBJECT_FIELD, true);

    private static final SortField SENT_DATE_SORT = new SortField(SENT_DATE_SORT_FIELD_MILLISECOND_RESOLUTION, SortField.Type.LONG);
    private static final SortField SENT_DATE_SORT_REVERSE = new SortField(SENT_DATE_SORT_FIELD_MILLISECOND_RESOLUTION, SortField.Type.LONG, true);

    private final MailboxId.Factory mailboxIdFactory;
    private final MessageId.Factory messageIdFactory;
    private final LuceneIndexableDocument indexableDocument;

    @VisibleForTesting
    final IndexWriter writer;
    private final Directory directory;

    private int maxQueryResults = DEFAULT_MAX_QUERY_RESULTS;

    private boolean suffixMatch = false;

    @Inject
    public LuceneMessageSearchIndex(
        MailboxSessionMapperFactory factory,
        MailboxId.Factory mailboxIdFactory,
        Directory directory,
        MessageId.Factory messageIdFactory,
        SessionProvider sessionProvider,
        TextExtractor textExtractor) throws IOException {
        this(factory, mailboxIdFactory, directory, false, messageIdFactory, sessionProvider, textExtractor);
    }

    public LuceneMessageSearchIndex(
            MailboxSessionMapperFactory factory,
            MailboxId.Factory mailboxIdFactory,
            Directory directory,
            boolean dropIndexOnStart,
            MessageId.Factory messageIdFactory,
            SessionProvider sessionProvider,
            TextExtractor textExtractor) throws IOException {
        super(factory, ImmutableSet.of(), sessionProvider);
        this.mailboxIdFactory = mailboxIdFactory;
        this.messageIdFactory = messageIdFactory;
        this.indexableDocument = new LuceneIndexableDocument(textExtractor);
        this.directory = directory;
        try {
            this.writer = new IndexWriter(this.directory, createConfig(LenientImapSearchAnalyzer.INSTANCE, dropIndexOnStart));
        } catch (IndexFormatTooOldException e) {
            throw new RuntimeException("Old lucene index version detected, automatic migration is not supported. See https://github.com/apache/james-project/blob/master/upgrade-instructions.md#james-4046-refactor-and-update-apache-james-mailbox-lucene for details", e);
        }
    }

    @PreDestroy
    public void close() throws IOException {
        log.trace("Closing Lucene index");
        writer.commit();
        writer.close();
    }

    @Override
    public org.apache.james.events.Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public EnumSet<SearchCapabilities> getSupportedCapabilities(EnumSet<MailboxManager.MessageCapabilities> messageCapabilities) {
        return EnumSet.of(SearchCapabilities.MultimailboxSearch,
            SearchCapabilities.Text,
            SearchCapabilities.FullText,
            SearchCapabilities.AttachmentFileName,
            SearchCapabilities.Attachment,
            SearchCapabilities.HighlightSearch);
    }

    /**
     * Set the max count of results which will get returned from a query. The default is {@link #DEFAULT_MAX_QUERY_RESULTS}
     */
    public void setMaxQueryResults(int maxQueryResults) {
        this.maxQueryResults = maxQueryResults;
    }

    protected IndexWriterConfig createConfig(Analyzer analyzer, boolean dropIndexOnStart) {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        if (dropIndexOnStart) {
            config.setOpenMode(OpenMode.CREATE);
        } else {
            config.setOpenMode(OpenMode.CREATE_OR_APPEND);
        }
        return config;
    }

    /**
     * If set to true this implementation will use {@link WildcardQuery} to match suffix and prefix. This is what RFC3501 expects but is often not what the user does.
     * It also slow things a lot if you have complex queries which use many "TEXT" arguments. If you want the implementation to behave strict like RFC3501 says, you should
     * set this to true.
     * <p>
     * The default is false for performance reasons
     */
    public void setEnableSuffixMatch(boolean suffixMatch) {
        this.suffixMatch = suffixMatch;
    }

    @Override
    public Flux<MessageUid> doSearch(MailboxSession session, Mailbox mailbox, SearchQuery searchQuery) throws MailboxException {
        Preconditions.checkArgument(session != null, "'session' is mandatory");

        return Flux.fromIterable(searchMultimap(ImmutableList.of(mailbox.getMailboxId()), searchQuery))
            .map(SearchResult::getMessageUid);
    }

    @Override
    public Flux<MessageId> search(MailboxSession session, Collection<MailboxId> mailboxIds, SearchQuery searchQuery, SearchOptions searchOptions) throws MailboxException {
        Preconditions.checkArgument(session != null, "'session' is mandatory");
        if (mailboxIds.isEmpty()) {
            return Flux.empty();
        }

        if (searchQuery.shouldCollapseThreads()) {
            return searchCollapseThreads(mailboxIds, searchQuery, searchOptions);
        }
        return searchWithoutCollapseThreads(mailboxIds, searchQuery, searchOptions);
    }

    private Flux<MessageId> searchWithoutCollapseThreads(Collection<MailboxId> mailboxIds, SearchQuery searchQuery, SearchOptions searchOptions) throws MailboxException {
        long requestedLimit = Math.addExact(searchOptions.offset().getOffset(), searchOptions.limit().getLimit().orElseThrow());

        return Flux.fromIterable(searchMultimap(mailboxIds, searchQuery)
            .stream()
            .filter(searchResult -> searchResult.getMessageId().isPresent())
            .map(searchResult -> searchResult.getMessageId().get())
            .filter(SearchUtil.distinct())
            .limit(requestedLimit)
            .skip(searchOptions.offset().getOffset())
            .collect(ImmutableList.toImmutableList()));
    }

    private Flux<MessageId> searchCollapseThreads(Collection<MailboxId> mailboxIds, SearchQuery searchQuery, SearchOptions searchOptions) throws MailboxException {
        Query query = buildQuery(mailboxIds, searchQuery);

        try (IndexReader reader = DirectoryReader.open(writer)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            GroupingSearch groupingSearch = new GroupingSearch(new TermGroupSelector(THREAD_ID_FIELD));
            Sort sort = createSort(searchQuery.getSorts());
            groupingSearch.setGroupSort(sort);
            groupingSearch.setSortWithinGroup(sort);
            // get the first message of each thread group
            groupingSearch.setGroupDocsOffset(0);
            groupingSearch.setGroupDocsLimit(1);

            int groupOffset = Math.toIntExact(searchOptions.offset().getOffset());
            int topNGroups = Math.toIntExact(searchOptions.limit().getLimit().orElseThrow());

            TopGroups<BytesRef> topGroups = groupingSearch.search(searcher, query, groupOffset, topNGroups);
            List<MessageId> result = new ArrayList<>(topGroups.groups.length);
            for (GroupDocs<BytesRef> group : topGroups.groups) {
                ScoreDoc[] scoreDocs = group.scoreDocs();
                Document document = searcher.storedFields().document(scoreDocs[0].doc);
                documentToSearchResult(document).getMessageId().ifPresent(result::add);
            }
            return Flux.fromIterable(result);
        } catch (IOException e) {
            throw new MailboxException("Unable to search the mailbox", e);
        }
    }

    private List<SearchResult> searchMultimap(Collection<MailboxId> mailboxIds, SearchQuery searchQuery) throws MailboxException {
        return searchDocument(mailboxIds, searchQuery, maxQueryResults)
            .stream()
            .map(this::documentToSearchResult)
            .toList();
    }

    private SearchResult documentToSearchResult(Document doc) {
        MessageUid uid = MessageUid.of(doc.getField(UID_FIELD).numericValue().longValue());
        MailboxId mailboxId = mailboxIdFactory.fromString(doc.get(MAILBOX_ID_FIELD));
        Optional<MessageId> messageId = Optional.ofNullable(doc.get(MESSAGE_ID_FIELD))
            .map(messageIdFactory::fromString);
        return new SearchResult(messageId, mailboxId, uid);
    }

    public List<Document> searchDocument(Collection<MailboxId> mailboxIds, SearchQuery searchQuery, int maxQueryResults) throws MailboxException {
        try (IndexReader reader = DirectoryReader.open(writer)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Query query = buildQuery(mailboxIds, searchQuery);

            // query for all the documents sorted as specified in the SearchQuery
            TopDocs docs = searcher.search(query, maxQueryResults, createSort(searchQuery.getSorts()));

            return Stream.of(docs.scoreDocs)
                .map(Throwing.function(sDoc -> searcher.storedFields().document(sDoc.doc)))
                .toList();
        } catch (IOException e) {
            throw new MailboxException("Unable to search the mailbox", e);
        }
    }

    private Query buildQueryFromMailboxes(Collection<MailboxId> mailboxIds) {
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
        for (MailboxId id: mailboxIds) {
            String idAsString = id.serialize();
            queryBuilder.add(new TermQuery(new Term(MAILBOX_ID_FIELD, idAsString)), BooleanClause.Occur.SHOULD);
        }
        return queryBuilder.build();
    }

    private Query buildQuery(Collection<MailboxId> mailboxIds, SearchQuery searchQuery) throws MailboxException {
        Query inMailboxes = buildQueryFromMailboxes(mailboxIds);
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
        queryBuilder.add(inMailboxes, BooleanClause.Occur.MUST);
        // Not return flags documents
        queryBuilder.add(new PrefixQuery(new Term(FLAGS_FIELD, "")), BooleanClause.Occur.MUST_NOT);

        List<Criterion> crits = searchQuery.getCriteria();
        for (Criterion crit : crits) {
            queryBuilder.add(createQuery(crit, inMailboxes, searchQuery.getRecentMessageUids()), BooleanClause.Occur.MUST);
        }
        return queryBuilder.build();
    }

    private String toSentDateField(DateResolution res) {
        return switch (res) {
            case Year -> SENT_DATE_FIELD_YEAR_RESOLUTION;
            case Month -> SENT_DATE_FIELD_MONTH_RESOLUTION;
            case Day -> SENT_DATE_FIELD_DAY_RESOLUTION;
            case Hour -> SENT_DATE_FIELD_HOUR_RESOLUTION;
            case Minute -> SENT_DATE_FIELD_MINUTE_RESOLUTION;
            case Second -> SENT_DATE_FIELD_SECOND_RESOLUTION;
        };
    }

    private String toInteralDateField(DateResolution res) {
        return switch (res) {
            case Year -> INTERNAL_DATE_FIELD_YEAR_RESOLUTION;
            case Month -> INTERNAL_DATE_FIELD_MONTH_RESOLUTION;
            case Day -> INTERNAL_DATE_FIELD_DAY_RESOLUTION;
            case Hour -> INTERNAL_DATE_FIELD_HOUR_RESOLUTION;
            case Minute -> INTERNAL_DATE_FIELD_MINUTE_RESOLUTION;
            case Second -> INTERNAL_DATE_FIELD_SECOND_RESOLUTION;
        };
    }

    private String toSaveDateField(DateResolution res) {
        return switch (res) {
            case Year -> SAVE_DATE_FIELD_YEAR_RESOLUTION;
            case Month -> SAVE_DATE_FIELD_MONTH_RESOLUTION;
            case Day -> SAVE_DATE_FIELD_DAY_RESOLUTION;
            case Hour -> SAVE_DATE_FIELD_HOUR_RESOLUTION;
            case Minute -> SAVE_DATE_FIELD_MINUTE_RESOLUTION;
            case Second -> SAVE_DATE_FIELD_SECOND_RESOLUTION;
        };
    }

    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.InternalDateCriterion}
     */
    private Query createInternalDateQuery(SearchQuery.InternalDateCriterion crit) {
        DateOperator dop = crit.getOperator();
        DateResolution res = dop.getDateResultion();
        String field = toInteralDateField(res);
        return createQuery(field, dop);
    }

    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.SaveDateCriterion}
     */
    private Query createSaveDateQuery(SearchQuery.SaveDateCriterion crit) {
        DateOperator dop = crit.getOperator();
        DateResolution res = dop.getDateResultion();
        String field = toSaveDateField(res);
        return createQuery(field, dop);
    }

    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.SizeCriterion}
     */
    private Query createSizeQuery(SearchQuery.SizeCriterion crit) {
        NumericOperator op = crit.getOperator();
        return switch (op.getType()) {
            case EQUALS -> LongPoint.newExactQuery(SIZE_FIELD, op.getValue());
            case GREATER_THAN -> LongPoint.newRangeQuery(SIZE_FIELD, op.getValue() + 1, Long.MAX_VALUE);
            case LESS_THAN -> LongPoint.newRangeQuery(SIZE_FIELD, Long.MIN_VALUE, op.getValue() - 1);
        };
    }

    /**
     * This method will return the right {@link Query} depending if {@link #suffixMatch} is enabled
     */
    private Query createTermQuery(String fieldName, String value) {
        if (suffixMatch) {
            return new WildcardQuery(new Term(fieldName, "*" + value + "*"));
        } else {
            return new PrefixQuery(new Term(fieldName, value));
        }
    }

    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.HeaderCriterion}
     */
    private Query createHeaderQuery(SearchQuery.HeaderCriterion crit) throws UnsupportedSearchException {
        if (crit.getOperator() == null) {
            throw new UnsupportedSearchException();
        }

        String name = crit.getHeaderName().toUpperCase(Locale.US);
        String fieldName = PREFIX_HEADER_FIELD + name;
        return switch (crit.getOperator()) {
            case ContainsOperator cop -> createTermQuery(fieldName, cop.getValue().toUpperCase(Locale.US));
            case SearchQuery.ExistsOperator existsOperator -> new PrefixQuery(new Term(fieldName, StringUtils.EMPTY));
            case DateOperator dop -> createQuery(toSentDateField(dop.getDateResultion()), dop);
            case SearchQuery.AddressOperator addressOperator -> createTermQuery(name.toLowerCase(Locale.US), addressOperator.getAddress().toUpperCase(Locale.US));
            default -> throw new UnsupportedSearchException();
        };
    }


    private Query createQuery(String field, DateOperator dop) {
        Date date = dop.getDate();
        DateResolution res = dop.getDateResultion();
        DateTools.Resolution dRes = toResolution(res);
        String value = DateTools.dateToString(date, dRes);
        return switch (dop.getType()) {
            case ON -> new TermQuery(new Term(field, value));
            case BEFORE -> TermRangeQuery.newStringRange(field, DateTools.dateToString(MIN_DATE, dRes), value, INCLUDE_LOWER, !INCLUDE_UPPER);
            case AFTER -> TermRangeQuery.newStringRange(field, value, DateTools.dateToString(MAX_DATE, dRes), !INCLUDE_LOWER, INCLUDE_UPPER);
        };
    }

    private DateTools.Resolution toResolution(DateResolution res) {
        return switch (res) {
            case Year -> DateTools.Resolution.YEAR;
            case Month -> DateTools.Resolution.MONTH;
            case Day -> DateTools.Resolution.DAY;
            case Hour -> DateTools.Resolution.HOUR;
            case Minute -> DateTools.Resolution.MINUTE;
            case Second -> DateTools.Resolution.SECOND;
        };
    }

    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.UidCriterion}
     */
    private Query createUidQuery(SearchQuery.UidCriterion crit) {
        UidRange[] ranges = crit.getOperator().getRange();
        if (ranges.length == 1) {
            UidRange range = ranges[0];
            return LongPoint.newRangeQuery(UID_FIELD, range.getLowValue().asLong(), range.getHighValue().asLong());
        } else {
            BooleanQuery.Builder rangesQuery = new BooleanQuery.Builder();
            for (UidRange range : ranges) {
                rangesQuery.add(LongPoint.newRangeQuery(UID_FIELD, range.getLowValue().asLong(), range.getHighValue().asLong()), BooleanClause.Occur.SHOULD);
            }
            return rangesQuery.build();
        }
    }


    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.UidCriterion}
     */
    private Query createModSeqQuery(SearchQuery.ModSeqCriterion crit) {
        NumericOperator op = crit.getOperator();
        return switch (op.getType()) {
            case EQUALS -> LongPoint.newRangeQuery(MODSEQ_FIELD, op.getValue(), op.getValue());
            case GREATER_THAN -> LongPoint.newRangeQuery(MODSEQ_FIELD, op.getValue(), Long.MAX_VALUE);
            case LESS_THAN -> LongPoint.newRangeQuery(MODSEQ_FIELD, Long.MIN_VALUE, op.getValue());
        };
    }

    private Query createAttachmentQuery(boolean isSet) {
        return new TermQuery(new Term(HAS_ATTACHMENT_FIELD, Boolean.toString(isSet)));
    }

    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.FlagCriterion}. This is kind of a hack
     * as it will do a search for the flags in this method and
     */
    private Query createFlagQuery(String flag, boolean isSet, Query inMailboxes, Collection<MessageUid> recentUids) throws MailboxException {
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

        if (isSet) {
            queryBuilder.add(new TermQuery(new Term(FLAGS_FIELD, flag)), BooleanClause.Occur.MUST);
        } else {
            // lucene does not support simple NOT queries so we do some nasty hack here
            BooleanQuery.Builder bQuery = new BooleanQuery.Builder();
            bQuery.add(new PrefixQuery(new Term(FLAGS_FIELD, "")), BooleanClause.Occur.MUST);
            bQuery.add(new TermQuery(new Term(FLAGS_FIELD, flag)),BooleanClause.Occur.MUST_NOT);

            queryBuilder.add(bQuery.build(), BooleanClause.Occur.MUST);
        }
        queryBuilder.add(inMailboxes, BooleanClause.Occur.MUST);

        try (IndexReader reader = DirectoryReader.open(writer)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Set<MessageUid> uids = new HashSet<>();

            // queryBuilder for all the documents sorted by uid
            TopDocs docs = searcher.search(queryBuilder.build(), maxQueryResults, new Sort(UID_SORT));
            ScoreDoc[] sDocs = docs.scoreDocs;
            for (ScoreDoc sDoc : sDocs) {
                MessageUid uid = MessageUid.of(searcher.storedFields().document(sDoc.doc).getField(UID_FIELD).numericValue().longValue());
                uids.add(uid);
            }

            // add or remove recent uids
            if (flag.equalsIgnoreCase("\\RECENT")) {
                if (isSet) {
                    uids.addAll(recentUids);
                } else {
                    uids.removeAll(recentUids);
                }
            }

            List<MessageRange> ranges = MessageRange.toRanges(new ArrayList<>(uids));
            UidRange[] nRanges = new UidRange[ranges.size()];
            for (int i = 0; i < ranges.size(); i++) {
                MessageRange range = ranges.get(i);
                nRanges[i] = new UidRange(range.getUidFrom(), range.getUidTo());
            }
            return createUidQuery((UidCriterion) SearchQuery.uid(nRanges));
        } catch (IOException e) {
            throw new MailboxException("Unable to search mailbox " + inMailboxes, e);
        }
    }

    private Sort createSort(List<SearchQuery.Sort> sorts) {
        List<SortField> fields = new ArrayList<>();

        for (SearchQuery.Sort sort : sorts) {
            boolean reverse = sort.isReverse();
            SortField sortField = createSortField(sort, reverse);
            if (sortField != null) {

                fields.add(sortField);

                // Add the uid sort as tie-breaker
                if (sortField == SENT_DATE_SORT) {
                    fields.add(UID_SORT);
                } else if (sortField == SENT_DATE_SORT_REVERSE) {
                    fields.add(UID_SORT_REVERSE);
                }
            }
        }
        // add the uid sorting as last so if no other sorting was able to do the job it will get sorted by the uid
        fields.add(UID_SORT);
        return new Sort(fields.toArray(SortField[]::new));
    }

    private SortField createSortField(SearchQuery.Sort s, boolean reverse) {
        if (s.getSortClause() == null) {
            return null;
        }
        if (reverse) {
            return switch (s.getSortClause()) {
                case Arrival -> ARRIVAL_MAILBOX_SORT_REVERSE;
                case SentDate -> SENT_DATE_SORT_REVERSE;
                case MailboxCc -> FIRST_CC_MAILBOX_SORT_REVERSE;
                case MailboxFrom -> FIRST_FROM_MAILBOX_SORT_REVERSE;
                case Size -> SIZE_SORT_REVERSE;
                case BaseSubject -> BASE_SUBJECT_SORT_REVERSE;
                case MailboxTo -> FIRST_TO_MAILBOX_SORT_REVERSE;
                case Uid -> UID_SORT_REVERSE;
                default -> null;
            };
        }
        return switch (s.getSortClause()) {
            case Arrival -> ARRIVAL_MAILBOX_SORT;
            case SentDate -> SENT_DATE_SORT;
            case MailboxCc -> FIRST_CC_MAILBOX_SORT;
            case MailboxFrom -> FIRST_FROM_MAILBOX_SORT;
            case Size -> SIZE_SORT;
            case BaseSubject -> BASE_SUBJECT_SORT;
            case MailboxTo -> FIRST_TO_MAILBOX_SORT;
            case Uid -> UID_SORT;
            default -> null;
        };
    }

    /**
     * Convert the given {@link Flag} to a String
     */
    private String toString(Flag flag) {
        if (Flag.ANSWERED.equals(flag)) {
            return "\\ANSWERED";
        } else if (Flag.DELETED.equals(flag)) {
            return "\\DELETED";
        } else if (Flag.DRAFT.equals(flag)) {
            return "\\DRAFT";
        } else if (Flag.FLAGGED.equals(flag)) {
            return "\\FLAGGED";
        } else if (Flag.RECENT.equals(flag)) {
            return "\\RECENT";
        } else if (Flag.SEEN.equals(flag)) {
            return "\\FLAG";
        } else {
            return flag.toString();
        }
    }

    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.TextCriterion}
     */
    private Query createTextQuery(SearchQuery.TextCriterion crit) {
        String value = crit.getOperator().getValue().toUpperCase(Locale.US);
        return switch (crit.getType()) {
            case BODY -> createTermQuery(BODY_FIELD, value);
            case ATTACHMENTS -> createTermQuery(ATTACHMENT_TEXT_CONTENT_FIELD, value);
            case ATTACHMENT_FILE_NAME -> createTermQuery(ATTACHMENT_FILE_NAME_FIELD, value);
            case FULL -> {
                BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
                queryBuilder.add(createTermQuery(BODY_FIELD, value), BooleanClause.Occur.SHOULD);
                queryBuilder.add(createTermQuery(HEADERS_FIELD, value), BooleanClause.Occur.SHOULD);
                queryBuilder.add(createTermQuery(ATTACHMENT_TEXT_CONTENT_FIELD, value), BooleanClause.Occur.SHOULD);
                yield queryBuilder.build();
            }
        };
    }

    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.AllCriterion}
     */
    private Query createAllQuery() {
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

        queryBuilder.add(createQuery(MessageRange.all()), BooleanClause.Occur.MUST);
        queryBuilder.add(new PrefixQuery(new Term(FLAGS_FIELD, "")), BooleanClause.Occur.MUST_NOT);

        return queryBuilder.build();
    }

    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.ConjunctionCriterion}
     */
    private Query createConjunctionQuery(SearchQuery.ConjunctionCriterion crit, Query inMailboxes, Collection<MessageUid> recentUids) throws MailboxException {
        List<Criterion> crits = crit.getCriteria();
        BooleanQuery.Builder conQuery = new BooleanQuery.Builder();
        switch (crit.getType()) {
        case AND:
            for (Criterion criterion : crits) {
                conQuery.add(createQuery(criterion, inMailboxes, recentUids), BooleanClause.Occur.MUST);
            }
            return conQuery.build();
        case OR:
            for (Criterion criterion : crits) {
                conQuery.add(createQuery(criterion, inMailboxes, recentUids), BooleanClause.Occur.SHOULD);
            }
            return conQuery.build();
        case NOR:
            BooleanQuery.Builder nor = new BooleanQuery.Builder();
            for (Criterion criterion : crits) {
                conQuery.add(createQuery(criterion, inMailboxes, recentUids), BooleanClause.Occur.SHOULD);
            }
            nor.add(inMailboxes, BooleanClause.Occur.MUST);

            nor.add(conQuery.build(), BooleanClause.Occur.MUST_NOT);
            return nor.build();
        default:
            throw new UnsupportedSearchException();
        }
    }

    /**
     * Return a {@link Query} which is builded based on the given {@link Criterion}
     */
    private Query createQuery(Criterion criterion, Query inMailboxes, Collection<MessageUid> recentUids) throws MailboxException {
        if (criterion instanceof SearchQuery.InternalDateCriterion crit) {
            return createInternalDateQuery(crit);
        } else if (criterion instanceof SearchQuery.SaveDateCriterion crit) {
            return createSaveDateQuery(crit);
        } else if (criterion instanceof SearchQuery.SizeCriterion crit) {
            return createSizeQuery(crit);
        }  else if (criterion instanceof SearchQuery.MessageIdCriterion crit) {
            return new TermQuery(new Term(MESSAGE_ID_FIELD, crit.getMessageId().serialize()));
        } else if (criterion instanceof HeaderCriterion crit) {
            return createHeaderQuery(crit);
        } else if (criterion instanceof UidCriterion crit) {
            return createUidQuery(crit);
        } else if (criterion instanceof FlagCriterion crit) {
            return createFlagQuery(toString(crit.getFlag()), crit.getOperator().isSet(), inMailboxes, recentUids);
        } else if (criterion instanceof AttachmentCriterion crit) {
            return createAttachmentQuery(crit.getOperator().isSet());
        } else if (criterion instanceof CustomFlagCriterion crit) {
            return createFlagQuery(crit.getFlag().toLowerCase(Locale.US), crit.getOperator().isSet(), inMailboxes, recentUids);
        } else if (criterion instanceof SearchQuery.TextCriterion crit) {
            return createTextQuery(crit);
        } else if (criterion instanceof SearchQuery.AllCriterion) {
            return createAllQuery();
        } else if (criterion instanceof SearchQuery.ConjunctionCriterion crit) {
            return createConjunctionQuery(crit, inMailboxes, recentUids);
        } else if (criterion instanceof SearchQuery.ModSeqCriterion) {
            return createModSeqQuery((SearchQuery.ModSeqCriterion) criterion);
        } else if (criterion instanceof SearchQuery.MimeMessageIDCriterion mimeMessageIDCriterion) {
            return createHeaderQuery(mimeMessageIDCriterion.asHeaderCriterion());
        } else if (criterion instanceof SearchQuery.SubjectCriterion subjectCriterion) {
            return createHeaderQuery(subjectCriterion.asHeaderCriterion());
        } else if (criterion instanceof SearchQuery.ThreadIdCriterion threadIdCriterion) {
            return createTermQuery(THREAD_ID_FIELD, threadIdCriterion.getThreadId().serialize());
        }
        throw new UnsupportedSearchException();
    }

    @Override
    public Mono<Void> add(MailboxSession session, Mailbox mailbox, MailboxMessage membership) {
        return Mono.fromCallable(() -> retrieveFlags(mailbox, membership.getUid()))
            .filter(flags -> !new Flags().equals(flags))
            .flatMap(any -> Mono.fromRunnable(Throwing.runnable(() -> update(mailbox.getMailboxId(), membership.getUid(), membership.createFlags()))))
            .switchIfEmpty(Mono.defer(() -> indexableDocument.createMessageDocument(membership, session)
                .flatMap(document -> Mono.fromRunnable(Throwing.runnable(() -> {
                    writer.addDocument(document);
                    writer.addDocument(indexableDocument.createFlagsDocument(membership));
                })))))
            .then();
    }

    @Override
    public Mono<Void> update(MailboxSession session, MailboxId mailboxId, List<UpdatedFlags> updatedFlagsList) {
        return Mono.fromRunnable(Throwing.runnable(() -> {
            for (UpdatedFlags updatedFlags : updatedFlagsList) {
                update(mailboxId, updatedFlags.getUid(), updatedFlags.getNewFlags());
            }
        }));
    }

    private void update(MailboxId mailboxId, MessageUid uid, Flags f) throws IOException {
        var flagsID = createFlagsIdField(mailboxId, uid);
        var term = new Term(ID_FIELD, flagsID);
        var doc = indexableDocument.createFlagsDocument(mailboxId, uid, f);
        log.trace("Updating flags document, mailboxId:{}, message uid: {}, flags:'{}', term: {}, new document: {}",
                mailboxId, uid, f, term, doc);
        writer.updateDocument(term, doc);
    }

    private Query createQuery(MessageRange range) {
        return switch (range.getType()) {
            case ONE -> LongPoint.newRangeQuery(UID_FIELD,
                range.getUidFrom().asLong(),
                range.getUidTo().asLong());
            case FROM -> LongPoint.newRangeQuery(UID_FIELD,
                range.getUidFrom().asLong(),
                MessageUid.MAX_VALUE.asLong());
            default -> LongPoint.newRangeQuery(UID_FIELD, MessageUid.MIN_VALUE.asLong(), MessageUid.MAX_VALUE.asLong());
        };
    }

    @Override
    public Mono<Void> delete(MailboxSession session, MailboxId mailboxId, Collection<MessageUid> expungedUids) {
        return Mono.fromRunnable(Throwing.runnable(() -> MessageRange.toRanges(expungedUids)
            .forEach(Throwing.<MessageRange>consumer(messageRange -> delete(mailboxId, messageRange))
                .sneakyThrow())));
    }

    @Override
    public Mono<Void> deleteAll(MailboxSession session, MailboxId mailboxId) {
        return Mono.fromRunnable(Throwing.runnable(() -> delete(mailboxId, MessageRange.all())));
    }

    public void delete(MailboxId mailboxId, MessageRange range) throws IOException {
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
        queryBuilder.add(new TermQuery(new Term(MAILBOX_ID_FIELD, mailboxId.serialize())), BooleanClause.Occur.MUST);
        queryBuilder.add(createQuery(range), BooleanClause.Occur.MUST);

        writer.deleteDocuments(queryBuilder.build());
    }

    public void commit() throws IOException {
        writer.commit();
    }

    @Override
    public void postReindexing() {
        try {
            commit();
        } catch (IOException e) {
            throw new RuntimeException("Error while commiting to index", e);
        }
    }

    @Override
    public Mono<Flags> retrieveIndexedFlags(Mailbox mailbox, MessageUid uid) {
        return Mono.fromCallable(() -> retrieveFlags(mailbox, uid));
    }

    private Flags retrieveFlags(Mailbox mailbox, MessageUid uid) throws IOException {
        try (IndexReader reader = DirectoryReader.open(writer)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Flags retrievedFlags = new Flags();

            BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
            queryBuilder.add(new TermQuery(new Term(MAILBOX_ID_FIELD, mailbox.getMailboxId().serialize())), BooleanClause.Occur.MUST);
            queryBuilder.add(createQuery(MessageRange.one(uid)), BooleanClause.Occur.MUST);
            queryBuilder.add(new PrefixQuery(new Term(FLAGS_FIELD, "")), BooleanClause.Occur.MUST);

            TopDocs docs = searcher.search(queryBuilder.build(), 100000);
            ScoreDoc[] sDocs = docs.scoreDocs;
            for (ScoreDoc sDoc : sDocs) {
                Document doc = searcher.storedFields().document(sDoc.doc);

                Stream.of(doc.getValues(FLAGS_FIELD))
                    .forEach(flag -> fromString(flag).ifPresentOrElse(retrievedFlags::add, () -> retrievedFlags.add(flag)));
            }
            return retrievedFlags;
        }
    }

    /**
     * Convert the given {@link Flag} to a String
     */
    private Optional<Flag> fromString(String flag) {
        return switch (flag) {
            case "\\ANSWERED" -> Optional.of(Flag.ANSWERED);
            case "\\DELETED" -> Optional.of(Flag.DELETED);
            case "\\DRAFT" -> Optional.of(Flag.DRAFT);
            case "\\FLAGGED" -> Optional.of(Flag.FLAGGED);
            case "\\RECENT" -> Optional.of(Flag.RECENT);
            case "\\FLAG" -> Optional.of(Flag.SEEN);
            default -> Optional.empty();
        };
    }
}
