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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.ContentType.MimeType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class JsoupTextExtractor implements TextExtractor {
    private static final String TITLE_HTML_TAG = "title";
    private static final String NO_BASE_URI = "";
    private static final MimeType TEXT_HTML = MimeType.of("text/html");
    private static final MimeType TEXT_PLAIN = MimeType.of("text/plain");

    @Override
    public boolean applicable(ContentType contentType) {
        if (contentType == null) {
            return false;
        }
        return contentType.mimeType().equals(TEXT_HTML) || contentType.mimeType().equals(TEXT_PLAIN);
    }

    @Override
    public ParsedContent extractContent(InputStream inputStream, ContentType contentType) throws Exception {
        if (inputStream == null || contentType == null) {
            return ParsedContent.empty();
        }
        Charset charset = contentType.charset().orElse(StandardCharsets.UTF_8);
        if (contentType.mimeType().equals(TEXT_HTML)) {
            return parseHtmlContent(inputStream, charset);
        }
        if (contentType.mimeType().equals(TEXT_PLAIN)) {
            return parsePlainTextContent(inputStream, charset);
        }
        return ParsedContent.empty();
    }

    @Override
    public Mono<ParsedContent> extractContentReactive(InputStream inputStream, ContentType contentType) {
        if (inputStream == null || contentType == null) {
            return Mono.just(ParsedContent.empty());
        }
        Charset charset = contentType.charset().orElse(StandardCharsets.UTF_8);
        if (contentType.mimeType().equals(TEXT_HTML)) {
            return Mono.fromCallable(() -> parseHtmlContent(inputStream, charset))
                .subscribeOn(Schedulers.boundedElastic());
        }
        if (contentType.mimeType().equals(TEXT_PLAIN)) {
            return Mono.fromCallable(() -> parsePlainTextContent(inputStream, charset))
                .subscribeOn(Schedulers.boundedElastic());
        }
        return Mono.just(ParsedContent.empty());
    }

    private ParsedContent parsePlainTextContent(InputStream inputStream, Charset charset) throws IOException {
        return ParsedContent.of(IOUtils.toString(inputStream, charset));
    }

    private ParsedContent parseHtmlContent(InputStream inputStream, Charset charset) throws IOException {
        Document doc = Jsoup.parse(inputStream, charset.name(), NO_BASE_URI);
        doc.select(TITLE_HTML_TAG).remove();
        return ParsedContent.of(doc.text());
    }
}
