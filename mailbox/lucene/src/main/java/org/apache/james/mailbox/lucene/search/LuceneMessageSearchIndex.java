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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxManager.SearchCapabilities;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedSearchException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AllCriterion;
import org.apache.james.mailbox.model.SearchQuery.AttachmentCriterion;
import org.apache.james.mailbox.model.SearchQuery.ContainsOperator;
import org.apache.james.mailbox.model.SearchQuery.Criterion;
import org.apache.james.mailbox.model.SearchQuery.CustomFlagCriterion;
import org.apache.james.mailbox.model.SearchQuery.DateOperator;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.mailbox.model.SearchQuery.FlagCriterion;
import org.apache.james.mailbox.model.SearchQuery.HeaderCriterion;
import org.apache.james.mailbox.model.SearchQuery.HeaderOperator;
import org.apache.james.mailbox.model.SearchQuery.NumericOperator;
import org.apache.james.mailbox.model.SearchQuery.UidCriterion;
import org.apache.james.mailbox.model.SearchQuery.UidRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.SearchUtil;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Group;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.dom.datetime.DateTime;
import org.apache.james.mime4j.dom.field.DateTimeField;
import org.apache.james.mime4j.field.address.AddressFormatter;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.mime4j.field.datetime.parser.DateTimeParser;
import org.apache.james.mime4j.message.SimpleContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Lucene based {@link ListeningMessageSearchIndex} which offers message searching via a Lucene index
 * 
 * 

 * @param 
 */
public class LuceneMessageSearchIndex extends ListeningMessageSearchIndex {
    private static final Logger LOGGER = LoggerFactory.getLogger(LuceneMessageSearchIndex.class);
    private final static Date MAX_DATE;
    private final static Date MIN_DATE;
    
    static {
        Calendar cal = Calendar.getInstance();
        cal.set(9999, 11, 31);
        MAX_DATE = cal.getTime();
        
        cal.set(0000, 0, 1);
        MIN_DATE = cal.getTime();
    }
    
    /**
     * Default max query results
     */
    public final static int DEFAULT_MAX_QUERY_RESULTS = 100000;
    
    /**
     * {@link Field} which will contain the unique index of the {@link Document}
     */
    public final static String ID_FIELD ="id";
    
    
    /**
     * {@link Field} which will contain uid of the {@link MailboxMessage}
     */
    public final static String UID_FIELD = "uid";
    
    /**
     * {@link Field} boolean field that say if the message as an attachment or not
     */
    public final static String HAS_ATTACHMENT_FIELD = "hasAttachment";

    /**
     * {@link Field} which will contain the {@link Flags} of the {@link MailboxMessage}
     */
    public final static String FLAGS_FIELD = "flags";
  
    /**
     * {@link Field} which will contain the size of the {@link MailboxMessage}
     */
    public final static String SIZE_FIELD = "size";

    /**
     * {@link Field} which will contain the body of the {@link MailboxMessage}
     */
    public final static String BODY_FIELD = "body";
    
    
    /**
     * Prefix which will be used for each message header to store it also in a seperate {@link Field}
     */
    public final static String PREFIX_HEADER_FIELD ="header_";
    
    /**
     * {@link Field} which will contain the whole message header of the {@link MailboxMessage}
     */
    public final static String HEADERS_FIELD ="headers";

    /**
     * {@link Field} which will contain the mod-sequence of the message
     */
    public final static String MODSEQ_FIELD = "modSeq";

    /**
     * {@link Field} which will contain the TO-Address of the message
     */
    public final static String TO_FIELD ="to";
    
    public final static String FIRST_TO_MAILBOX_NAME_FIELD ="firstToMailboxName";
    public final static String FIRST_TO_MAILBOX_DISPLAY_FIELD ="firstToMailboxDisplay";

    /**
     * {@link Field} which will contain the CC-Address of the message
     */
    public final static String CC_FIELD ="cc";

    public final static String FIRST_CC_MAILBOX_NAME_FIELD ="firstCcMailboxName";
    

    /**
     * {@link Field} which will contain the FROM-Address of the message
     */
    public final static String FROM_FIELD ="from";
    
    public final static String FIRST_FROM_MAILBOX_NAME_FIELD ="firstFromMailboxName";
    public final static String FIRST_FROM_MAILBOX_DISPLAY_FIELD ="firstFromMailboxDisplay";

    /**
     * {@link Field} which will contain the BCC-Address of the message
     */
    public final static String BCC_FIELD ="bcc";
    
    
    public final static String BASE_SUBJECT_FIELD = "baseSubject";
    
    /**
     * {@link Field} which contain the internalDate of the message with YEAR-Resolution
     */
    public final static String INTERNAL_DATE_FIELD_YEAR_RESOLUTION ="internaldateYearResolution";
    
    
    /**
     * {@link Field} which contain the internalDate of the message with MONTH-Resolution
     */
    public final static String INTERNAL_DATE_FIELD_MONTH_RESOLUTION ="internaldateMonthResolution";
    
    /**
     * {@link Field} which contain the internalDate of the message with DAY-Resolution
     */
    public final static String INTERNAL_DATE_FIELD_DAY_RESOLUTION ="internaldateDayResolution";
    
    /**
     * {@link Field} which contain the internalDate of the message with HOUR-Resolution
     */
    public final static String INTERNAL_DATE_FIELD_HOUR_RESOLUTION ="internaldateHourResolution";
    
    /**
     * {@link Field} which contain the internalDate of the message with MINUTE-Resolution
     */
    public final static String INTERNAL_DATE_FIELD_MINUTE_RESOLUTION ="internaldateMinuteResolution";
    
    /**
     * {@link Field} which contain the internalDate of the message with SECOND-Resolution
     */
    public final static String INTERNAL_DATE_FIELD_SECOND_RESOLUTION ="internaldateSecondResolution";
    
    
    /**
     * {@link Field} which contain the internalDate of the message with MILLISECOND-Resolution
     */
    public final static String INTERNAL_DATE_FIELD_MILLISECOND_RESOLUTION ="internaldateMillisecondResolution";

    /**
     * {@link Field} which will contain the id of the {@link Mailbox}
     */
    public final static String MAILBOX_ID_FIELD ="mailboxid";

    /**
     * {@link Field} which will contain the user of the {@link MailboxSession}
     */
    public final static String USERS = "userSession";
    /**
     * {@link Field} which will contain the id of the {@link MessageId}
     */
    public final static String MESSAGE_ID_FIELD ="messageid";

