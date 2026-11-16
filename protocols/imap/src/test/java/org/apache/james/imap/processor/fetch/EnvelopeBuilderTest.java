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

package org.apache.james.imap.processor.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.james.imap.message.response.FetchResponse;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.Headers;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.junit.jupiter.api.Test;

class EnvelopeBuilderTest {
    private static Headers headers(Header... headers) {
        List<Header> headerList = Arrays.asList(headers);
        return new Headers() {
            @Override
            public Iterator<Header> headers() {
                return headerList.iterator();
            }

            @Override
            public InputStream getInputStream() {
                throw new UnsupportedOperationException();
            }

            @Override
            public long size() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void displayNamesShouldNotKeepTheirRfc5322Quoting() throws Exception {
        FetchResponse.Envelope envelope = new EnvelopeBuilder().buildEnvelope(headers(
            new Header("From", "\"Doe, John\" <john@domain.tld>"),
            new Header("To", "\"Bob \\\"B\\\" Smith\" <bob@domain.tld>, \"Group (x)\" <g@domain.tld>, Plain Name <plain@domain.tld>")));

        assertThat(envelope.getFrom()[0].getPersonalName()).isEqualTo("Doe, John");
        assertThat(Arrays.stream(envelope.getTo()).map(FetchResponse.Envelope.Address::getPersonalName))
            .containsExactly("Bob \"B\" Smith", "Group (x)", "Plain Name");
    }

    @Test
    void nonAsciiDisplayNamesShouldBeEncoded() throws Exception {
        FetchResponse.Envelope envelope = new EnvelopeBuilder().buildEnvelope(headers(
            new Header("From", "=?UTF-8?Q?Ren=C3=A9?= <rene@domain.tld>")));

        assertThat(envelope.getFrom()[0].getPersonalName()).startsWith("=?").endsWith("?=");
    }

    @Test
    void rawUtf8HeadersShouldBeDecoded() throws Exception {
        FetchResponse.Envelope envelope = new EnvelopeBuilder().buildEnvelope(headers(
            new Header("From", "Ålice <alice@tmail.local>"),
            new Header("Subject", "é 日本")));

        assertThat(envelope.getFrom()[0].getHostName()).isEqualTo("tmail.local");
        assertThat(envelope.getFrom()[0].getMailboxName()).isEqualTo("alice");
        assertThat(DecoderUtil.decodeEncodedWords(envelope.getFrom()[0].getPersonalName(), DecodeMonitor.SILENT))
            .isEqualTo("Ålice");
        assertThat(DecoderUtil.decodeEncodedWords(envelope.getSubject(), DecodeMonitor.SILENT))
            .isEqualTo("é 日本");
    }
}
