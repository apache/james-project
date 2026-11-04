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

package org.apache.james.imap.encode.base;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ImapResponseComposerImplTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void quoteShouldNeverWriteCrLfOrNul(boolean utf8Accepted) throws Exception {
        ByteImapResponseWriter writer = new ByteImapResponseWriter();
        ImapResponseComposerImpl composer = new ImapResponseComposerImpl(writer, 1024);
        composer.setUtf8Accepted(utf8Accepted);

        composer.untagged().message("FETCH");
        composer.quote("\r\n* 1 EXPUNGE");
        composer.quote("en-\0Ret");
        composer.end();
        composer.flush();

        assertThat(writer.getString()).isEqualTo("* FETCH \"  * 1 EXPUNGE\" \"en- Ret\"\r\n");
    }
}
