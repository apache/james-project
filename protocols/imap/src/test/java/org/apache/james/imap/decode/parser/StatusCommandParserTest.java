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

package org.apache.james.imap.decode.parser;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import org.apache.james.imap.api.Tag;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.junit.jupiter.api.Test;

class StatusCommandParserTest {
    @Test
    void fuzzingInputShouldNotLeadToOutOfMemoryException() {
        String base64Input = "MiAgKH8MSU4kQiBOJEIgICMAf15Df39/f39/f39/f39/f39/f39/f39/f0NDRTogP19GbT8JCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCT0NAAAAAAAAAD0/TkNPAAAAAAhlAAAAAAA=";
        byte[] bytes = Base64.getDecoder().decode(base64Input);

        assertThatThrownBy(() -> new StatusCommandParser(new UnpooledStatusResponseFactory())
            .decode(new ImapRequestStreamLineReader(new ByteArrayInputStream(bytes),
                new ByteArrayOutputStream()), new Tag("AEA"), new FakeImapSession()))
        .isInstanceOf(DecodingException.class);
    }
}