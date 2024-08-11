package org.apache.james.mailbox.lucene.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

class LuceneTestsUtils {

    private final static Logger log = LoggerFactory.getLogger(LuceneTestsUtils.class);
    static final Function<Document, String> documentStringFormatter = field -> "\n\t * " + field;

    static List<Document> getAllDocumentsFromRepository(IndexReader reader) {
        List<Document> result = new ArrayList<>(reader.maxDoc());
        for (int i = 0; i < reader.maxDoc(); i++) {
            try {
                result.add(reader.storedFields().document(i));
            } catch (IOException e) {
                log.error("Problem getting document for index: {}", i);
            }
        }
        return result;
    }
}
