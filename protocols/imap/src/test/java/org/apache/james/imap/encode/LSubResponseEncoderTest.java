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
import org.apache.james.imap.message.response.LSubResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LSubResponseEncoderTest  {
    private LSubResponseEncoder encoder;

    private ByteImapResponseWriter writer = new ByteImapResponseWriter();
    private ImapResponseComposer composer = new ImapResponseComposerImpl(writer);

    @BeforeEach
    void setUp() {
        encoder = new LSubResponseEncoder();
    }

    @Test
    void encoderShouldAcceptLSubResponse() {
        assertThat(encoder.acceptableMessages()).isEqualTo(LSubResponse.class);
    }

    @Test
    void encoderShouldIncludeLSUBCommand() throws Exception {
        encoder.encode(new LSubResponse("name", true, '.'), composer);
        composer.flush();
        assertThat(writer.getString()).startsWith("* LSUB");
    }

}