    /**
     * {@link Field} which contain the Date header of the message with YEAR-Resolution
     */
    public final static String SENT_DATE_FIELD_YEAR_RESOLUTION ="sentdateYearResolution";
    
    
    /**
     * {@link Field} which contain the Date header of the message with MONTH-Resolution
     */
    public final static String SENT_DATE_FIELD_MONTH_RESOLUTION ="sentdateMonthResolution";
    
    /**
     * {@link Field} which contain the Date header of the message with DAY-Resolution
     */
    public final static String SENT_DATE_FIELD_DAY_RESOLUTION ="sentdateDayResolution";
    
    /**
     * {@link Field} which contain the Date header of the message with HOUR-Resolution
     */
    public final static String SENT_DATE_FIELD_HOUR_RESOLUTION ="sentdateHourResolution";
    
    /**
     * {@link Field} which contain the Date header of the message with MINUTE-Resolution
     */
    public final static String SENT_DATE_FIELD_MINUTE_RESOLUTION ="sentdateMinuteResolution";
    
    /**
     * {@link Field} which contain the Date header of the message with SECOND-Resolution
     */
    public final static String SENT_DATE_FIELD_SECOND_RESOLUTION ="sentdateSecondResolution";
    
    
    /**
     * {@link Field} which contain the Date header of the message with MILLISECOND-Resolution
     */
    public final static String SENT_DATE_FIELD_MILLISECOND_RESOLUTION ="sentdateMillisecondResolution";

    public final static String SENT_DATE_SORT_FIELD_MILLISECOND_RESOLUTION ="sentdateSort";

    public final static String NON_EXIST_FIELD ="nonExistField";

    
    private final static String MEDIA_TYPE_TEXT = "text"; 
    private final static String MEDIA_TYPE_MESSAGE = "message"; 
    private final static String DEFAULT_ENCODING = "US-ASCII";
    
    private final static SortField UID_SORT = new SortField(UID_FIELD, SortField.LONG);
    private final static SortField UID_SORT_REVERSE = new SortField(UID_FIELD, SortField.LONG, true);

    private final static SortField SIZE_SORT = new SortField(SIZE_FIELD, SortField.LONG);
    private final static SortField SIZE_SORT_REVERSE = new SortField(SIZE_FIELD, SortField.LONG, true);

    private final static SortField FIRST_CC_MAILBOX_SORT = new SortField(FIRST_CC_MAILBOX_NAME_FIELD, SortField.STRING);
    private final static SortField FIRST_CC_MAILBOX_SORT_REVERSE = new SortField(FIRST_CC_MAILBOX_NAME_FIELD, SortField.STRING, true);

    private final static SortField FIRST_TO_MAILBOX_SORT = new SortField(FIRST_TO_MAILBOX_NAME_FIELD, SortField.STRING);
    private final static SortField FIRST_TO_MAILBOX_SORT_REVERSE = new SortField(FIRST_TO_MAILBOX_NAME_FIELD, SortField.STRING, true);

    private final static SortField FIRST_FROM_MAILBOX_SORT = new SortField(FIRST_FROM_MAILBOX_NAME_FIELD, SortField.STRING);
    private final static SortField FIRST_FROM_MAILBOX_SORT_REVERSE = new SortField(FIRST_FROM_MAILBOX_NAME_FIELD, SortField.STRING, true);

    
    private final static SortField ARRIVAL_MAILBOX_SORT = new SortField(INTERNAL_DATE_FIELD_MILLISECOND_RESOLUTION, SortField.LONG);
    private final static SortField ARRIVAL_MAILBOX_SORT_REVERSE = new SortField(INTERNAL_DATE_FIELD_MILLISECOND_RESOLUTION, SortField.LONG, true);

    private final static SortField BASE_SUBJECT_SORT = new SortField(BASE_SUBJECT_FIELD, SortField.STRING);
    private final static SortField BASE_SUBJECT_SORT_REVERSE = new SortField(BASE_SUBJECT_FIELD, SortField.STRING, true);
    
    private final static SortField SENT_DATE_SORT = new SortField(SENT_DATE_SORT_FIELD_MILLISECOND_RESOLUTION, SortField.LONG);
    private final static SortField SENT_DATE_SORT_REVERSE = new SortField(SENT_DATE_SORT_FIELD_MILLISECOND_RESOLUTION, SortField.LONG, true);
    
    private final static SortField FIRST_TO_MAILBOX_DISPLAY_SORT = new SortField(FIRST_TO_MAILBOX_DISPLAY_FIELD, SortField.STRING);
    private final static SortField FIRST_TO_MAILBOX_DISPLAY_SORT_REVERSE = new SortField(FIRST_TO_MAILBOX_DISPLAY_FIELD, SortField.STRING, true);

    private final static SortField FIRST_FROM_MAILBOX_DISPLAY_SORT = new SortField(FIRST_FROM_MAILBOX_DISPLAY_FIELD, SortField.STRING);
    private final static SortField FIRST_FROM_MAILBOX_DISPLAY_SORT_REVERSE = new SortField(FIRST_FROM_MAILBOX_DISPLAY_FIELD, SortField.STRING, true);
    
    private final MailboxId.Factory mailboxIdFactory;
    private final MessageId.Factory messageIdFactory;
    private final IndexWriter writer;
    
    private int maxQueryResults = DEFAULT_MAX_QUERY_RESULTS;

    private boolean suffixMatch = false;

    @Inject
    public LuceneMessageSearchIndex(MessageMapperFactory factory, MailboxId.Factory mailboxIdFactory, Directory directory, MessageId.Factory messageIdFactory, MailboxManager mailboxManager) throws CorruptIndexException, LockObtainFailedException, IOException {
        this(factory, mailboxIdFactory, directory, false, true, messageIdFactory);
    }

    public LuceneMessageSearchIndex(
            MessageMapperFactory factory,
            MailboxId.Factory mailboxIdFactory,
            Directory directory,
            boolean dropIndexOnStart,
            boolean lenient,
            MessageId.Factory messageIdFactory)
                    throws CorruptIndexException, LockObtainFailedException, IOException {
        super(factory);
        this.mailboxIdFactory = mailboxIdFactory;
        this.messageIdFactory = messageIdFactory;
        this.writer = new IndexWriter(directory,  createConfig(createAnalyzer(lenient), dropIndexOnStart));
    }
    
    
    public LuceneMessageSearchIndex(MessageMapperFactory factory, MailboxId.Factory mailboxIdFactory, MessageId.Factory messageIdFactory, IndexWriter writer) {
        super(factory);
        this.mailboxIdFactory = mailboxIdFactory;
        this.messageIdFactory = messageIdFactory;
        this.writer = writer;
    }

