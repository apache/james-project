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

package org.apache.james.mailbox.store.extractor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.google.common.collect.Maps;

public class JsoupTextExtractor implements TextExtractor {
    private static final String TITLE_HTML_TAG = "title";

    @Override
    public ParsedContent extractContent(InputStream inputStream, String contentType) throws Exception {
        if (inputStream == null) {
            return ParsedContent.empty();
        }
        Map<String, List<String>> emptyMetadata = Maps.newHashMap();
        if (contentType != null) {
            if (contentType.equals("text/html")) {
                Document doc = Jsoup.parse(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
                doc.select(TITLE_HTML_TAG).remove();
                return new ParsedContent(Optional.ofNullable(doc.text()), emptyMetadata);
            }
            if (contentType.equals("text/plain")) {
                return new ParsedContent(Optional.ofNullable(IOUtils.toString(inputStream, StandardCharsets.UTF_8)), emptyMetadata);
            }
        }
        return ParsedContent.empty();
    }
}
