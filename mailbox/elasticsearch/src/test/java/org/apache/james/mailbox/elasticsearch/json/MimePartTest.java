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
package org.apache.james.mailbox.elasticsearch.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class MimePartTest {

    @Test
    void buildShouldWorkWhenTextualContentFromParserIsEmpty() {
        MimePart.builder()
            .addBodyContent(new ByteArrayInputStream(new byte[] {}))
            .addMediaType("text")
            .addSubType("plain")
            .build();
    }

    @Test
    void buildShouldWorkWhenTextualContentFromParserIsNonEmpty() {
        String body = "text";
        MimePart mimePart = MimePart.builder()
            .addBodyContent(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)))
            .addMediaType("text")
            .addSubType("plain")
            .build();
        
        assertThat(mimePart.getTextualBody()).contains(body);
    }
}
