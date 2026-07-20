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

package org.apache.james.imap.message;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

class SequencedLiteralTest {
    private static Literal streamedLiteral(byte[] content) {
        return new Literal() {
            @Override
            public long size() {
                return content.length;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(content);
            }
        };
    }

    @Test
    void sizeShouldBeTheSumOfTheParts() throws Exception {
        SequencedLiteral literal = new SequencedLiteral(List.of(
            BytesBackedLiteral.of("ab".getBytes(US_ASCII)),
            BytesBackedLiteral.of("cde".getBytes(US_ASCII))));

        assertThat(literal.size()).isEqualTo(5);
    }

    @Test
    void getInputStreamShouldConcatenateTheParts() throws Exception {
        SequencedLiteral literal = new SequencedLiteral(List.of(
            BytesBackedLiteral.of(" (BODY[] {15}\r\n".getBytes(US_ASCII)),
            BytesBackedLiteral.of("Body of Initial".getBytes(US_ASCII)),
            BytesBackedLiteral.of(")\r\n".getBytes(US_ASCII))));

        assertThat(new String(IOUtils.toByteArray(literal.getInputStream()), US_ASCII))
            .isEqualTo(" (BODY[] {15}\r\nBody of Initial)\r\n");
    }

    @Test
    void asBytesSequenceShouldAggregateChunksInOrderWhenAllPartsAreInMemory() throws Exception {
        SequencedLiteral literal = new SequencedLiteral(List.of(
            BytesBackedLiteral.of("ab".getBytes(US_ASCII)),
            BytesBackedLiteral.of("cde".getBytes(US_ASCII))));

        Optional<byte[][]> chunks = literal.asBytesSequence();

        assertThat(chunks).isPresent();
        assertThat(chunks.get().length).isEqualTo(2);
        assertThat(new String(chunks.get()[0], US_ASCII)).isEqualTo("ab");
        assertThat(new String(chunks.get()[1], US_ASCII)).isEqualTo("cde");
    }

    @Test
    void asBytesSequenceShouldBeEmptyWhenAPartIsNotInMemory() {
        SequencedLiteral literal = new SequencedLiteral(List.of(
            BytesBackedLiteral.of("ab".getBytes(US_ASCII)),
            streamedLiteral("cde".getBytes(US_ASCII))));

        assertThat(literal.asBytesSequence()).isEmpty();
    }
}
