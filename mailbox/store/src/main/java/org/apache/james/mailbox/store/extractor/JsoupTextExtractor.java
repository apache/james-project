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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.ContentType.MimeType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.google.common.collect.ImmutableMap;

public class JsoupTextExtractor implements TextExtractor {
    private static final String TITLE_HTML_TAG = "title";
    private static final String NO_BASE_URI = "";
    private static final Map<String, List<String>> EMPTY_METADATA = ImmutableMap.of();
    private static final MimeType TEXT_HTML = MimeType.of("text/html");
    private static final MimeType TEXT_PLAIN = MimeType.of("text/plain");

    @Override
    public ParsedContent extractContent(InputStream inputStream, ContentType contentType) throws Exception {
        if (inputStream == null || contentType == null) {
            return ParsedContent.empty();
        }
        if (contentType.mimeType().equals(TEXT_HTML)) {
            return parseHtmlContent(inputStream);
        }
        if (contentType.mimeType().equals(TEXT_PLAIN)) {
            return parsePlainTextContent(inputStream);
        }
        return ParsedContent.empty();
    }

    private ParsedContent parsePlainTextContent(InputStream inputStream) throws IOException {
        return new ParsedContent(Optional.ofNullable(IOUtils.toString(inputStream, StandardCharsets.UTF_8)), EMPTY_METADATA);
    }

    private ParsedContent parseHtmlContent(InputStream inputStream) throws IOException {
        Document doc = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), NO_BASE_URI);
        doc.select(TITLE_HTML_TAG).remove();
        return new ParsedContent(Optional.ofNullable(doc.text()), EMPTY_METADATA);
    }
}
