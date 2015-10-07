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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedSearchException;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AllCriterion;
import org.apache.james.mailbox.model.SearchQuery.ContainsOperator;
import org.apache.james.mailbox.model.SearchQuery.Criterion;
import org.apache.james.mailbox.model.SearchQuery.CustomFlagCriterion;
import org.apache.james.mailbox.model.SearchQuery.DateOperator;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.mailbox.model.SearchQuery.FlagCriterion;
import org.apache.james.mailbox.model.SearchQuery.HeaderCriterion;
import org.apache.james.mailbox.model.SearchQuery.HeaderOperator;
import org.apache.james.mailbox.model.SearchQuery.NumericOperator;
import org.apache.james.mailbox.model.SearchQuery.NumericRange;
import org.apache.james.mailbox.model.SearchQuery.UidCriterion;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.Message;
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
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
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

/**
 * Lucene based {@link ListeningMessageSearchIndex} which offers message searching via a Lucene index
 * 
 * 

 * @param <Id>
 */
public class LuceneMessageSearchIndex<Id extends MailboxId> extends ListeningMessageSearchIndex<Id> {
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
     * {@link Field} which will contain uid of the {@link Message}
     */
    public final static String UID_FIELD = "uid";
    
    /**
     * {@link Field} which will contain the {@link Flags} of the {@link Message}
     */
    public final static String FLAGS_FIELD = "flags";
  
    /**
     * {@link Field} which will contain the size of the {@link Message}
     */
    public final static String SIZE_FIELD = "size";

    /**
     * {@link Field} which will contain the body of the {@link Message}
     */
    public final static String BODY_FIELD = "body";
    
    
    /**
     * Prefix which will be used for each message header to store it also in a seperate {@link Field}
     */
    public final static String PREFIX_HEADER_FIELD ="header_";
    
