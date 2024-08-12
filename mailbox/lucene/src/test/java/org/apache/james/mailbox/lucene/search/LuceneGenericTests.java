package org.apache.james.mailbox.lucene.search;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.apache.james.mailbox.lucene.search.LuceneTestsUtils.documentStringFormatter;
import static org.apache.james.mailbox.lucene.search.LuceneTestsUtils.getAllDocumentsFromRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LuceneGenericTests {

    private static final String ID_FIELD = "id";
    private static final String FLAGS_FIELD = "flags";
    private final static Logger log = LoggerFactory.getLogger(LuceneGenericTests.class);
    private static IndexWriter writer;

    @BeforeEach
    void setUp() throws IOException {
        var memoryIndex = new ByteBuffersDirectory();
        var analyzer = new StandardAnalyzer();
        var indexWriterConfig = new IndexWriterConfig(analyzer);
        writer = new IndexWriter(memoryIndex, indexWriterConfig);
    }

    @Test
    void testAddingAndUpdatingDocument() throws IOException {
        var flags_1_1_id = "flags-1-1";
        var seenFlag = "flags:/SEEN";
        var answeredFlag = "flags:/ANSWERED";

        var document = new Document();
        document.add(new StringField(ID_FIELD, flags_1_1_id, Field.Store.YES));
        document.add(new StringField(FLAGS_FIELD, "", Field.Store.YES));
        log.trace("Writing initial document for flags-1-1: {}", document);
        writer.addDocument(document);

        var document2 = new Document();
        document2.add(new StringField(ID_FIELD, "flags-1-2", Field.Store.YES));
        document2.add(new StringField(FLAGS_FIELD, "flags:/SEEN", Field.Store.YES));
        log.trace("Writing initial document for flags-1-2: {}", document);
        writer.addDocument(document2);

        try (IndexReader reader = DirectoryReader.open(writer)) {
            var indexSearcher = new IndexSearcher(reader);
            var term = new Term(ID_FIELD, flags_1_1_id);
            var termQuery = new TermQuery(term);
            var foundDocuments = indexSearcher.search(termQuery, 50);

            log.trace("Repository initial state, total: {}, docs: {}",
                    reader.maxDoc(), getAllDocumentsFromRepository(reader).stream().map(documentStringFormatter).toList());

            assertEquals(2, reader.maxDoc());
            assertEquals(1, foundDocuments.scoreDocs.length);

            for (ScoreDoc foundDocument : foundDocuments.scoreDocs) {
                var foundDoc = reader.storedFields().document(foundDocument.doc);
                log.trace("[1] Found document for first edit: \n\t* {}", foundDoc);
            }

            var doc = new Document();
            doc.add(new StringField(ID_FIELD, flags_1_1_id, Field.Store.YES));
            doc.add(new StringField(FLAGS_FIELD, seenFlag, Field.Store.YES));
            writer.updateDocument(term, doc);
        }

        try (IndexReader reader = DirectoryReader.open(writer)) {
            var indexSearcher = new IndexSearcher(reader);
            var term = new Term(ID_FIELD, flags_1_1_id);
            var termQuery = new TermQuery(term);
            var foundDocuments = indexSearcher.search(termQuery, 50);

            log.trace("After first edit, total: {}, found: {} docs matching term: '{}', all documents after first update: {}",
                    reader.maxDoc(),
                    foundDocuments.scoreDocs.length,
                    term,
                    getAllDocumentsFromRepository(reader).stream().map(documentStringFormatter).toList());

            assertEquals(2, reader.maxDoc());
            assertEquals(1, foundDocuments.scoreDocs.length);

            for (ScoreDoc foundDocument : foundDocuments.scoreDocs) {
                var foundDoc = reader.storedFields().document(foundDocument.doc);
                log.trace("[1] Found document for second edit: \n\t* {}", foundDoc);
            }

            var newDoc = new Document();
            newDoc.add(new StringField(ID_FIELD, flags_1_1_id, Field.Store.YES));
            newDoc.add(new StringField(FLAGS_FIELD, answeredFlag, Field.Store.YES));
            writer.updateDocument(term, newDoc);

            log.trace("[2] Updated document for second edit (with term '{}'): \n\t* {}", term, newDoc);
        }

        try (IndexReader reader = DirectoryReader.open(writer)) {
            var indexSearcher = new IndexSearcher(reader);
            var term = new Term(ID_FIELD, flags_1_1_id);
            var termQuery = new TermQuery(term);
            var foundDocuments = indexSearcher.search(termQuery, 50);

            log.trace("After second edit, total: {}, found: {} docs matching term: '{}', all documents after first update: {}",
                    reader.maxDoc(),
                    foundDocuments.scoreDocs.length,
                    term,
                    getAllDocumentsFromRepository(reader).stream().map(documentStringFormatter).toList());

            assertEquals(2, reader.maxDoc());
            assertEquals(1, foundDocuments.scoreDocs.length);

            for (ScoreDoc foundDocument : foundDocuments.scoreDocs) {
                var foundDoc = reader.storedFields().document(foundDocument.doc);
                log.trace("Found document *after* second edit: \n\t* {}", foundDoc);
            }
        }
    }
}
