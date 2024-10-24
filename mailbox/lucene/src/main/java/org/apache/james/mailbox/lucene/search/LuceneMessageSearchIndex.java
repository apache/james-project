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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxManager.SearchCapabilities;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedSearchException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
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
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Group;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.dom.field.DateTimeField;
import org.apache.james.mime4j.field.address.AddressFormatter;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.mime4j.message.SimpleContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
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

    /**
     * {@link Field} which will contain the unique index of the {@link Document}
     */
    @VisibleForTesting
    static final String ID_FIELD = "id";


    /**
     * {@link Field} which will contain uid of the {@link MailboxMessage}
     */
    static final String UID_FIELD = "uid";

    /**
     * {@link Field} boolean field that say if the message as an attachment or not
     */
    private static final String HAS_ATTACHMENT_FIELD = "hasAttachment";

    /**
     * {@link Field} which will contain the {@link Flags} of the {@link MailboxMessage}
     */
    static final String FLAGS_FIELD = "flags";

    /**
     * {@link Field} which will contain the size of the {@link MailboxMessage}
     */
    private static final String SIZE_FIELD = "size";

    /**
     * {@link Field} which will contain the body of the {@link MailboxMessage}
     */
    static final String BODY_FIELD = "body";

    /**
     * Prefix which will be used for each message header to store it also in a seperate {@link Field}
     */
    private static final String PREFIX_HEADER_FIELD = "header_";

    /**
     * {@link Field} which will contain the whole message header of the {@link MailboxMessage}
     */
    private static final String HEADERS_FIELD = "headers";

    /**
     * {@link Field} which will contain the mod-sequence of the message
     */
    private static final String MODSEQ_FIELD = "modSeq";

    /**
     * {@link Field} which will contain the threadId of the message
     */
    private static final String THREAD_ID_FIELD = "threadId";

    /**
     * {@link Field} which will contain the TO-Address of the message
     */
    private static final String TO_FIELD = "to";

    private static final String FIRST_TO_MAILBOX_NAME_FIELD = "firstToMailboxName";
    private static final String FIRST_TO_MAILBOX_DISPLAY_FIELD = "firstToMailboxDisplay";

    /**
     * {@link Field} which will contain the CC-Address of the message
     */
    private static final String CC_FIELD = "cc";

    private static final String FIRST_CC_MAILBOX_NAME_FIELD = "firstCcMailboxName";


    /**
     * {@link Field} which will contain the FROM-Address of the message
     */
    private static final String FROM_FIELD = "from";

    private static final String FIRST_FROM_MAILBOX_NAME_FIELD = "firstFromMailboxName";
    private static final String FIRST_FROM_MAILBOX_DISPLAY_FIELD = "firstFromMailboxDisplay";

    /**
     * {@link Field} which will contain the BCC-Address of the message
     */
    private static final String BCC_FIELD = "bcc";


    static final String BASE_SUBJECT_FIELD = "baseSubject";
    static final String SUBJECT_FIELD = "subject";

    /**
     * {@link Field} which contain the internalDate of the message with YEAR-Resolution
     */
    private static final String INTERNAL_DATE_FIELD_YEAR_RESOLUTION = "internaldateYearResolution";


    /**
     * {@link Field} which contain the internalDate of the message with MONTH-Resolution
     */
    private static final String INTERNAL_DATE_FIELD_MONTH_RESOLUTION = "internaldateMonthResolution";

    /**
     * {@link Field} which contain the internalDate of the message with DAY-Resolution
     */
    private static final String INTERNAL_DATE_FIELD_DAY_RESOLUTION = "internaldateDayResolution";

    /**
     * {@link Field} which contain the internalDate of the message with HOUR-Resolution
     */
    private static final String INTERNAL_DATE_FIELD_HOUR_RESOLUTION = "internaldateHourResolution";

    /**
     * {@link Field} which contain the internalDate of the message with MINUTE-Resolution
     */
    private static final String INTERNAL_DATE_FIELD_MINUTE_RESOLUTION = "internaldateMinuteResolution";

    /**
     * {@link Field} which contain the internalDate of the message with SECOND-Resolution
     */
    private static final String INTERNAL_DATE_FIELD_SECOND_RESOLUTION = "internaldateSecondResolution";


    /**
     * {@link Field} which contain the internalDate of the message with MILLISECOND-Resolution
     */
    private static final String INTERNAL_DATE_FIELD_MILLISECOND_RESOLUTION = "internaldateMillisecondResolution";

    /**
     * {@link Field} which contain the saveDate of the message with YEAR-Resolution
     */
    private static final String SAVE_DATE_FIELD_YEAR_RESOLUTION = "saveDateYearResolution";

    /**
     * {@link Field} which contain the saveDate of the message with MONTH-Resolution
     */
    private static final String SAVE_DATE_FIELD_MONTH_RESOLUTION = "saveDateMonthResolution";

    /**
     * {@link Field} which contain the saveDate of the message with DAY-Resolution
     */
    private static final String SAVE_DATE_FIELD_DAY_RESOLUTION = "saveDateDayResolution";

    /**
     * {@link Field} which contain the saveDate of the message with HOUR-Resolution
     */
    private static final String SAVE_DATE_FIELD_HOUR_RESOLUTION = "saveDateHourResolution";

    /**
     * {@link Field} which contain the saveDate of the message with MINUTE-Resolution
     */
    private static final String SAVE_DATE_FIELD_MINUTE_RESOLUTION = "saveDateMinuteResolution";

    /**
     * {@link Field} which contain the saveDate of the message with SECOND-Resolution
     */
    private static final String SAVE_DATE_FIELD_SECOND_RESOLUTION = "saveDateSecondResolution";

    /**
     * {@link Field} which will contain the id of the {@link Mailbox}
     */
    static final String MAILBOX_ID_FIELD = "mailboxid";

    /**
     * {@link Field} which will contain the user of the {@link MailboxSession}
     */
    private static final String USERS = "userSession";
    /**
     * {@link Field} which will contain the id of the {@link MessageId}
     */
    static final String MESSAGE_ID_FIELD = "messageid";

    /**
     * {@link Field} which contain the Date header of the message with YEAR-Resolution
     */
    private static final String SENT_DATE_FIELD_YEAR_RESOLUTION = "sentdateYearResolution";


    /**
     * {@link Field} which contain the Date header of the message with MONTH-Resolution
     */
    private static final String SENT_DATE_FIELD_MONTH_RESOLUTION = "sentdateMonthResolution";

    /**
     * {@link Field} which contain the Date header of the message with DAY-Resolution
     */
    private static final String SENT_DATE_FIELD_DAY_RESOLUTION = "sentdateDayResolution";

    /**
     * {@link Field} which contain the Date header of the message with HOUR-Resolution
     */
    private static final String SENT_DATE_FIELD_HOUR_RESOLUTION = "sentdateHourResolution";

    /**
     * {@link Field} which contain the Date header of the message with MINUTE-Resolution
     */
    private static final String SENT_DATE_FIELD_MINUTE_RESOLUTION = "sentdateMinuteResolution";

    /**
     * {@link Field} which contain the Date header of the message with SECOND-Resolution
     */
    private static final String SENT_DATE_FIELD_SECOND_RESOLUTION = "sentdateSecondResolution";


    /**
     * {@link Field} which contain the Date header of the message with MILLISECOND-Resolution
     */
    private static final String SENT_DATE_FIELD_MILLISECOND_RESOLUTION = "sentdateMillisecondResolution";

    private static final String SENT_DATE_SORT_FIELD_MILLISECOND_RESOLUTION = "sentdateSort";

    private static final String MEDIA_TYPE_TEXT = "text";
    private static final String MEDIA_TYPE_MESSAGE = "message";
    private static final String DEFAULT_ENCODING = "US-ASCII";
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
        SessionProvider sessionProvider) throws IOException {
        this(factory, mailboxIdFactory, directory, false, messageIdFactory, sessionProvider);
    }

    public LuceneMessageSearchIndex(
            MailboxSessionMapperFactory factory,
            MailboxId.Factory mailboxIdFactory,
            Directory directory,
            boolean dropIndexOnStart,
            MessageId.Factory messageIdFactory,
            SessionProvider sessionProvider) throws IOException {
        super(factory, ImmutableSet.of(), sessionProvider);
        this.mailboxIdFactory = mailboxIdFactory;
        this.messageIdFactory = messageIdFactory;
        this.directory = directory;
        try {
            this.writer = new IndexWriter(this.directory, createConfig(LenientImapSearchAnalyzer.INSTANCE, dropIndexOnStart));
        } catch (IndexFormatTooOldException e) {
            throw new RuntimeException("Old lucene index version detected, automatic migration is not supported. See https://github.com/james/james-project/blob/master/upgrade-instructions.md#james-4046-refactor-and-update-apache-james-mailbox-lucene for details", e);
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
    public Flux<MessageId> search(MailboxSession session, Collection<MailboxId> mailboxIds, SearchQuery searchQuery, long limit) throws MailboxException {
        Preconditions.checkArgument(session != null, "'session' is mandatory");
        if (mailboxIds.isEmpty()) {
            return Flux.empty();
        }

        return Flux.fromIterable(searchMultimap(mailboxIds, searchQuery)
            .stream()
            .filter(searchResult -> searchResult.getMessageId().isPresent())
            .map(searchResult -> searchResult.getMessageId().get())
            .filter(SearchUtil.distinct())
            .limit(Long.valueOf(limit).intValue())
            .collect(ImmutableList.toImmutableList()));
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
        Query inMailboxes = buildQueryFromMailboxes(mailboxIds);

        try (IndexReader reader = DirectoryReader.open(writer)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
            queryBuilder.add(inMailboxes, BooleanClause.Occur.MUST);
            // Not return flags documents
            queryBuilder.add(new PrefixQuery(new Term(FLAGS_FIELD, "")), BooleanClause.Occur.MUST_NOT);

            List<Criterion> crits = searchQuery.getCriteria();
            for (Criterion crit : crits) {
                queryBuilder.add(createQuery(crit, inMailboxes, searchQuery.getRecentMessageUids()), BooleanClause.Occur.MUST);
            }

            // query for all the documents sorted as specified in the SearchQuery
            TopDocs docs = searcher.search(queryBuilder.build(), maxQueryResults, createSort(searchQuery.getSorts()));

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

    /**
     * Create a new {@link Document} for the given {@link MailboxMessage}. This Document does not contain any flags data. The {@link Flags} are stored in a seperate Document.
     * <p>
     * See {@link #createFlagsDocument(MailboxMessage)}
     */
    private Document createMessageDocument(final MailboxSession session, final MailboxMessage membership) throws IOException, MimeException {
        final Document doc = new Document();
        // TODO: Better handling
        doc.add(new StringField(USERS, session.getUser().asString().toUpperCase(Locale.US), Store.YES));
        doc.add(new StringField(MAILBOX_ID_FIELD, membership.getMailboxId().serialize().toUpperCase(Locale.US), Store.YES));
        doc.add(new NumericDocValuesField(UID_FIELD, membership.getUid().asLong()));
        doc.add(new LongPoint(UID_FIELD, membership.getUid().asLong()));
        doc.add(new StoredField(UID_FIELD, membership.getUid().asLong()));
        doc.add(new StringField(HAS_ATTACHMENT_FIELD, Boolean.toString(hasAttachment(membership)), Store.YES));

        String serializedMessageId = SearchUtil.getSerializedMessageIdIfSupportedByUnderlyingStorageOrNull(membership);
        if (serializedMessageId != null) {
            doc.add(new StringField(MESSAGE_ID_FIELD, serializedMessageId, Store.YES));
        }
        String serializedThreadId = SearchUtil.getSerializedThreadIdIfSupportedByUnderlyingStorageOrNull(membership);
        if (serializedThreadId != null) {
            doc.add(new StringField(THREAD_ID_FIELD, serializedThreadId, Store.YES));
        }

        // create a unique key for the document which can be used later on updates to find the document
        doc.add(new StringField(ID_FIELD, membership.getMailboxId().serialize().toUpperCase(Locale.US) + "-" + membership.getUid().asLong(), Store.YES));

        doc.add(new StringField(INTERNAL_DATE_FIELD_YEAR_RESOLUTION, DateTools.dateToString(membership.getInternalDate(), DateTools.Resolution.YEAR), Store.NO));
        doc.add(new StringField(INTERNAL_DATE_FIELD_MONTH_RESOLUTION, DateTools.dateToString(membership.getInternalDate(), DateTools.Resolution.MONTH), Store.NO));
        doc.add(new StringField(INTERNAL_DATE_FIELD_DAY_RESOLUTION, DateTools.dateToString(membership.getInternalDate(), DateTools.Resolution.DAY), Store.NO));
        doc.add(new StringField(INTERNAL_DATE_FIELD_HOUR_RESOLUTION, DateTools.dateToString(membership.getInternalDate(), DateTools.Resolution.HOUR), Store.NO));
        doc.add(new StringField(INTERNAL_DATE_FIELD_MINUTE_RESOLUTION, DateTools.dateToString(membership.getInternalDate(), DateTools.Resolution.MINUTE), Store.NO));
        doc.add(new StringField(INTERNAL_DATE_FIELD_SECOND_RESOLUTION, DateTools.dateToString(membership.getInternalDate(), DateTools.Resolution.SECOND), Store.NO));
        doc.add(new NumericDocValuesField(INTERNAL_DATE_FIELD_MILLISECOND_RESOLUTION, Long.parseLong(DateTools.dateToString(membership.getInternalDate(), DateTools.Resolution.MILLISECOND))));

        membership.getSaveDate().ifPresent(saveDate -> {
            doc.add(new StringField(SAVE_DATE_FIELD_YEAR_RESOLUTION, DateTools.dateToString(saveDate, DateTools.Resolution.YEAR), Store.NO));
            doc.add(new StringField(SAVE_DATE_FIELD_MONTH_RESOLUTION, DateTools.dateToString(saveDate, DateTools.Resolution.MONTH), Store.NO));
            doc.add(new StringField(SAVE_DATE_FIELD_DAY_RESOLUTION, DateTools.dateToString(saveDate, DateTools.Resolution.DAY), Store.NO));
            doc.add(new StringField(SAVE_DATE_FIELD_HOUR_RESOLUTION, DateTools.dateToString(saveDate, DateTools.Resolution.HOUR), Store.NO));
            doc.add(new StringField(SAVE_DATE_FIELD_MINUTE_RESOLUTION, DateTools.dateToString(saveDate, DateTools.Resolution.MINUTE), Store.NO));
            doc.add(new StringField(SAVE_DATE_FIELD_SECOND_RESOLUTION, DateTools.dateToString(saveDate, DateTools.Resolution.SECOND), Store.NO));
        });

        doc.add(new LongPoint(SIZE_FIELD, membership.getFullContentOctets()));
        doc.add(new NumericDocValuesField(SIZE_FIELD, membership.getFullContentOctets()));

        // content handler which will index the headers and the body of the message
        SimpleContentHandler handler = new SimpleContentHandler() {


            @Override
            public void headers(Header header) {

                Date sentDate = null;
                String firstFromMailbox = "";
                String firstToMailbox = "";
                String firstCcMailbox = "";
                String firstFromDisplay = "";
                String firstToDisplay = "";

                for (org.apache.james.mime4j.stream.Field f : header) {
                    String headerName = f.getName().toUpperCase(Locale.US);
                    String headerValue = f.getBody().toUpperCase(Locale.US);
                    String fullValue = f.toString().toUpperCase(Locale.US);
                    doc.add(new TextField(HEADERS_FIELD, fullValue, Store.NO));
                    doc.add(new TextField(PREFIX_HEADER_FIELD + headerName, headerValue, Store.NO));

                    if (f instanceof DateTimeField dateTimeField) {
                        sentDate = dateTimeField.getDate();
                    }
                    String field = null;
                    if ("To".equalsIgnoreCase(headerName)) {
                        field = TO_FIELD;
                    } else if ("From".equalsIgnoreCase(headerName)) {
                        field = FROM_FIELD;
                    } else if ("Cc".equalsIgnoreCase(headerName)) {
                        field = CC_FIELD;
                    } else if ("Bcc".equalsIgnoreCase(headerName)) {
                        field = BCC_FIELD;
                    }


                    // Check if we can index the address in the right manner
                    if (field != null) {
                        // not sure if we really should reparse it. It maybe be better to check just for the right type.
                        // But this impl was easier in the first place
                        AddressList aList = LenientAddressParser.DEFAULT.parseAddressList(MimeUtil.unfold(f.getBody()));
                        for (int i = 0; i < aList.size(); i++) {
                            Address address = aList.get(i);
                            if (address instanceof org.apache.james.mime4j.dom.address.Mailbox mailbox) {
                                String value = AddressFormatter.DEFAULT.encode(mailbox).toUpperCase(Locale.US);
                                doc.add(new TextField(field, value, Store.NO));
                                if (i == 0) {
                                    String mailboxAddress = SearchUtil.getMailboxAddress(mailbox);
                                    String mailboxDisplay = SearchUtil.getDisplayAddress(mailbox);

                                    if ("To".equalsIgnoreCase(headerName)) {
                                        firstToMailbox = mailboxAddress;
                                        firstToDisplay = mailboxDisplay;
                                    } else if ("From".equalsIgnoreCase(headerName)) {
                                        firstFromMailbox = mailboxAddress;
                                        firstFromDisplay = mailboxDisplay;

                                    } else if ("Cc".equalsIgnoreCase(headerName)) {
                                        firstCcMailbox = mailboxAddress;
                                    }

                                }
                            } else if (address instanceof Group) {
                                MailboxList mList = ((Group) address).getMailboxes();
                                for (int a = 0; a < mList.size(); a++) {
                                    org.apache.james.mime4j.dom.address.Mailbox mailbox = mList.get(a);
                                    String value = AddressFormatter.DEFAULT.encode(mailbox).toUpperCase(Locale.US);
                                    doc.add(new TextField(field, value, Store.NO));

                                    if (i == 0 && a == 0) {
                                        String mailboxAddress = SearchUtil.getMailboxAddress(mailbox);
                                        String mailboxDisplay = SearchUtil.getDisplayAddress(mailbox);

                                        if ("To".equalsIgnoreCase(headerName)) {
                                            firstToMailbox = mailboxAddress;
                                            firstToDisplay = mailboxDisplay;
                                        } else if ("From".equalsIgnoreCase(headerName)) {
                                            firstFromMailbox = mailboxAddress;
                                            firstFromDisplay = mailboxDisplay;

                                        } else if ("Cc".equalsIgnoreCase(headerName)) {
                                            firstCcMailbox = mailboxAddress;
                                        }
                                    }
                                }
                            }
                        }

                        doc.add(new TextField(field, headerValue, Store.NO));

                    } else if (headerName.equalsIgnoreCase("Subject")) {
                        doc.add(new StringField(SUBJECT_FIELD, f.getBody(), Store.YES));
                        doc.add(new StringField(BASE_SUBJECT_FIELD, SearchUtil.getBaseSubject(headerValue), Store.YES));
                        doc.add(new SortedSetDocValuesField(BASE_SUBJECT_FIELD, new BytesRef(SearchUtil.getBaseSubject(headerValue))));
                    }
                }
                if (sentDate == null) {
                    sentDate = membership.getInternalDate();
                } else {

                    doc.add(new StringField(SENT_DATE_FIELD_YEAR_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.YEAR), Store.NO));
                    doc.add(new StringField(SENT_DATE_FIELD_MONTH_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.MONTH), Store.NO));
                    doc.add(new StringField(SENT_DATE_FIELD_DAY_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.DAY), Store.NO));
                    doc.add(new StringField(SENT_DATE_FIELD_HOUR_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.HOUR), Store.NO));
                    doc.add(new StringField(SENT_DATE_FIELD_MINUTE_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.MINUTE), Store.NO));
                    doc.add(new StringField(SENT_DATE_FIELD_SECOND_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.SECOND), Store.NO));
                    doc.add(new StringField(SENT_DATE_FIELD_MILLISECOND_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.MILLISECOND), Store.NO));

                }
                // Remove existing SENT_DATE_SORT_FIELD field if it exists
                doc.removeField(SENT_DATE_SORT_FIELD_MILLISECOND_RESOLUTION);
                doc.add(new NumericDocValuesField(SENT_DATE_SORT_FIELD_MILLISECOND_RESOLUTION, Long.parseLong(DateTools.dateToString(sentDate, DateTools.Resolution.MILLISECOND))));

                doc.add(new StringField(FIRST_FROM_MAILBOX_NAME_FIELD, firstFromMailbox, Store.YES));
                doc.add(new SortedSetDocValuesField(FIRST_FROM_MAILBOX_NAME_FIELD, new BytesRef(firstFromMailbox)));
                doc.add(new StringField(FIRST_TO_MAILBOX_NAME_FIELD, firstToMailbox, Store.YES));
                doc.add(new SortedSetDocValuesField(FIRST_TO_MAILBOX_NAME_FIELD, new BytesRef(firstToMailbox)));
                doc.add(new StringField(FIRST_CC_MAILBOX_NAME_FIELD, firstCcMailbox, Store.YES));
                doc.add(new SortedSetDocValuesField(FIRST_CC_MAILBOX_NAME_FIELD, new BytesRef(firstCcMailbox)));
                doc.add(new StringField(FIRST_FROM_MAILBOX_DISPLAY_FIELD, firstFromDisplay, Store.YES));
                doc.add(new StringField(FIRST_TO_MAILBOX_DISPLAY_FIELD, firstToDisplay, Store.YES));

            }

            @Override
            public void body(BodyDescriptor desc, InputStream in) throws IOException {
                String mediaType = desc.getMediaType();
                if (MEDIA_TYPE_TEXT.equalsIgnoreCase(mediaType) || MEDIA_TYPE_MESSAGE.equalsIgnoreCase(mediaType)) {
                    String cset = desc.getCharset();
                    if (cset == null) {
                        cset = DEFAULT_ENCODING;
                    }
                    Charset charset;
                    try {
                        charset = Charset.forName(cset);
                    } catch (Exception e) {
                        // Invalid charset found so fallback toe the DEFAULT_ENCODING
                        charset = Charset.forName(DEFAULT_ENCODING);
                    }

                    String bodyContent = IOUtils.toString(in, charset);
                    doc.add(new TextField(BODY_FIELD, bodyContent, Store.YES));
                }
            }

        };
        //config.setStrictParsing(false);
        MimeStreamParser parser = new MimeStreamParser(MimeConfig.PERMISSIVE);
        parser.setContentDecoding(true);
        parser.setContentHandler(handler);

        // parse the message to index headers and body
        parser.parse(membership.getFullContent());

        return doc;
    }

    private static boolean hasAttachment(MailboxMessage membership) {
       return MessageAttachmentMetadata.hasNonInlinedAttachment(membership.getAttachments());
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
    private Query createTextQuery(SearchQuery.TextCriterion crit) throws UnsupportedSearchException {
        String value = crit.getOperator().getValue().toUpperCase(Locale.US);
        switch (crit.getType()) {
        case BODY:
            return createTermQuery(BODY_FIELD, value);
        case FULL:
            BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
            queryBuilder.add(createTermQuery(BODY_FIELD, value), BooleanClause.Occur.SHOULD);
            queryBuilder.add(createTermQuery(HEADERS_FIELD,value), BooleanClause.Occur.SHOULD);
            return queryBuilder.build();
        default:
            throw new UnsupportedSearchException();
        }
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
            return createFlagQuery(crit.getFlag(), crit.getOperator().isSet(), inMailboxes, recentUids);
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
        return Mono.fromRunnable(Throwing.runnable(() -> {
            Document doc = createMessageDocument(session, membership);
            Document flagsDoc = createFlagsDocument(membership);

            log.trace("Adding document: uid:'{}' with flags: {}", doc.get("uid"), flagsDoc);

            writer.addDocument(doc);
            writer.addDocument(flagsDoc);
        }));
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
        var doc = createFlagsDocument(mailboxId, uid, f);
        log.trace("Updating flags document, mailboxId:{}, message uid: {}, flags:'{}', term: {}, new document: {}",
                mailboxId, uid, f, term, doc);
        writer.updateDocument(term, doc);
    }

    /**
     * Index the {@link Flags} and add it to the {@link Document}
     */
    private Document createFlagsDocument(MailboxMessage message) {
        return createFlagsDocument(message.getMailboxId(), message.getUid(), message.createFlags());
    }

    private Document createFlagsDocument(MailboxId mailboxId, final MessageUid messageUid, Flags flags) {
        Document doc = new Document();
        doc.add(new StringField(ID_FIELD, createFlagsIdField(mailboxId, messageUid), Store.YES));
        doc.add(new StringField(MAILBOX_ID_FIELD, mailboxId.serialize(), Store.YES));

        doc.add(new NumericDocValuesField(UID_FIELD, messageUid.asLong()));
        doc.add(new LongPoint(UID_FIELD, messageUid.asLong()));
        doc.add(new StoredField(UID_FIELD, messageUid.asLong()));

        indexFlags(doc, flags);
        return doc;
    }

    private static String createFlagsIdField(MailboxId mailboxId, MessageUid messageUid) {
        return "flags-" + mailboxId.serialize() + "-" + messageUid.asLong();
    }

    /**
     * Add the given {@link Flags} to the {@link Document}
     */
    private void indexFlags(Document doc, Flags f) {
        Flag[] flags = f.getSystemFlags();
        for (Flag flag : flags) {
            doc.add(new StringField(FLAGS_FIELD, toString(flag), Store.YES));
        }

        String[] userFlags = f.getUserFlags();
        for (String userFlag : userFlags) {
            doc.add(new StringField(FLAGS_FIELD, userFlag, Store.YES));
        }

        // if no flags are there we just use a empty field
        if (flags.length == 0 && userFlags.length == 0) {
            doc.add(new StringField(FLAGS_FIELD, "",Store.NO));
        }
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