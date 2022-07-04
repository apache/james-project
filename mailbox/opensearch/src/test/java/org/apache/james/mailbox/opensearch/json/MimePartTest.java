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
package org.apache.james.mailbox.opensearch.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.model.ContentType.MediaType;
import org.apache.james.mailbox.model.ContentType.SubType;
import org.junit.jupiter.api.Test;

class MimePartTest {

    @Test
    void buildShouldWorkWhenTextualContentFromParserIsEmpty() {
        MimePart.builder(contentType -> true)
            .addBodyContent(new ByteArrayInputStream(new byte[] {}))
            .addMediaType(MediaType.of("text"))
            .addSubType(SubType.of("plain"))
            .build();
    }

    @Test
    void buildShouldWorkWhenTextualContentFromParserIsNonEmpty() {
        String body = "text";
        MimePart mimePart = MimePart.builder(contentType -> true)
            .addBodyContent(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)))
            .addMediaType(MediaType.of("text"))
            .addSubType(SubType.of("plain"))
            .build()
            .asMimePart((in, contentType) -> ParsedContent.empty())
            .block();

        assertThat(mimePart.getTextualBody()).contains(body);
    }
}
