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

package org.apache.james.mailbox.extractor;

import java.io.InputStream;

import org.apache.james.mailbox.model.ContentType;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public interface TextExtractor {
    default boolean applicable(ContentType contentType) {
        return true;
    }

    /**
     * This method will close the InputStream argument.
     */
    ParsedContent extractContent(InputStream inputStream, ContentType contentType) throws Exception;

    /**
     * This method will close the InputStream argument.
     */
    default Mono<ParsedContent> extractContentReactive(InputStream inputStream, ContentType contentType) {
        return Mono.using(() -> inputStream,
                stream -> Mono.fromCallable(() -> extractContent(stream, contentType)).subscribeOn(Schedulers.boundedElastic()),
                Throwing.consumer(InputStream::close).orDoNothing());
    }

}
