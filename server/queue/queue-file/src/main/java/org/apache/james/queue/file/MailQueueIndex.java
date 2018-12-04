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
package org.apache.james.queue.file;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.supplier.SupplierChainer;
import com.google.common.collect.ImmutableList;
import org.apache.james.core.MailAddress;
import org.apache.james.queue.api.ManageableMailQueue.Type;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SetBasedFieldSelector;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.mailet.Mail;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Provides indexing of mails.
 * <p>
 * Uses {@code Lucene} to construct the underlying index.
 * <p>
 * It has several methods for adding, searching and deleting to/from the index.
 */
public class MailQueueIndex {
    private static final String QUEUE_ITEM_ID_KEY = "key";
    private static final SetBasedFieldSelector FIELDS_TO_LOAD = new SetBasedFieldSelector(Collections.singleton(QUEUE_ITEM_ID_KEY), Collections.emptySet());
    private final IndexWriter indexWriter;

    /**
     * Constructs new {@code MailQueueIndex} instance using provided {@code indexWriter}.
     *
     * @param indexWriter The index writer to maintain an index.
     */
    public MailQueueIndex(IndexWriter indexWriter) {
        this.indexWriter = Objects.requireNonNull(indexWriter);
    }

    /**
     * Constructs new {@code MailQueueIndex} instance using provided {@code indexDir}.
     * <p>
     * Uses Lucene version {@link Version#LUCENE_36} by default.
     *
     * @param indexDir The directory path in which index files will be stored.
     *
     * @throws IOException When cannot open {@code indexDir} or index already stored there were corrupted.
     */
    public MailQueueIndex(File indexDir) throws IOException {
        StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_36);
        this.indexWriter = new IndexWriter(FSDirectory.open(indexDir), new IndexWriterConfig(Version.LUCENE_36, analyzer));
    }

    private static Document toMailEnvelopeLuceneDocument(Mail mail, String key) {
        Document doc = new Document();

        doc.add(analyzedField(key, QUEUE_ITEM_ID_KEY));
        doc.add(analyzedField(mail.getName(), Type.Name.name()));
        doc.add(analyzedField(mail.getMaybeSender().asString(), Type.Sender.name()));

        if (mail.getRecipients() != null) {
            mail.getRecipients()
                    .stream()
                    .map(MailAddress::asString)
                    .forEach(mailAddress -> doc.add(analyzedField(mailAddress, Type.Recipient.name())));
        }

        return doc;
    }

    private static Field analyzedField(String key, String filePathKey) {
        return new Field(filePathKey, key, Field.Store.YES, Field.Index.NOT_ANALYZED);
    }

    private static Query toLuceneQuery(Type type, String value) {
        return new TermQuery(new Term(type.name(), value));
    }

    /**
     * Adds a mail to index.
     * <p>
     * The document that will be created based on {@code mail} consists of following fields:
     * <p>
     * <ul>
     * <li>Name: {@link Mail#getName()}</li>
     * <li>Sender: {@link Mail#getMaybeSender()}</li>
     * <li>Recipient: {@link Mail#getRecipients()}</li>
     * </ul>
     *
     * @param mail        Mail to be indexed.
     * @param queueItemId Identifier by which, subsequently, it will be possible to uniquely identify this {@code
     *                    mail}.
     *
     * @throws IOException When there is a low-level IO error.
     */
    public void index(Mail mail, String queueItemId) throws IOException {
        indexWriter.addDocument(toMailEnvelopeLuceneDocument(mail, queueItemId));
    }

    /**
     * Commits all pending changes.
     *
     * @throws IOException When there is a low-level IO error.
     */
    public void commit() throws IOException {
        indexWriter.commit();
    }

    /**
     * Searches document matching criteria.
     *
     * @param type  The field type.
     * @param value The field value.
     *
     * @return the queue item id list that matched provided criteria.
     *
     * @throws IOException When there is a low-level IO error.
     */
    public List<String> search(Type type, String value) throws IOException {
        try (IndexReader reader = IndexReader.open(indexWriter, true)) {
            int maxCount = Math.max(1, reader.maxDoc());
            IndexSearcher searcher = new IndexSearcher(reader);
            Query query = toLuceneQuery(type, value);

            return Stream.of(searcher.search(query, maxCount).scoreDocs)
                    .map(scoreDoc -> Throwing.supplier(() -> searcher.doc(scoreDoc.doc, FIELDS_TO_LOAD)))
                    .map(SupplierChainer::get)
                    .map(doc -> doc.get(QUEUE_ITEM_ID_KEY))
                    .collect(ImmutableList.toImmutableList());
        }
    }

    /**
     * The shortcut to perform searching and deletion. Invoking this method will have same result as following:
     * <p>
     * <pre>
     *     List<String> result = search(type, value);
     *     delete(type, value);
     * </pre>
     *
     * @param type  The field type.
     * @param value The field value.
     *
     * @return the queue item id list that matched provided criteria.
     *
     * @throws IOException When there is a low-level IO error.
     */
    public List<String> pop(Type type, String value) throws IOException {
        List<String> result = search(type, value);
        delete(type, value);

        return result;
    }

    /**
     * Deletes documents matching criteria.
     *
     * @param type  The field type.
     * @param value The field value.
     *
     * @throws IOException When there is a low-level IO error.
     */
    public void delete(Type type, String value) throws IOException {
        indexWriter.deleteDocuments(toLuceneQuery(type, value));
    }

    /**
     * Delete all documents in the index.
     *
     * @throws IOException When there is a low-level IO error.
     */
    public void deleteAll() throws IOException {
        indexWriter.deleteAll();
    }
}
