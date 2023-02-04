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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.ContentType;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * A default text extractor that is directly based on the input file provided.
 * 
 * Costs fewer calculations than TikaTextExtractor, but result is not that good.
 */
public class DefaultTextExtractor implements TextExtractor {
    @Override
    public boolean applicable(ContentType contentType) {
        return contentType != null && contentType.mediaType().equals(ContentType.MediaType.TEXT);
    }

    @Override
    public ParsedContent extractContent(InputStream inputStream, ContentType contentType) throws Exception {
        try (var input = inputStream) {
            if (applicable(contentType)) {
                Charset charset = contentType.charset().orElse(StandardCharsets.UTF_8);
                return ParsedContent.of(IOUtils.toString(input, charset));
            } else {
                return ParsedContent.empty();
            }
        }
    }

    @Override
    public Mono<ParsedContent> extractContentReactive(InputStream inputStream, ContentType contentType) {
        if (applicable(contentType)) {
            Charset charset = contentType.charset().orElse(StandardCharsets.UTF_8);
            return Mono.using(() -> inputStream,
                    stream -> Mono.fromCallable(() -> ParsedContent.of(IOUtils.toString(stream, charset)))
                            .subscribeOn(Schedulers.boundedElastic()),
                    Throwing.consumer(InputStream::close).orDoNothing());
        } else {
            return Mono.just(ParsedContent.empty());
        }
    }
}
