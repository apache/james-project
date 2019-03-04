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

package org.apache.james.blob.objectstorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.objectstorage.crypto.CryptoConfig;
import org.jclouds.io.Payloads;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import com.google.crypto.tink.subtle.Hex;

class AESPayloadCodecTest implements PayloadCodecContract {
    private static final byte[] ENCRYPTED_BYTES = Hex.decode("28cdfb53c283185598ec7c49c415c6b56e85d3d74af89740270c2d2cd8006e1265a301436d919ed7acfc14586b5bd193e34c744ef1641230457dae3475");

    @Override
    public PayloadCodec codec() {
        return new AESPayloadCodec(
            new CryptoConfig(
                "c603a7327ee3dcbc031d8d34b1096c605feca5e1",
                "foobar".toCharArray()));
    }

    @Test
    void aesCodecShouldEncryptPayloadContentWhenWriting() throws Exception {
        Payload payload = codec().write(expected());
        byte[] bytes = IOUtils.toByteArray(payload.getPayload().openStream());
        // authenticated encryption uses a random salt for the authentication
        // header all we can say for sure is that the output is not the same as
        // the input.
        assertThat(bytes).isNotEqualTo(SOME_BYTES);
    }

    @Test
    void aesCodecShouldDecryptPayloadContentWhenReading() throws Exception {
        Payload payload = new Payload(Payloads.newInputStreamPayload(new ByteArrayInputStream(ENCRYPTED_BYTES)), Optional.empty());

        InputStream actual = codec().read(payload);

        assertThat(actual).hasSameContentAs(expected());
    }

    @Test
    void aesCodecShouldRaiseExceptionWhenUnderliyingInputStreamFails() throws Exception {
        Payload payload =
            new Payload(Payloads.newInputStreamPayload(new FilterInputStream(new ByteArrayInputStream(ENCRYPTED_BYTES)) {
                private int readCount = 0;

                @Override
                public int read(@NotNull byte[] b, int off, int len) throws IOException {
                    if (readCount >= ENCRYPTED_BYTES.length / 2) {
                        throw new IOException();
                    } else {
                        readCount += len;
                        return super.read(b, off, len);
                    }
                }
            }),
            Optional.empty());
        int i = ENCRYPTED_BYTES.length / 2;
        byte[] bytes = new byte[i];
        InputStream is = codec().read(payload);
        assertThatThrownBy(() -> is.read(bytes, 0, i)).isInstanceOf(IOException.class);

    }
}
