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

package org.apache.james.blob.api;

import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.BaseEncoding;

/**
 * How the payload of a blob id is spelled out, as set by the {@code james.blob.id.hash.encoding} system
 * property.
 *
 * <p>Truncated ids are left unpadded: they exist to be short, and padding them back up would give away
 * part of what {@link BlobIdEntropy} saved. Ids at full entropy keep the padding of their encoding, so
 * that a deployment pinned there keeps spelling ids the way releases up to 3.9.x did.</p>
 */
public class BlobIdEncoding {
    public static final String ENCODING_PROPERTY = "james.blob.id.hash.encoding";
    private static final BaseEncoding DEFAULT_ENCODING = BaseEncoding.base64Url();

    public static BlobIdEncoding fromSystemProperties() {
        return new BlobIdEncoding(Optional.ofNullable(System.getProperty(ENCODING_PROPERTY))
            .map(BlobIdEncoding::baseEncodingFrom)
            .orElse(DEFAULT_ENCODING));
    }

    @VisibleForTesting
    static BaseEncoding baseEncodingFrom(String encodingType) {
        return switch (encodingType) {
            case "base16", "hex" -> BaseEncoding.base16();
            case "base32" -> BaseEncoding.base32();
            case "base32Hex" -> BaseEncoding.base32Hex();
            case "base64" -> BaseEncoding.base64();
            case "base64Url" -> BaseEncoding.base64Url();
            default -> throw new IllegalArgumentException("Unknown encoding type: " + encodingType);
        };
    }

    private final BaseEncoding encoding;

    @VisibleForTesting
    BlobIdEncoding(BaseEncoding encoding) {
        if (BlobIdEntropy.entropyBits() == BlobIdEntropy.MAX_ENTROPY_BITS) {
            this.encoding = encoding;
        } else {
            this.encoding = encoding.omitPadding();
        }
    }

    public String encode(byte[] payload) {
        return encoding.encode(payload);
    }
}
