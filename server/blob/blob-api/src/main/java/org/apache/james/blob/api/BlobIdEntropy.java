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

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

/**
 * How many bits of entropy a blob id carries, as set by the {@code james.blobid.entropy} system property.
 *
 * <p>Defaults to {@value #DEFAULT_ENTROPY_BITS} bits, the full SHA-256 output, so that ids of existing
 * deployments are left untouched. {@code 128} is the sensible alternative: the birthday bound puts a
 * collision at {@code n^2/2^129}, ie. 1.5e-19 for ten billion blobs, and truncating a cryptographic hash
 * to its leading bits is standard practice (NIST SP 800-107, FIPS 180-4).</p>
 */
public class BlobIdEntropy {
    public static final String ENTROPY_BITS_PROPERTY = "james.blobid.entropy";
    public static final int DEFAULT_ENTROPY_BITS = 256;
    private static final int MIN_ENTROPY_BITS = 128;
    private static final int BITS_PER_BYTE = 8;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int ENTROPY_BITS = parse(System.getProperty(ENTROPY_BITS_PROPERTY));

    @VisibleForTesting
    static int parse(String value) {
        return Optional.ofNullable(value)
            .map(String::trim)
            .filter(trimmed -> !trimmed.isEmpty())
            .map(BlobIdEntropy::parseBits)
            .orElse(DEFAULT_ENTROPY_BITS);
    }

    private static int parseBits(String value) {
        try {
            int bits = Integer.parseInt(value);
            Preconditions.checkArgument(bits % BITS_PER_BYTE == 0,
                "'%s' must be a multiple of %s, got %s", ENTROPY_BITS_PROPERTY, BITS_PER_BYTE, bits);
            Preconditions.checkArgument(bits >= MIN_ENTROPY_BITS && bits <= DEFAULT_ENTROPY_BITS,
                "'%s' must be within [%s, %s], got %s", ENTROPY_BITS_PROPERTY, MIN_ENTROPY_BITS, DEFAULT_ENTROPY_BITS, bits);
            return bits;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid '" + ENTROPY_BITS_PROPERTY + "' value: '" + value + "'. Expected a bit count, eg. 128 or 256", e);
        }
    }

    public static int entropyBits() {
        return ENTROPY_BITS;
    }

    public static int entropyBytes() {
        return ENTROPY_BITS / BITS_PER_BYTE;
    }

    public static byte[] randomBytes() {
        byte[] bytes = new byte[entropyBytes()];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static byte[] truncate(byte[] hash) {
        if (hash.length <= entropyBytes()) {
            return hash;
        }
        return Arrays.copyOf(hash, entropyBytes());
    }
}
