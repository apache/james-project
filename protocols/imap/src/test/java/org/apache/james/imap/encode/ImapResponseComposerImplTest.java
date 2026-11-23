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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImapResponseComposerImplTest {
    private ByteImapResponseWriter writer;
    private ImapResponseComposer composer;

    @BeforeEach
    void setUp() {
        writer = new ByteImapResponseWriter();
        composer = new ImapResponseComposerImpl(writer);
    }

    @Test
    void flagsShouldSkipStoredUserFlagsThatAreNotAtoms() throws Exception {
        Flags flags = new Flags(Flags.Flag.SEEN);
        flags.add("x)]");
        flags.add("$Forwarded");
        composer.flags(flags);
        composer.end();
        composer.flush();

        assertThat(writer.getString()).isEqualTo(" FLAGS (\\Seen $Forwarded)\r\n");
    }

    @Test
    void permanentFlagsShouldSkipStoredUserFlagsThatAreNotAtoms() {
        Flags flags = new Flags(Flags.Flag.USER);
        flags.add("x)]");
        flags.add("$Forwarded");

        assertThat(MessageFlags.names(flags)).containsExactly("$Forwarded", "\\*");
    }

    @Test
    void encodeShouldHandleQuotedChar128() throws Exception {
        Character c = 128;
        composer.quote(c.toString());
        composer.end();
        composer.flush();

        assertThat(writer.getString()).isEqualTo(" \"?\"\r\n");
    }

    @Test
    void quoteUpperCaseAsciiShouldEscapeQuoteAndBackslash() throws Exception {
        composer.quoteUpperCaseAscii("pl\"a\\in");
        composer.end();
        composer.flush();

        assertThat(writer.getString()).isEqualTo(" \"PL\\\"A\\\\IN\"\r\n");
    }

    @Test
    void quoteUpperCaseAsciiShouldReplaceNonAsciiCharacters() throws Exception {
        composer.quoteUpperCaseAscii("pl" + (char) 233 + "in");
        composer.end();
        composer.flush();

        assertThat(writer.getString()).isEqualTo(" \"PL?IN\"\r\n");
    }

    @Test
    void flagsShouldSkipStoredUserFlagsThatAreNotAtoms() throws Exception {
        Flags flags = new Flags(Flags.Flag.SEEN);
        flags.add("x)]");
        flags.add("$Forwarded");
        composer.flags(flags);
        composer.end();
        composer.flush();

        assertThat(writer.getString()).isEqualTo(" FLAGS (\\Seen $Forwarded)\r\n");
    }

    @Test
    void permanentFlagsShouldSkipStoredUserFlagsThatAreNotAtoms() {
        Flags flags = new Flags(Flags.Flag.USER);
        flags.add("x)]");
        flags.add("$Forwarded");

        assertThat(MessageFlags.names(flags)).containsExactly("$Forwarded", "\\*");
    }
}
