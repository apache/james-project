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

package org.apache.james.jdkim.mailets;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class HeaderSkippingOutputStreamTest {
    private static String skip(byte[] buffer, int off, int len) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (HeaderSkippingOutputStream stream = new HeaderSkippingOutputStream(out)) {
            stream.write(buffer, off, len);
        }
        return out.toString(StandardCharsets.US_ASCII);
    }

    private static String skipByteByByte(byte[] buffer) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (HeaderSkippingOutputStream stream = new HeaderSkippingOutputStream(out)) {
            for (byte b : buffer) {
                stream.write(b);
            }
        }
        return out.toString(StandardCharsets.US_ASCII);
    }

    @Test
    void writeShouldSkipHeaders() throws IOException {
        byte[] buffer = "H: v\r\n\r\nbody".getBytes(StandardCharsets.US_ASCII);

        assertThat(skip(buffer, 0, buffer.length)).isEqualTo("body");
    }

    @Test
    void writeShouldHonourTheOffset() throws IOException {
        byte[] buffer = "XXXXXXH: v\r\n\r\nbody".getBytes(StandardCharsets.US_ASCII);

        assertThat(skip(buffer, 6, buffer.length - 6)).isEqualTo("body");
    }

    @Test
    void writeShouldHonourTheOffsetAndTheLength() throws IOException {
        byte[] buffer = "XXH: v\r\n\r\nbodyYY".getBytes(StandardCharsets.US_ASCII);

        assertThat(skip(buffer, 2, buffer.length - 4)).isEqualTo("body");
    }

    @Test
    void separatorRightAfterACarriageReturnShouldBeFound() throws IOException {
        byte[] buffer = "H: v\r\r\n\r\nbody".getBytes(StandardCharsets.US_ASCII);

        assertThat(skip(buffer, 0, buffer.length)).isEqualTo("body");
    }

    @Test
    void separatorRightAfterACarriageReturnShouldBeFoundWhenWritingByteByByte() throws IOException {
        byte[] buffer = "H: v\r\r\n\r\nbody".getBytes(StandardCharsets.US_ASCII);

        assertThat(skipByteByByte(buffer)).isEqualTo("body");
    }

    @Test
    void separatorAfterCrLfCrShouldBeFound() throws IOException {
        byte[] buffer = "H: v\r\n\r\r\n\r\nbody".getBytes(StandardCharsets.US_ASCII);

        assertThat(skip(buffer, 0, buffer.length)).isEqualTo("body");
    }

    @Test
    void separatorSplitAcrossWritesShouldBeFound() throws IOException {
        byte[] buffer = "H: v\r\n\r\nbody".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (HeaderSkippingOutputStream stream = new HeaderSkippingOutputStream(out)) {
            stream.write(buffer, 0, 6);
            stream.write(buffer, 6, buffer.length - 6);
        }

        assertThat(out.toString(StandardCharsets.US_ASCII)).isEqualTo("body");
    }
}
