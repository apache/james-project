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
import java.util.function.Function;

import static org.apache.james.mailbox.lucene.search.LuceneTestsUtils.documentStringFormatter;
import static org.apache.james.mailbox.lucene.search.LuceneTestsUtils.getAllDocumentsFromRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LuceneGenericTests {

    private static final String TITLE_FIELD = "title";
    private static final String BODY_FIELD = "body";
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
        var originalTitle1 = "title1";
        var newSuperFancyNewTitle = "super fancy new title";
        var secondBoringTitle = "boring new title";

        var document = new Document();
        document.add(new StringField(TITLE_FIELD, "title1", Field.Store.YES));
        document.add(new StringField(BODY_FIELD, "body1", Field.Store.YES));
        writer.addDocument(document);

        var document2 = new Document();
        document2.add(new StringField(TITLE_FIELD, "title2", Field.Store.YES));
        document2.add(new StringField(BODY_FIELD, "body2", Field.Store.YES));
        writer.addDocument(document2);



        try (IndexReader reader = DirectoryReader.open(writer)) {
            log.trace("Repository initial state: {}", getAllDocumentsFromRepository(reader).stream().map(documentStringFormatter).toList());
            assertEquals(2, reader.maxDoc());

            var indexSearcher = new IndexSearcher(reader);

            var term = new Term(TITLE_FIELD, originalTitle1);
            var termQuery = new TermQuery(term);
            var foundDocuments = indexSearcher.search(termQuery, 50);

            assertEquals(1, foundDocuments.scoreDocs.length);

            for (ScoreDoc foundDocument : foundDocuments.scoreDocs) {
                var doc = reader.storedFields().document(foundDocument.doc);
                log.trace("Found document before first edit: \n\t* {}", doc);

                assertEquals(originalTitle1, doc.get(TITLE_FIELD));

                doc.removeField(TITLE_FIELD);
                doc.add(new StringField(TITLE_FIELD, newSuperFancyNewTitle, Field.Store.YES));
                writer.updateDocument(term, doc);
            }
        }

        try (IndexReader reader = DirectoryReader.open(writer)) {
            assertEquals(2, reader.maxDoc());

            var indexSearcher = new IndexSearcher(reader);

            // let's search for old one (expect not found)
            var term = new Term(TITLE_FIELD, originalTitle1);
            var foundDocuments = indexSearcher.search(new TermQuery(term), 50);

            assertEquals(0, foundDocuments.scoreDocs.length);

            log.trace("All documents after first update: {}", getAllDocumentsFromRepository(reader).stream().map(documentStringFormatter).toList());

            // let's search for the new one
            term = new Term(TITLE_FIELD, newSuperFancyNewTitle);
            TopDocs foundDocuments2 = indexSearcher.search(new TermQuery(term), 5);

            assertEquals(1, foundDocuments2.scoreDocs.length);

            for (ScoreDoc foundDocument : foundDocuments2.scoreDocs) {
                var doc = reader.storedFields().document(foundDocument.doc);
                log.trace("Found document before second edit: \n\t* {}", doc);
                assertEquals(newSuperFancyNewTitle, doc.get(TITLE_FIELD));

                doc.removeField(TITLE_FIELD);
                doc.add(new StringField(TITLE_FIELD, secondBoringTitle, Field.Store.YES));
                writer.updateDocument(term, doc);
            }
        }

        try (IndexReader reader = DirectoryReader.open(writer)) {
            IndexSearcher indexSearcher = new IndexSearcher(reader);

            // let's search for old one (expect not found)
            var term = new Term(TITLE_FIELD, newSuperFancyNewTitle);
            var foundDocuments = indexSearcher.search(new TermQuery(term), 5);

            assertEquals(0, foundDocuments.scoreDocs.length);

            log.trace("All documents after second update: {}", getAllDocumentsFromRepository(reader).stream().map(documentStringFormatter).toList());

            // let's search for the new boring one
            term = new Term(TITLE_FIELD, secondBoringTitle);
            foundDocuments = indexSearcher.search(new TermQuery(term), 5);

            for (ScoreDoc foundDocument : foundDocuments.scoreDocs) {
                final Document doc = reader.storedFields().document(foundDocument.doc);
                log.trace("Found document after second edit: \n\t* {}", doc);
                assertEquals(secondBoringTitle, doc.get(TITLE_FIELD));
            }
        }
    }
}
