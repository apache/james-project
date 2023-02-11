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

package org.apache.james.imap.encode;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.imap.encode.base.ByteImapResponseWriter;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.message.response.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

class SearchResponseEncoderTest {
    private static final LongList IDS = new LongArrayList();

    static {
        IDS.add(1L);
        IDS.add(4L);
        IDS.add(9L);
        IDS.add(16L);
    }

    private SearchResponse response;
    private SearchResponseEncoder encoder;
    private ByteImapResponseWriter writer = new ByteImapResponseWriter();
    private ImapResponseComposer composer = new ImapResponseComposerImpl(writer);

    @BeforeEach
    void setUp() throws Exception {
        response = new SearchResponse(IDS, null);
        encoder = new SearchResponseEncoder();
    }

    @Test
    void acceptableMessagesShouldReturnSearchResponseClass() {
        assertThat(encoder.acceptableMessages()).isEqualTo(SearchResponse.class);
    }

    @Test
    void testEncode() throws Exception {
        encoder.encode(response, composer);
        composer.flush();
        assertThat(writer.getString()).isEqualTo("* SEARCH 1 4 9 16\r\n");
    }
}