    /**
     * {@link Field} which will contain the whole message header of the {@link Message}
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
    
    private final IndexWriter writer;
    
    private int maxQueryResults = DEFAULT_MAX_QUERY_RESULTS;

    private boolean suffixMatch = false;
    
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

    
    public LuceneMessageSearchIndex(MessageMapperFactory<Id> factory, Directory directory) throws CorruptIndexException, LockObtainFailedException, IOException {
        this(factory, directory, false, true);
    }
    
    
    public LuceneMessageSearchIndex(MessageMapperFactory<Id> factory, Directory directory, boolean dropIndexOnStart, boolean lenient) throws CorruptIndexException, LockObtainFailedException, IOException {
        super(factory);
        this.writer = new IndexWriter(directory,  createConfig(createAnalyzer(lenient), dropIndexOnStart));
    }
    
    
    public LuceneMessageSearchIndex(MessageMapperFactory<Id> factory, IndexWriter writer) {
        super(factory);
        this.writer = writer;
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
     * Create a {@link Analyzer} which is used to index the {@link Message}'s
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
    
    
    
    /**
     * @see org.apache.james.mailbox.store.search.MessageSearchIndex#search(org.apache.james.mailbox.MailboxSession, org.apache.james.mailbox.store.mail.model.Mailbox, org.apache.james.mailbox.model.SearchQuery)
     */
    public Iterator<Long> search(MailboxSession session, Mailbox<Id> mailbox, SearchQuery searchQuery) throws MailboxException {
        Set<Long> uids = new LinkedHashSet<Long>();
        IndexSearcher searcher = null;

        try {
            searcher = new IndexSearcher(IndexReader.open(writer, true));
            BooleanQuery query = new BooleanQuery();
            query.add(new TermQuery(new Term(MAILBOX_ID_FIELD, mailbox.getMailboxId().serialize())), BooleanClause.Occur.MUST);
            // Not return flags documents
            query.add(new PrefixQuery(new Term(FLAGS_FIELD, "")), BooleanClause.Occur.MUST_NOT);
            List<Criterion> crits = searchQuery.getCriterias();
            for (int i = 0; i < crits.size(); i++) {
                query.add(createQuery(crits.get(i), mailbox, searchQuery.getRecentMessageUids()), BooleanClause.Occur.MUST);
            }

            // query for all the documents sorted as specified in the SearchQuery
            TopDocs docs = searcher.search(query, null, maxQueryResults, createSort(searchQuery.getSorts()));
            ScoreDoc[] sDocs = docs.scoreDocs;
            for (int i = 0; i < sDocs.length; i++) {
                long uid = Long.valueOf(searcher.doc(sDocs[i].doc).get(UID_FIELD));
                uids.add(uid);
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
        return uids.iterator();
    }

   
    /**
     * Create a new {@link Document} for the given {@link Message}. This Document does not contain any flags data. The {@link Flags} are stored in a seperate Document. 
     * 
     * See {@link #createFlagsDocument(Message)}
     * 
     * @param membership
     * @return document
     */
    private Document createMessageDocument(final MailboxSession session, final Message<?> membership) throws MailboxException{
        final Document doc = new Document();
        // TODO: Better handling
        doc.add(new Field(MAILBOX_ID_FIELD, membership.getMailboxId().serialize().toUpperCase(Locale.ENGLISH), Store.YES, Index.NOT_ANALYZED));
        doc.add(new NumericField(UID_FIELD,Store.YES, true).setLongValue(membership.getUid()));
        
        // create an unqiue key for the document which can be used later on updates to find the document
        doc.add(new Field(ID_FIELD, membership.getMailboxId().serialize().toUpperCase(Locale.ENGLISH) +"-" + Long.toString(membership.getUid()), Store.YES, Index.NOT_ANALYZED));

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
                    String headerName = f.getName().toUpperCase(Locale.ENGLISH);
                    String headerValue = f.getBody().toUpperCase(Locale.ENGLISH);
                    String fullValue =  f.toString().toUpperCase(Locale.ENGLISH);
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
                            session.getLog().debug("Unable to parse Date header for proper indexing", e);
                            // This should never happen anyway fallback to the already parsed field
                            sentDate = ((DateTimeField) f).getDate();
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
                                        String value = AddressFormatter.DEFAULT.encode(mailbox).toUpperCase(Locale.ENGLISH);
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
                                            String value = AddressFormatter.DEFAULT.encode(mailbox).toUpperCase(Locale.ENGLISH);
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
                        doc.add(new Field(BODY_FIELD,  line.toUpperCase(Locale.ENGLISH),Store.NO, Index.ANALYZED));
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
        } catch (MimeException e) {
            // This should never happen as it was parsed before too without problems.            
            throw new MailboxException("Unable to index content of message", e);
        } catch (IOException e) {
            // This should never happen as it was parsed before too without problems.
            // anyway let us just skip the body and headers in the index
            throw new MailboxException("Unable to index content of message", e);
        }
       

        return doc;
    }

    private String toSentDateField(DateResolution res) {
        String field;
        switch (res) {
        case Year:
            field = SENT_DATE_FIELD_YEAR_RESOLUTION;
            break;
        case Month:
            field = SENT_DATE_FIELD_MONTH_RESOLUTION;
            break;
        case Day:
            field = SENT_DATE_FIELD_DAY_RESOLUTION;
            break;
        case Hour:
            field = SENT_DATE_FIELD_HOUR_RESOLUTION;
            break;
        case Minute:
            field = SENT_DATE_FIELD_MINUTE_RESOLUTION;
            break;
        case Second:
            field = SENT_DATE_FIELD_SECOND_RESOLUTION;
            break;
        default:
            field = SENT_DATE_FIELD_MILLISECOND_RESOLUTION;
            break;
        }
        return field;
    }


    private static Calendar getGMT() {
        return Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.ENGLISH);
    }

    
    private String toInteralDateField(DateResolution res) {
        String field;
        switch (res) {
        case Year:
            field = INTERNAL_DATE_FIELD_YEAR_RESOLUTION;
            break;
        case Month:
            field = INTERNAL_DATE_FIELD_MONTH_RESOLUTION;
            break;
        case Day:
            field = INTERNAL_DATE_FIELD_DAY_RESOLUTION;
            break;
        case Hour:
            field = INTERNAL_DATE_FIELD_HOUR_RESOLUTION;
            break;
        case Minute:
            field = INTERNAL_DATE_FIELD_MINUTE_RESOLUTION;
            break;
        case Second:
            field = INTERNAL_DATE_FIELD_SECOND_RESOLUTION;
            break;
        default:
            field = INTERNAL_DATE_FIELD_MILLISECOND_RESOLUTION;
            break;
        }
        return field;
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
        String name = crit.getHeaderName().toUpperCase(Locale.ENGLISH);
        String fieldName = PREFIX_HEADER_FIELD + name;
        if (op instanceof SearchQuery.ContainsOperator) {
            ContainsOperator cop = (ContainsOperator) op;
            return createTermQuery(fieldName, cop.getValue().toUpperCase(Locale.ENGLISH));
        } else if (op instanceof SearchQuery.ExistsOperator){
            return new PrefixQuery(new Term(fieldName, ""));
        } else if (op instanceof SearchQuery.DateOperator) {
                DateOperator dop = (DateOperator) op;
                String field = toSentDateField(dop.getDateResultion());
                return createQuery(field, dop);
        } else if (op instanceof SearchQuery.AddressOperator) {
            String field = name.toLowerCase(Locale.ENGLISH);
            return createTermQuery(field, ((SearchQuery.AddressOperator) op).getAddress().toUpperCase(Locale.ENGLISH));
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
     * 
     * @param crit
     * @return query
     * @throws UnsupportedSearchException
     */
    private Query createUidQuery(SearchQuery.UidCriterion crit) throws UnsupportedSearchException {
        NumericRange[] ranges = crit.getOperator().getRange();
        if (ranges.length == 1) {
            NumericRange range = ranges[0];
            return NumericRangeQuery.newLongRange(UID_FIELD, range.getLowValue(), range.getHighValue(), true, true);
        } else {
            BooleanQuery rangesQuery = new BooleanQuery();
            for (int i = 0; i < ranges.length; i++) {
                NumericRange range = ranges[i];
                rangesQuery.add(NumericRangeQuery.newLongRange(UID_FIELD, range.getLowValue(), range.getHighValue(), true, true), BooleanClause.Occur.SHOULD);
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
    
    /**
     * Return a {@link Query} which is build based on the given {@link SearchQuery.FlagCriterion}. This is kind of a hack
     * as it will do a search for the flags in this method and 
     * 
     * @param crit
     * @return query
     * @throws UnsupportedSearchException
     */
    private Query createFlagQuery(String flag, boolean isSet, Mailbox<?> mailbox, Collection<Long> recentUids) throws MailboxException, UnsupportedSearchException {
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
        query.add(new TermQuery(new Term(MAILBOX_ID_FIELD, mailbox.getMailboxId().serialize())), BooleanClause.Occur.MUST);
        
        
        IndexSearcher searcher = null;

        try {
            Set<Long> uids = new HashSet<Long>();
            searcher = new IndexSearcher(IndexReader.open(writer, true));
            
            // query for all the documents sorted by uid
            TopDocs docs = searcher.search(query, null, maxQueryResults, new Sort(UID_SORT));
            ScoreDoc[] sDocs = docs.scoreDocs;
            for (int i = 0; i < sDocs.length; i++) {
                long uid = Long.valueOf(searcher.doc(sDocs[i].doc).get(UID_FIELD));
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
            
            List<MessageRange> ranges = MessageRange.toRanges(new ArrayList<Long>(uids));
            NumericRange[] nRanges = new NumericRange[ranges.size()];
            for (int i = 0; i < ranges.size(); i++) {
                MessageRange range = ranges.get(i);
                nRanges[i] = new NumericRange(range.getUidFrom(), range.getUidTo());
            }
            return createUidQuery((UidCriterion) SearchQuery.uid(nRanges));
        } catch (IOException e) {
            throw new MailboxException("Unable to search mailbox " + mailbox, e);
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
        Sort sort = new Sort();
        List<SortField> fields = new ArrayList<SortField>();
        
        for (int i = 0; i < sorts.size(); i++) {
            SearchQuery.Sort s = sorts.get(i);
            boolean reverse = s.isReverse();
            SortField sf = null;
            
            switch (s.getSortClause()) {
            case Arrival:
                if (reverse) {
                    sf = ARRIVAL_MAILBOX_SORT_REVERSE;
                } else {
                    sf = ARRIVAL_MAILBOX_SORT;
                }
                break;
            case SentDate:
                if (reverse) {
                    sf = SENT_DATE_SORT_REVERSE;
                } else {
                    sf = SENT_DATE_SORT;
                }
                break;
            case MailboxCc:
                if (reverse) {
                    sf = FIRST_CC_MAILBOX_SORT_REVERSE;
                } else {
                    sf = FIRST_CC_MAILBOX_SORT;
                }
                break;
            case MailboxFrom:
                if (reverse) {
                    sf = FIRST_FROM_MAILBOX_SORT_REVERSE;
                } else {
                    sf = FIRST_FROM_MAILBOX_SORT;
                }
                break;
            case Size:
                if (reverse) {
                    sf = SIZE_SORT_REVERSE;
                } else {
                    sf = SIZE_SORT;
                }
                break;
            case BaseSubject:
                if (reverse) {
                    sf = BASE_SUBJECT_SORT_REVERSE;
                } else {
                    sf = BASE_SUBJECT_SORT;
                }
                break;
            case MailboxTo:
                if (reverse) {
                    sf = FIRST_TO_MAILBOX_SORT_REVERSE;
                } else {
                    sf = FIRST_TO_MAILBOX_SORT;
                }
                break;
                
            case Uid:
                if (reverse) {
                    sf = UID_SORT_REVERSE;
                } else {
                    sf = UID_SORT;
                }
                break;
            case DisplayFrom:
                if (reverse) {
                    sf = FIRST_FROM_MAILBOX_DISPLAY_SORT_REVERSE;;
                } else {
                    sf = FIRST_FROM_MAILBOX_DISPLAY_SORT;
                }
                break;
            case DisplayTo:
                if (reverse) {
                    sf = FIRST_TO_MAILBOX_DISPLAY_SORT_REVERSE;
                } else {
                    sf = FIRST_TO_MAILBOX_DISPLAY_SORT;
                }
                break;   
            default:
                break;
            }
            if (sf != null) {

                fields.add(sf);
                
                // Add the uid sort as tie-breaker
                if (sf == SENT_DATE_SORT) {
                    fields.add(UID_SORT);
                } else if (sf == SENT_DATE_SORT_REVERSE) {
                    fields.add(UID_SORT_REVERSE);
                }
            }
        }
        // add the uid sorting as last so if no other sorting was able todo the job it will get sorted by the uid
        fields.add(UID_SORT);
        sort.setSort(fields.toArray(new SortField[0]));
        return sort;
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
        String value = crit.getOperator().getValue().toUpperCase(Locale.ENGLISH);
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
    private Query createConjunctionQuery(SearchQuery.ConjunctionCriterion crit, Mailbox<?> mailbox, Collection<Long> recentUids) throws UnsupportedSearchException, MailboxException {
        List<Criterion> crits = crit.getCriteria();
        BooleanQuery conQuery = new BooleanQuery();
        switch (crit.getType()) {
        case AND:
            for (int i = 0; i < crits.size(); i++) {
                conQuery.add(createQuery(crits.get(i), mailbox, recentUids), BooleanClause.Occur.MUST);
            }
            return conQuery;
        case OR:
            for (int i = 0; i < crits.size(); i++) {
                conQuery.add(createQuery(crits.get(i), mailbox, recentUids), BooleanClause.Occur.SHOULD);
            }
            return conQuery;
        case NOR:
            BooleanQuery nor = new BooleanQuery();
            for (int i = 0; i < crits.size(); i++) {
                conQuery.add(createQuery(crits.get(i), mailbox, recentUids), BooleanClause.Occur.SHOULD);
            }
            nor.add(new TermQuery(new Term(MAILBOX_ID_FIELD, mailbox.getMailboxId().serialize())), BooleanClause.Occur.MUST);

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
    private Query createQuery(Criterion criterion, Mailbox<?> mailbox, Collection<Long> recentUids) throws UnsupportedSearchException, MailboxException {
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
            return createFlagQuery(toString(crit.getFlag()), crit.getOperator().isSet(), mailbox, recentUids);
        } else if (criterion instanceof SearchQuery.CustomFlagCriterion) {
            CustomFlagCriterion crit = (CustomFlagCriterion) criterion;
            return createFlagQuery(crit.getFlag(), crit.getOperator().isSet(), mailbox, recentUids);
        } else if (criterion instanceof SearchQuery.TextCriterion) {
            SearchQuery.TextCriterion crit = (SearchQuery.TextCriterion) criterion;
            return createTextQuery(crit);
        } else if (criterion instanceof SearchQuery.AllCriterion) {
            return createAllQuery((AllCriterion) criterion);
        } else if (criterion instanceof SearchQuery.ConjunctionCriterion) {
            SearchQuery.ConjunctionCriterion crit = (SearchQuery.ConjunctionCriterion) criterion;
            return createConjunctionQuery(crit, mailbox, recentUids);
        } else if (criterion instanceof SearchQuery.ModSeqCriterion) {
            return createModSeqQuery((SearchQuery.ModSeqCriterion) criterion);
        }
        throw new UnsupportedSearchException();

    }

    

    /**
     * @see org.apache.james.mailbox.store.search.ListeningMessageSearchIndex#add(org.apache.james.mailbox.MailboxSession, org.apache.james.mailbox.store.mail.model.Mailbox, org.apache.james.mailbox.store.mail.model.Message)
     */
    public void add(MailboxSession session, Mailbox<Id> mailbox, Message<Id> membership) throws MailboxException {
        Document doc = createMessageDocument(session, membership);
        Document flagsDoc = createFlagsDocument(membership);

        try {
            writer.addDocument(doc);
            writer.addDocument(flagsDoc);
        } catch (CorruptIndexException e) {
            throw new MailboxException("Unable to add message to index", e);
        } catch (IOException e) {
            throw new MailboxException("Unable to add message to index", e);
        }
    }

    /**
     * @see org.apache.james.mailbox.store.search.ListeningMessageSearchIndex#update(org.apache.james.mailbox.MailboxSession, org.apache.james.mailbox.store.mail.model.Mailbox, org.apache.james.mailbox.model.MessageRange, javax.mail.Flags)
     */
    public void update(MailboxSession session, Mailbox<Id> mailbox, MessageRange range, Flags f, long modSeq) throws MailboxException {
        IndexSearcher searcher = null;
        try {
            searcher = new IndexSearcher(IndexReader.open(writer, true));
            BooleanQuery query = new BooleanQuery();
            query.add(new TermQuery(new Term(MAILBOX_ID_FIELD, mailbox.getMailboxId().serialize())), BooleanClause.Occur.MUST);
            query.add(createQuery(range), BooleanClause.Occur.MUST);
            query.add( new PrefixQuery(new Term(FLAGS_FIELD, "")), BooleanClause.Occur.MUST);

            TopDocs docs = searcher.search(query, 100000);
            ScoreDoc[] sDocs = docs.scoreDocs;
            for (int i = 0; i < sDocs.length; i++) {
                Document doc = searcher.doc(sDocs[i].doc);
                
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
     * 
     * @param f
     * @param doc
     */
    private Document createFlagsDocument(Message<?> message) {
        Document doc = new Document();
        doc.add(new Field(ID_FIELD, "flags-" + message.getMailboxId().serialize() +"-" + Long.toString(message.getUid()), Store.YES, Index.NOT_ANALYZED));
        doc.add(new Field(MAILBOX_ID_FIELD, message.getMailboxId().serialize(), Store.YES, Index.NOT_ANALYZED));
        doc.add(new NumericField(UID_FIELD,Store.YES, true).setLongValue(message.getUid()));
        
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
        List<String> fString = new ArrayList<String>();
        Flag[] flags = f.getSystemFlags();
        for (int a = 0; a < flags.length; a++) {
            fString.add(toString(flags[a]));
            doc.add(new Field(FLAGS_FIELD, toString(flags[a]),Store.NO, Index.NOT_ANALYZED));
        }
        
        String[] userFlags = f.getUserFlags();
        for (int a = 0; a < userFlags.length; a++) {
            doc.add(new Field(FLAGS_FIELD, userFlags[a],Store.NO, Index.NOT_ANALYZED));
        }
        
        // if no flags are there we just use a empty field
        if (flags.length == 0 && userFlags.length == 0) {
            doc.add(new Field(FLAGS_FIELD, "",Store.NO, Index.NOT_ANALYZED));
        }
        
    }
    
    private Query createQuery(MessageRange range) {
        switch (range.getType()) {
        case ONE:
            return NumericRangeQuery.newLongRange(UID_FIELD, range.getUidFrom(), range.getUidTo(), true, true);
        case FROM:
            return NumericRangeQuery.newLongRange(UID_FIELD, range.getUidFrom(), Long.MAX_VALUE, true, true);
        default:
            return NumericRangeQuery.newLongRange(UID_FIELD, 0L, Long.MAX_VALUE, true, true);
        }
    }
    /**
     * @see org.apache.james.mailbox.store.search.ListeningMessageSearchIndex#delete(org.apache.james.mailbox.MailboxSession, org.apache.james.mailbox.store.mail.model.Mailbox, org.apache.james.mailbox.model.MessageRange)
     */
    public void delete(MailboxSession session, Mailbox<Id> mailbox, MessageRange range) throws MailboxException {
        BooleanQuery query = new BooleanQuery();
        query.add(new TermQuery(new Term(MAILBOX_ID_FIELD, mailbox.getMailboxId().serialize())), BooleanClause.Occur.MUST);
        query.add(createQuery(range), BooleanClause.Occur.MUST);
        
        try {
            writer.deleteDocuments(query);
        } catch (CorruptIndexException e) {
            throw new MailboxException("Unable to delete message from index", e);

        } catch (IOException e) {
            throw new MailboxException("Unable to delete message from index", e);
        }
    }
    


}
