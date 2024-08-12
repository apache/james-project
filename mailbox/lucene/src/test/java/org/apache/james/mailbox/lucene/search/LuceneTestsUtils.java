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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LuceneTestsUtils {

    static final Function<Document, String> documentStringFormatter = field -> "\n\t * " + field;
    private static final Logger log = LoggerFactory.getLogger(LuceneTestsUtils.class);

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
