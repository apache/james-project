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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.GeneralSecurityException;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.objectstorage.crypto.CryptoConfig;
import org.apache.james.blob.objectstorage.crypto.PBKDF2StreamingAeadFactory;
import org.jclouds.io.Payload;
import org.jclouds.io.Payloads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming;

public class AESPayloadCodec implements PayloadCodec {
    private static final Logger LOGGER = LoggerFactory.getLogger(AESPayloadCodec.class);
    private final AesGcmHkdfStreaming streamingAead;

    public AESPayloadCodec(CryptoConfig cryptoConfig) {
        streamingAead = PBKDF2StreamingAeadFactory.newAesGcmHkdfStreaming(cryptoConfig);
    }

    @Override
    public Payload write(InputStream is) {
        PipedInputStream snk = new PipedInputStream();
        try {
            PipedOutputStream src = new PipedOutputStream(snk);
            OutputStream outputStream = streamingAead.newEncryptingStream(src, PBKDF2StreamingAeadFactory.EMPTY_ASSOCIATED_DATA);
            Thread copyThread = new Thread(() -> {
                try (OutputStream stream = outputStream) {
                    IOUtils.copy(is, stream);
                } catch (IOException e) {
                    throw new RuntimeException("Stream copy failure ", e);
                }
            });
            copyThread.setUncaughtExceptionHandler((Thread t, Throwable e) ->
                LOGGER.error("Unable to encrypt payload's input stream",e)
            );
            copyThread.start();
            return Payloads.newInputStreamPayload(snk);
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Unable to build payload for object storage, failed to " +
                "encrypt", e);
        }
    }

    @Override
    public InputStream read(Payload payload) throws IOException {
        try {
            return streamingAead.newDecryptingStream(payload.openStream(), PBKDF2StreamingAeadFactory.EMPTY_ASSOCIATED_DATA);
        } catch (GeneralSecurityException e) {
            throw new IOException("Incorrect crypto setup", e);
        }
    }

}