    @Override
    public ListenerType getType() {
        return ListenerType.EACH_NODE;
    }


    @Override
    public EnumSet<SearchCapabilities> getSupportedCapabilities() {
        return EnumSet.noneOf(SearchCapabilities.class);
    }
    
    /**
     * Set the max count of results which will get returned from a query. The default is {@link #DEFAULT_MAX_QUERY_RESULTS}
     * 
     * @param maxQueryResults
     */
    public void setMaxQueryResults(int maxQueryResults) {
        this.maxQueryResults = maxQueryResults;
    }
    
    protected IndexWriterConfig createConfig(Analyzer analyzer, boolean dropIndexOnStart) {
        IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_31, analyzer);
        if (dropIndexOnStart) {
            config.setOpenMode(OpenMode.CREATE);
        } else {
            config.setOpenMode(OpenMode.CREATE_OR_APPEND);
        }
        return config;
    }
    
    /**
     * Create a {@link Analyzer} which is used to index the {@link MailboxMessage}'s
     * 
     * @param lenient 
     * 
     * @return analyzer
     */
    protected Analyzer createAnalyzer(boolean lenient) {
        if (lenient) {
           return new LenientImapSearchAnalyzer();
        } else {
            return new StrictImapSearchAnalyzer();
        }
    }
    
    /**
     * If set to true this implementation will use {@link WildcardQuery} to match suffix and prefix. This is what RFC3501 expects but is often not what the user does.
     * It also slow things a lot if you have complex queries which use many "TEXT" arguments. If you want the implementation to behave strict like RFC3501 says, you should
     * set this to true. 
     * 
     * The default is false for performance reasons
     * 
     * 
     * @param suffixMatch
     */
    public void setEnableSuffixMatch(boolean suffixMatch) {
        this.suffixMatch = suffixMatch;
    }
    
    
    
    @Override
    public Iterator<MessageUid> search(MailboxSession session, Mailbox mailbox, SearchQuery searchQuery) throws MailboxException {
        Preconditions.checkArgument(session != null, "'session' is mandatory");
        MailboxId mailboxId = mailbox.getMailboxId();
        MultimailboxesSearchQuery multimailboxesSearchQuery = MultimailboxesSearchQuery
            .from(searchQuery)
            .inMailboxes(mailboxId)
            .build();

        return FluentIterable.from(searchMultimap(multimailboxesSearchQuery, session))
            .transform(SearchResult::getMessageUid)
            .iterator();
    }

    @Override
    public List<MessageId> search(MailboxSession session, MultimailboxesSearchQuery searchQuery, long limit) throws MailboxException {
        Preconditions.checkArgument(session != null, "'session' is mandatory");
        return FluentIterable.from(searchMultimap(searchQuery, session))
            .transform(searchResult -> searchResult.getMessageId().get())
            .filter(SearchUtil.distinct())
            .limit(Long.valueOf(limit).intValue())
            .toList();
    }
    
    private List<SearchResult> searchMultimap(MultimailboxesSearchQuery searchQuery, MailboxSession session) throws MailboxException {
        ImmutableList.Builder<SearchResult> results = ImmutableList.builder();
        IndexSearcher searcher = null;

        Query inMailboxes = buildQueryFromMailboxes(searchQuery.getInMailboxes());
        
        try {
            searcher = new IndexSearcher(IndexReader.open(writer, true));
            BooleanQuery query = new BooleanQuery();
            query.add(inMailboxes, BooleanClause.Occur.MUST);
            // Not return flags documents
            query.add(new PrefixQuery(new Term(FLAGS_FIELD, "")), BooleanClause.Occur.MUST_NOT);
            query.add(new TermQuery(new Term(USERS, session.getUser().getUserName().toUpperCase(Locale.US))), Occur.MUST);
            List<Criterion> crits = searchQuery.getSearchQuery().getCriterias();
            for (Criterion crit : crits) {
                query.add(createQuery(crit, inMailboxes, searchQuery.getSearchQuery().getRecentMessageUids()), BooleanClause.Occur.MUST);
            }

            // query for all the documents sorted as specified in the SearchQuery
            TopDocs docs = searcher.search(query, null, maxQueryResults, createSort(searchQuery.getSearchQuery().getSorts()));
            ScoreDoc[] sDocs = docs.scoreDocs;
            for (ScoreDoc sDoc : sDocs) {
                Document doc = searcher.doc(sDoc.doc);
                MessageUid uid = MessageUid.of(Long.valueOf(doc.get(UID_FIELD)));
                MailboxId mailboxId = mailboxIdFactory.fromString(doc.get(MAILBOX_ID_FIELD));
                Optional<MessageId> messageId = toMessageId(Optional.fromNullable(doc.get(MESSAGE_ID_FIELD)));
                results.add(new SearchResult(messageId, mailboxId, uid));
            }
        } catch (IOException e) {
            throw new MailboxException("Unable to search the mailbox", e);
        } finally {
            if (searcher != null) {
                try {
                    searcher.close();
                } catch (IOException e) {
                    // ignore on close
                }
            }
        }
        return results.build();
    }

    private Optional<MessageId> toMessageId(Optional<String> messageIdField) {
        if (messageIdField.isPresent()) {
            return Optional.of(messageIdFactory.fromString(messageIdField.get()));
        }
        return Optional.absent();
    }

    private Query buildQueryFromMailboxes(ImmutableSet<MailboxId> mailboxIds) {
        if (mailboxIds.isEmpty()) {
            return new MatchAllDocsQuery();
        }
        BooleanQuery query = new BooleanQuery();
        for (MailboxId id: mailboxIds) {
            String idAsString = id.serialize();
            query.add(new TermQuery(new Term(MAILBOX_ID_FIELD, idAsString)), BooleanClause.Occur.SHOULD);
        }
        return query;
    }


    /**
     * Create a new {@link Document} for the given {@link MailboxMessage}. This Document does not contain any flags data. The {@link Flags} are stored in a seperate Document.
     * 
     * See {@link #createFlagsDocument(MailboxMessage)}
     * 
     * @param membership
     * @return document
     */
    private Document createMessageDocument(final MailboxSession session, final MailboxMessage membership) throws MailboxException{
        final Document doc = new Document();
        // TODO: Better handling
        doc.add(new Field(USERS, session.getUser().getUserName().toUpperCase(Locale.US), Store.YES, Index.NOT_ANALYZED));
        doc.add(new Field(MAILBOX_ID_FIELD, membership.getMailboxId().serialize().toUpperCase(Locale.US), Store.YES, Index.NOT_ANALYZED));
        doc.add(new NumericField(UID_FIELD,Store.YES, true).setLongValue(membership.getUid().asLong()));
        doc.add(new Field(HAS_ATTACHMENT_FIELD, Boolean.toString(hasAttachment(membership)), Store.YES, Index.NOT_ANALYZED));
        doc.add(new Field(MESSAGE_ID_FIELD, SearchUtil.getSerializedMessageIdIfSupportedByUnderlyingStorageOrNull(membership), Store.YES, Index.NOT_ANALYZED));

        // create an unqiue key for the document which can be used later on updates to find the document
        doc.add(new Field(ID_FIELD, membership.getMailboxId().serialize().toUpperCase(Locale.US) +"-" + Long.toString(membership.getUid().asLong()), Store.YES, Index.NOT_ANALYZED));

        doc.add(new Field(INTERNAL_DATE_FIELD_YEAR_RESOLUTION, DateTools.dateToString(membership.getInternalDate(), DateTools.Resolution.YEAR), Store.NO, Index.NOT_ANALYZED));
        doc.add(new Field(INTERNAL_DATE_FIELD_MONTH_RESOLUTION, DateTools.dateToString(membership.getInternalDate(), DateTools.Resolution.MONTH), Store.NO, Index.NOT_ANALYZED));
        doc.add(new Field(INTERNAL_DATE_FIELD_DAY_RESOLUTION, DateTools.dateToString(membership.getInternalDate(), DateTools.Resolution.DAY), Store.NO, Index.NOT_ANALYZED));
        doc.add(new Field(INTERNAL_DATE_FIELD_HOUR_RESOLUTION, DateTools.dateToString(membership.getInternalDate(), DateTools.Resolution.HOUR), Store.NO, Index.NOT_ANALYZED));
        doc.add(new Field(INTERNAL_DATE_FIELD_MINUTE_RESOLUTION, DateTools.dateToString(membership.getInternalDate(), DateTools.Resolution.MINUTE), Store.NO, Index.NOT_ANALYZED));
        doc.add(new Field(INTERNAL_DATE_FIELD_SECOND_RESOLUTION, DateTools.dateToString(membership.getInternalDate(), DateTools.Resolution.SECOND), Store.NO, Index.NOT_ANALYZED));
        doc.add(new Field(INTERNAL_DATE_FIELD_MILLISECOND_RESOLUTION, DateTools.dateToString(membership.getInternalDate(), DateTools.Resolution.MILLISECOND), Store.NO, Index.NOT_ANALYZED));

        doc.add(new NumericField(SIZE_FIELD,Store.YES, true).setLongValue(membership.getFullContentOctets()));

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
                
                Iterator<org.apache.james.mime4j.stream.Field> fields = header.iterator();
                while(fields.hasNext()) {
                    org.apache.james.mime4j.stream.Field f = fields.next();
                    String headerName = f.getName().toUpperCase(Locale.US);
                    String headerValue = f.getBody().toUpperCase(Locale.US);
                    String fullValue =  f.toString().toUpperCase(Locale.US);
                    doc.add(new Field(HEADERS_FIELD, fullValue, Store.NO, Index.ANALYZED));
                    doc.add(new Field(PREFIX_HEADER_FIELD + headerName, headerValue, Store.NO, Index.ANALYZED));
                    
                    if (f instanceof DateTimeField) {
                        // We need to make sure we convert it to GMT
                        final StringReader reader = new StringReader(f.getBody());
                        try {
                            DateTime dateTime = new DateTimeParser(reader).parseAll();
                            Calendar cal = getGMT();
                            cal.set(dateTime.getYear(), dateTime.getMonth() - 1, dateTime.getDay(), dateTime.getHour(), dateTime.getMinute(), dateTime.getSecond());
                            sentDate =  cal.getTime();
                            
                        } catch (org.apache.james.mime4j.field.datetime.parser.ParseException e) {
                            LOGGER.debug("Unable to parse Date header for proper indexing", e);
                            // This should never happen anyway fallback to the already parsed field
                            sentDate = ((DateTimeField) f).getDate();
                        }
                        if (sentDate == null) {
                            sentDate = membership.getInternalDate();
                        }

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
                        

                        // Check if we can index the the address in the right manner
                        if (field != null) {
                                // not sure if we really should reparse it. It maybe be better to check just for the right type.
                                // But this impl was easier in the first place
                                AddressList aList = LenientAddressParser.DEFAULT.parseAddressList(MimeUtil.unfold(f.getBody()));
                                for (int i = 0; i < aList.size(); i++) {
                                    Address address = aList.get(i);
                                    if (address instanceof org.apache.james.mime4j.dom.address.Mailbox) {
                                        org.apache.james.mime4j.dom.address.Mailbox mailbox = (org.apache.james.mime4j.dom.address.Mailbox) address;
                                        String value = AddressFormatter.DEFAULT.encode(mailbox).toUpperCase(Locale.US);
                                        doc.add(new Field(field, value, Store.NO, Index.ANALYZED));
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
                                            doc.add(new Field(field, value, Store.NO, Index.ANALYZED));

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

                            
                            doc.add(new Field(field, headerValue, Store.NO, Index.ANALYZED));

                    } else if (headerName.equalsIgnoreCase("Subject")) {
                        doc.add(new Field(BASE_SUBJECT_FIELD, SearchUtil.getBaseSubject(headerValue), Store.YES, Index.NOT_ANALYZED));
                    } 
                }
                if (sentDate == null) {
                    sentDate = membership.getInternalDate();
                } else {
                    
                    doc.add(new Field(SENT_DATE_FIELD_YEAR_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.YEAR), Store.NO, Index.NOT_ANALYZED));
                    doc.add(new Field(SENT_DATE_FIELD_MONTH_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.MONTH), Store.NO, Index.NOT_ANALYZED));
                    doc.add(new Field(SENT_DATE_FIELD_DAY_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.DAY), Store.NO, Index.NOT_ANALYZED));
                    doc.add(new Field(SENT_DATE_FIELD_HOUR_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.HOUR), Store.NO, Index.NOT_ANALYZED));
                    doc.add(new Field(SENT_DATE_FIELD_MINUTE_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.MINUTE), Store.NO, Index.NOT_ANALYZED));
                    doc.add(new Field(SENT_DATE_FIELD_SECOND_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.SECOND), Store.NO, Index.NOT_ANALYZED));
                    doc.add(new Field(SENT_DATE_FIELD_MILLISECOND_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.MILLISECOND), Store.NO, Index.NOT_ANALYZED));
                    
                }
                doc.add(new Field(SENT_DATE_SORT_FIELD_MILLISECOND_RESOLUTION,DateTools.dateToString(sentDate, DateTools.Resolution.MILLISECOND), Store.NO, Index.NOT_ANALYZED));

                doc.add(new Field(FIRST_FROM_MAILBOX_NAME_FIELD, firstFromMailbox, Store.YES, Index.NOT_ANALYZED));
                doc.add(new Field(FIRST_TO_MAILBOX_NAME_FIELD, firstToMailbox, Store.YES, Index.NOT_ANALYZED));
                doc.add(new Field(FIRST_CC_MAILBOX_NAME_FIELD, firstCcMailbox, Store.YES, Index.NOT_ANALYZED));
                doc.add(new Field(FIRST_FROM_MAILBOX_DISPLAY_FIELD, firstFromDisplay, Store.YES, Index.NOT_ANALYZED));
                doc.add(new Field(FIRST_TO_MAILBOX_DISPLAY_FIELD, firstToDisplay, Store.YES, Index.NOT_ANALYZED));
           
            }

            @Override
            public void body(BodyDescriptor desc, InputStream in) throws MimeException, IOException {
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
                    
                    // Read the content one line after the other and add it to the document
                    BufferedReader bodyReader = new BufferedReader(new InputStreamReader(in, charset));
                    String line = null;
                    while((line = bodyReader.readLine()) != null) {
                        doc.add(new Field(BODY_FIELD,  line.toUpperCase(Locale.US),Store.NO, Index.ANALYZED));
                    }
                    
                }
            }
 
        };
        MimeConfig config = MimeConfig.custom()
                .setMaxLineLen(-1)
                .setMaxContentLen(-1)
                .build();
        //config.setStrictParsing(false);
        MimeStreamParser parser = new MimeStreamParser(config);
        parser.setContentDecoding(true);
        parser.setContentHandler(handler);
       
        try {
            // parse the message to index headers and body
            parser.parse(membership.getFullContent());
        } catch (MimeException | IOException e) {
            // This should never happen as it was parsed before too without problems.            
            throw new MailboxException("Unable to index content of message", e);
        }


        return doc;
    }

    private static boolean hasAttachment(MailboxMessage membership) {
       return FluentIterable.from(membership.getProperties())
            .anyMatch(PropertyBuilder.isHasAttachmentProperty());
    }

    private String toSentDateField(DateResolution res) {
        switch (res) {
            case Year:
                return SENT_DATE_FIELD_YEAR_RESOLUTION;
            case Month:
                return SENT_DATE_FIELD_MONTH_RESOLUTION;
            case Day:
                return SENT_DATE_FIELD_DAY_RESOLUTION;
            case Hour:
                return SENT_DATE_FIELD_HOUR_RESOLUTION;
            case Minute:
                return SENT_DATE_FIELD_MINUTE_RESOLUTION;
            case Second:
                return SENT_DATE_FIELD_SECOND_RESOLUTION;
            default:
                return SENT_DATE_FIELD_MILLISECOND_RESOLUTION;
        }
    }

    private static Calendar getGMT() {
        return Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.US);
    }

    private String toInteralDateField(DateResolution res) {
        switch (res) {
            case Year:
                return INTERNAL_DATE_FIELD_YEAR_RESOLUTION;
            case Month:
                return INTERNAL_DATE_FIELD_MONTH_RESOLUTION;
            case Day:
                return INTERNAL_DATE_FIELD_DAY_RESOLUTION;
            case Hour:
                return INTERNAL_DATE_FIELD_HOUR_RESOLUTION;
            case Minute:
                return INTERNAL_DATE_FIELD_MINUTE_RESOLUTION;
            case Second:
                return INTERNAL_DATE_FIELD_SECOND_RESOLUTION;
            default:
                return INTERNAL_DATE_FIELD_MILLISECOND_RESOLUTION;
        }
    }
    
    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.InternalDateCriterion}
     * 
     * @param crit
     * @return query
     * @throws UnsupportedSearchException
     */
    private Query createInternalDateQuery(SearchQuery.InternalDateCriterion crit) throws UnsupportedSearchException {
        DateOperator dop = crit.getOperator();
        DateResolution res = dop.getDateResultion();
        String field = toInteralDateField(res);
        return createQuery(field, dop);
    }
    
    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.SizeCriterion}
     * 
     * @param crit
     * @return query
     * @throws UnsupportedSearchException
     */
    private Query createSizeQuery(SearchQuery.SizeCriterion crit) throws UnsupportedSearchException {
        NumericOperator op = crit.getOperator();
        switch (op.getType()) {
        case EQUALS:
            return NumericRangeQuery.newLongRange(SIZE_FIELD, op.getValue(), op.getValue(), true, true);
        case GREATER_THAN:
            return NumericRangeQuery.newLongRange(SIZE_FIELD, op.getValue(), Long.MAX_VALUE, false, true);
        case LESS_THAN:
            return NumericRangeQuery.newLongRange(SIZE_FIELD, Long.MIN_VALUE, op.getValue(), true, false);
        default:
            throw new UnsupportedSearchException();
        }
    }
    
    /**
     * This method will return the right {@link Query} depending if {@link #suffixMatch} is enabled
     * 
     * @param fieldName
     * @param value
     * @return query
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
     * 
     * @param crit
     * @return query
     * @throws UnsupportedSearchException
     */
    private Query createHeaderQuery(SearchQuery.HeaderCriterion crit) throws UnsupportedSearchException {
        HeaderOperator op = crit.getOperator();
        String name = crit.getHeaderName().toUpperCase(Locale.US);
        String fieldName = PREFIX_HEADER_FIELD + name;
        if (op instanceof SearchQuery.ContainsOperator) {
            ContainsOperator cop = (ContainsOperator) op;
            return createTermQuery(fieldName, cop.getValue().toUpperCase(Locale.US));
        } else if (op instanceof SearchQuery.ExistsOperator){
            return new PrefixQuery(new Term(fieldName, ""));
        } else if (op instanceof SearchQuery.DateOperator) {
                DateOperator dop = (DateOperator) op;
                String field = toSentDateField(dop.getDateResultion());
                return createQuery(field, dop);
        } else if (op instanceof SearchQuery.AddressOperator) {
            String field = name.toLowerCase(Locale.US);
            return createTermQuery(field, ((SearchQuery.AddressOperator) op).getAddress().toUpperCase(Locale.US));
        } else {
            // Operator not supported
            throw new UnsupportedSearchException();
        }
    }
    
    
    private Query createQuery(String field, DateOperator dop) throws UnsupportedSearchException {
        Date date = dop.getDate();
        DateResolution res = dop.getDateResultion();
        DateTools.Resolution dRes = toResolution(res);
        String value = DateTools.dateToString(date, dRes);
        switch(dop.getType()) {
        case ON:
            return new TermQuery(new Term(field ,value));
        case BEFORE: 
            return new TermRangeQuery(field, DateTools.dateToString(MIN_DATE, dRes), value, true, false);
        case AFTER: 
            return new TermRangeQuery(field, value, DateTools.dateToString(MAX_DATE, dRes), false, true);
        default:
            throw new UnsupportedSearchException();
        }
    }
    
    private DateTools.Resolution toResolution(DateResolution res) {
        switch (res) {
        case Year:
            return DateTools.Resolution.YEAR;
        case Month:
            return DateTools.Resolution.MONTH;
        case Day:
            return DateTools.Resolution.DAY;
        case Hour:
            return DateTools.Resolution.HOUR;
        case Minute:
            return DateTools.Resolution.MINUTE;
        case Second:
            return DateTools.Resolution.SECOND;
        default:
            return DateTools.Resolution.MILLISECOND;
        }
    }
    
    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.UidCriterion}
     */
    private Query createUidQuery(SearchQuery.UidCriterion crit) throws UnsupportedSearchException {
        UidRange[] ranges = crit.getOperator().getRange();
        if (ranges.length == 1) {
            UidRange range = ranges[0];
            return NumericRangeQuery.newLongRange(UID_FIELD, range.getLowValue().asLong(), range.getHighValue().asLong(), true, true);
        } else {
            BooleanQuery rangesQuery = new BooleanQuery();
            for (UidRange range : ranges) {
                rangesQuery.add(NumericRangeQuery.newLongRange(UID_FIELD, range.getLowValue().asLong(), range.getHighValue().asLong(), true, true), BooleanClause.Occur.SHOULD);
            }        
            return rangesQuery;
        }
    }
    
    
    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.UidCriterion}
     * 
     * @param crit
     * @return query
     * @throws UnsupportedSearchException
     */
    private Query createModSeqQuery(SearchQuery.ModSeqCriterion crit) throws UnsupportedSearchException {
        NumericOperator op = crit.getOperator();
        switch (op.getType()) {
        case EQUALS:
            return NumericRangeQuery.newLongRange(MODSEQ_FIELD, op.getValue(), op.getValue(), true, true);
        case GREATER_THAN:
            return NumericRangeQuery.newLongRange(MODSEQ_FIELD, op.getValue(), Long.MAX_VALUE, false, true);
        case LESS_THAN:
            return NumericRangeQuery.newLongRange(MODSEQ_FIELD, Long.MIN_VALUE, op.getValue(), true, false);
        default:
            throw new UnsupportedSearchException();
        }
    }
    
    private Query createAttachmentQuery(boolean isSet, Query inMailboxes) throws MailboxException, UnsupportedSearchException {
        return new TermQuery(new Term(HAS_ATTACHMENT_FIELD, Boolean.toString(isSet)));
    }

    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.FlagCriterion}. This is kind of a hack
     * as it will do a search for the flags in this method and 
     *
     * @return query
     * @throws UnsupportedSearchException
     */
    private Query createFlagQuery(String flag, boolean isSet, Query inMailboxes, Collection<MessageUid> recentUids) throws MailboxException, UnsupportedSearchException {
        BooleanQuery query = new BooleanQuery();
        
        if (isSet) {   
            query.add(new TermQuery(new Term(FLAGS_FIELD, flag)), BooleanClause.Occur.MUST);
        } else {
            // lucene does not support simple NOT queries so we do some nasty hack here
            BooleanQuery bQuery = new BooleanQuery();
            bQuery.add(new PrefixQuery(new Term(FLAGS_FIELD, "")), BooleanClause.Occur.MUST);
            bQuery.add(new TermQuery(new Term(FLAGS_FIELD, flag)),BooleanClause.Occur.MUST_NOT);
            
            query.add(bQuery, BooleanClause.Occur.MUST);
        }
        query.add(inMailboxes, BooleanClause.Occur.MUST);
        
        
        IndexSearcher searcher = null;

        try {
            Set<MessageUid> uids = new HashSet<>();
            searcher = new IndexSearcher(IndexReader.open(writer, true));
            
            // query for all the documents sorted by uid
            TopDocs docs = searcher.search(query, null, maxQueryResults, new Sort(UID_SORT));
            ScoreDoc[] sDocs = docs.scoreDocs;
            for (ScoreDoc sDoc : sDocs) {
                MessageUid uid = MessageUid.of(Long.valueOf(searcher.doc(sDoc.doc).get(UID_FIELD)));
                uids.add(uid);
            }
            
            // add or remove recent uids
            if (flag.equalsIgnoreCase("\\RECENT")){
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
        } finally {
            if (searcher != null) {
                try {
                    searcher.close();
                } catch (IOException e) {
                    // ignore on close
                }
            }
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
        Sort sort = new Sort();
        sort.setSort(fields.toArray(new SortField[0]));
        return sort;
    }

    private SortField createSortField(SearchQuery.Sort s, boolean reverse) {
        switch (s.getSortClause()) {
            case Arrival:
                if (reverse) {
                    return ARRIVAL_MAILBOX_SORT_REVERSE;
                }
                return ARRIVAL_MAILBOX_SORT;
            case SentDate:
                if (reverse) {
                    return SENT_DATE_SORT_REVERSE;
                }
                return SENT_DATE_SORT;
            case MailboxCc:
                if (reverse) {
                    return FIRST_CC_MAILBOX_SORT_REVERSE;
                }
                return FIRST_CC_MAILBOX_SORT;
            case MailboxFrom:
                if (reverse) {
                    return FIRST_FROM_MAILBOX_SORT_REVERSE;
                }
                return FIRST_FROM_MAILBOX_SORT;
            case Size:
                if (reverse) {
                    return SIZE_SORT_REVERSE;
                }
                return SIZE_SORT;
            case BaseSubject:
                if (reverse) {
                    return BASE_SUBJECT_SORT_REVERSE;
                }
                return BASE_SUBJECT_SORT;
            case MailboxTo:
                if (reverse) {
                    return FIRST_TO_MAILBOX_SORT_REVERSE;
                }
                return FIRST_TO_MAILBOX_SORT;
            case Uid:
                if (reverse) {
                    return UID_SORT_REVERSE;
                }
                return UID_SORT;
            case DisplayFrom:
                if (reverse) {
                    return FIRST_FROM_MAILBOX_DISPLAY_SORT_REVERSE;
                }
                return FIRST_FROM_MAILBOX_DISPLAY_SORT;
            case DisplayTo:
                if (reverse) {
                    return FIRST_TO_MAILBOX_DISPLAY_SORT_REVERSE;
                }
                return FIRST_TO_MAILBOX_DISPLAY_SORT;
            default:
                return null;
        }
    }

    /**
     * Convert the given {@link Flag} to a String
     * 
     * @param flag
     * @return flagString
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
     * 
     * @param crit
     * @return query
     * @throws UnsupportedSearchException
     */
    private Query createTextQuery(SearchQuery.TextCriterion crit) throws UnsupportedSearchException {
        String value = crit.getOperator().getValue().toUpperCase(Locale.US);
        switch(crit.getType()) {
        case BODY:
            return createTermQuery(BODY_FIELD, value);
        case FULL: 
            BooleanQuery query = new BooleanQuery();
            query.add(createTermQuery(BODY_FIELD, value), BooleanClause.Occur.SHOULD);
            query.add(createTermQuery(HEADERS_FIELD,value), BooleanClause.Occur.SHOULD);
            return query;
        default:
            throw new UnsupportedSearchException();
        }
    }
    
    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.AllCriterion}
     * 
     * @param crit
     * @return query
     * @throws UnsupportedSearchException
     */
    private Query createAllQuery(SearchQuery.AllCriterion crit) throws UnsupportedSearchException{
        BooleanQuery query = new BooleanQuery();
        
        query.add(createQuery(MessageRange.all()), BooleanClause.Occur.MUST);
        query.add(new PrefixQuery(new Term(FLAGS_FIELD, "")), BooleanClause.Occur.MUST_NOT);
        
        return query;
    }
    
    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.ConjunctionCriterion}
     * 
     * @param crit
     * @return query
     * @throws UnsupportedSearchException
     */
    private Query createConjunctionQuery(SearchQuery.ConjunctionCriterion crit, Query inMailboxes, Collection<MessageUid> recentUids) throws UnsupportedSearchException, MailboxException {
        List<Criterion> crits = crit.getCriteria();
        BooleanQuery conQuery = new BooleanQuery();
        switch (crit.getType()) {
        case AND:
            for (Criterion criterion : crits) {
                conQuery.add(createQuery(criterion, inMailboxes, recentUids), BooleanClause.Occur.MUST);
            }
            return conQuery;
        case OR:
            for (Criterion criterion : crits) {
                conQuery.add(createQuery(criterion, inMailboxes, recentUids), BooleanClause.Occur.SHOULD);
            }
            return conQuery;
        case NOR:
            BooleanQuery nor = new BooleanQuery();
            for (Criterion criterion : crits) {
                conQuery.add(createQuery(criterion, inMailboxes, recentUids), BooleanClause.Occur.SHOULD);
            }
            nor.add(inMailboxes, BooleanClause.Occur.MUST);

            nor.add(conQuery, BooleanClause.Occur.MUST_NOT);
            return nor;
        default:
            throw new UnsupportedSearchException();
        }

    }
    
    /**
     * Return a {@link Query} which is builded based on the given {@link Criterion}
     * 
     * @param criterion
     * @return query
     * @throws UnsupportedSearchException
     */
    private Query createQuery(Criterion criterion, Query inMailboxes, Collection<MessageUid> recentUids) throws UnsupportedSearchException, MailboxException {
        if (criterion instanceof SearchQuery.InternalDateCriterion) {
            SearchQuery.InternalDateCriterion crit = (SearchQuery.InternalDateCriterion) criterion;
            return createInternalDateQuery(crit);
        } else if (criterion instanceof SearchQuery.SizeCriterion) {
            SearchQuery.SizeCriterion crit = (SearchQuery.SizeCriterion) criterion;
            return createSizeQuery(crit);
        } else if (criterion instanceof SearchQuery.HeaderCriterion) {
            HeaderCriterion crit = (HeaderCriterion) criterion;
            return createHeaderQuery(crit);
        } else if (criterion instanceof SearchQuery.UidCriterion) {
            SearchQuery.UidCriterion crit = (SearchQuery.UidCriterion) criterion;
            return createUidQuery(crit);
        } else if (criterion instanceof SearchQuery.FlagCriterion) {
            FlagCriterion crit = (FlagCriterion) criterion;
            return createFlagQuery(toString(crit.getFlag()), crit.getOperator().isSet(), inMailboxes, recentUids);
        } else if (criterion instanceof SearchQuery.AttachmentCriterion) {
            AttachmentCriterion crit = (AttachmentCriterion) criterion;
            return createAttachmentQuery(crit.getOperator().isSet(), inMailboxes);
        } else if (criterion instanceof SearchQuery.CustomFlagCriterion) {
            CustomFlagCriterion crit = (CustomFlagCriterion) criterion;
            return createFlagQuery(crit.getFlag(), crit.getOperator().isSet(), inMailboxes, recentUids);
        } else if (criterion instanceof SearchQuery.TextCriterion) {
            SearchQuery.TextCriterion crit = (SearchQuery.TextCriterion) criterion;
            return createTextQuery(crit);
        } else if (criterion instanceof SearchQuery.AllCriterion) {
            return createAllQuery((AllCriterion) criterion);
        } else if (criterion instanceof SearchQuery.ConjunctionCriterion) {
            SearchQuery.ConjunctionCriterion crit = (SearchQuery.ConjunctionCriterion) criterion;
            return createConjunctionQuery(crit, inMailboxes, recentUids);
        } else if (criterion instanceof SearchQuery.ModSeqCriterion) {
            return createModSeqQuery((SearchQuery.ModSeqCriterion) criterion);
        }
        throw new UnsupportedSearchException();

    }

    

    /**
     * @see org.apache.james.mailbox.store.search.ListeningMessageSearchIndex#add(org.apache.james.mailbox.MailboxSession, org.apache.james.mailbox.store.mail.model.Mailbox, MailboxMessage)
     */
    @Override
    public void add(MailboxSession session, Mailbox mailbox, MailboxMessage membership) throws MailboxException {
        Document doc = createMessageDocument(session, membership);
        Document flagsDoc = createFlagsDocument(membership);

        try {
            writer.addDocument(doc);
            writer.addDocument(flagsDoc);
        } catch (IOException e) {
            throw new MailboxException("Unable to add message to index", e);
        }
    }

    /**
     * @see ListeningMessageSearchIndex#update
     */
    @Override
    public void update(MailboxSession session, Mailbox mailbox, List<UpdatedFlags> updatedFlagsList) throws MailboxException {
        for (UpdatedFlags updatedFlags : updatedFlagsList) {
            update(mailbox, updatedFlags.getUid(), updatedFlags.getNewFlags());
        }
    }

    private void update(Mailbox mailbox, MessageUid uid, Flags f) throws MailboxException {
        IndexSearcher searcher = null;
        try {
            searcher = new IndexSearcher(IndexReader.open(writer, true));
            BooleanQuery query = new BooleanQuery();
            query.add(new TermQuery(new Term(MAILBOX_ID_FIELD, mailbox.getMailboxId().serialize())), BooleanClause.Occur.MUST);
            query.add(createQuery(MessageRange.one(uid)), BooleanClause.Occur.MUST);
            query.add( new PrefixQuery(new Term(FLAGS_FIELD, "")), BooleanClause.Occur.MUST);

            TopDocs docs = searcher.search(query, 100000);
            ScoreDoc[] sDocs = docs.scoreDocs;
            for (ScoreDoc sDoc : sDocs) {
                Document doc = searcher.doc(sDoc.doc);

                if (doc.getFieldable(FLAGS_FIELD) == null) {
                    doc.removeFields(FLAGS_FIELD);
                    indexFlags(doc, f);

                    writer.updateDocument(new Term(ID_FIELD, doc.get(ID_FIELD)), doc);

                }
            }
        } catch (IOException e) {
            throw new MailboxException("Unable to add messages in index", e);

        } finally {
            try {
                IOUtils.closeWhileHandlingException(searcher);
            } catch (IOException e) {
                //can't happen anyway
            }
        }
        
    }

    /**
     * Index the {@link Flags} and add it to the {@link Document}
     */
    private Document createFlagsDocument(MailboxMessage message) {
        Document doc = new Document();
        doc.add(new Field(ID_FIELD, "flags-" + message.getMailboxId().serialize() +"-" + Long.toString(message.getUid().asLong()), Store.YES, Index.NOT_ANALYZED));
        doc.add(new Field(MAILBOX_ID_FIELD, message.getMailboxId().serialize(), Store.YES, Index.NOT_ANALYZED));
        doc.add(new NumericField(UID_FIELD,Store.YES, true).setLongValue(message.getUid().asLong()));
        
        indexFlags(doc, message.createFlags());
        return doc;
    }
    
    /**
     * Add the given {@link Flags} to the {@link Document}
     * 
     * @param doc
     * @param f
     */
    private void indexFlags(Document doc, Flags f) {
        List<String> fString = new ArrayList<>();
        Flag[] flags = f.getSystemFlags();
        for (Flag flag : flags) {
            fString.add(toString(flag));
            doc.add(new Field(FLAGS_FIELD, toString(flag), Store.NO, Index.NOT_ANALYZED));
        }
        
        String[] userFlags = f.getUserFlags();
        for (String userFlag : userFlags) {
            doc.add(new Field(FLAGS_FIELD, userFlag, Store.NO, Index.NOT_ANALYZED));
        }
        
        // if no flags are there we just use a empty field
        if (flags.length == 0 && userFlags.length == 0) {
            doc.add(new Field(FLAGS_FIELD, "",Store.NO, Index.NOT_ANALYZED));
        }
        
    }
    
    private Query createQuery(MessageRange range) {
        switch (range.getType()) {
        case ONE:
            return NumericRangeQuery.newLongRange(UID_FIELD, 
                    range.getUidFrom().asLong(), 
                    range.getUidTo().asLong(), true, true);
        case FROM:
            return NumericRangeQuery.newLongRange(UID_FIELD, 
                    range.getUidFrom().asLong(), 
                    MessageUid.MAX_VALUE.asLong(), true, true);
        default:
            return NumericRangeQuery.newLongRange(UID_FIELD, MessageUid.MIN_VALUE.asLong(), MessageUid.MAX_VALUE.asLong(), true, true);
        }
    }

    @Override
    public void delete(MailboxSession session, Mailbox mailbox, List<MessageUid> expungedUids) throws MailboxException {
        Collection<MessageRange> messageRanges = MessageRange.toRanges(expungedUids);
        for (MessageRange messageRange : messageRanges) {
            delete(mailbox, messageRange);
        }
    }

    @Override
    public void deleteAll(MailboxSession session, Mailbox mailbox) throws MailboxException {
        delete(mailbox, MessageRange.all());
    }

    public void delete(Mailbox mailbox, MessageRange range) throws MailboxException {
        BooleanQuery query = new BooleanQuery();
        query.add(new TermQuery(new Term(MAILBOX_ID_FIELD, mailbox.getMailboxId().serialize())), BooleanClause.Occur.MUST);
        query.add(createQuery(range), BooleanClause.Occur.MUST);
        
        try {
            writer.deleteDocuments(query);
        } catch (IOException e) {
            throw new MailboxException("Unable to delete message from index", e);
        }
    }
}