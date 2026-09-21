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

package org.apache.james.blob.compaction;

import java.util.Arrays;
import java.util.Objects;
import java.util.zip.CRC32C;

import org.apache.james.blob.api.ObjectStoreIOException;

import com.github.luben.zstd.Zstd;

public record BlobSlot(long contentStart, int crc32c, long originalSize, byte[] compressedContent) {
    public byte[] decompress() throws ObjectStoreIOException {
        if (originalSize < 0 || originalSize > Integer.MAX_VALUE) {
            throw new ObjectStoreIOException("Invalid original size in chunk slot: " + originalSize);
        }
        byte[] decompressed;
        if (originalSize == 0) {
            decompressed = new byte[0];
        } else {
            try {
                decompressed = Zstd.decompress(compressedContent, (int) originalSize);
            } catch (Exception e) {
                throw new ObjectStoreIOException("Failed to decompress slot content at " + contentStart, e);
            }
        }

        if (decompressed.length != originalSize) {
            throw new ObjectStoreIOException("Decompressed size " + decompressed.length + " does not match expected " + originalSize);
        }

        CRC32C crc = new CRC32C();
        crc.update(decompressed);
        int computedCrc = (int) crc.getValue();
        if (computedCrc != crc32c) {
            throw new ObjectStoreIOException(String.format("CRC mismatch for chunk slot at offset %d: expected %d, got %d",
                contentStart, crc32c, computedCrc));
        }

        return decompressed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof BlobSlot other) {
            return contentStart == other.contentStart
                && crc32c == other.crc32c
                && originalSize == other.originalSize
                && Arrays.equals(compressedContent, other.compressedContent);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(contentStart, crc32c, originalSize);
        result = 31 * result + Arrays.hashCode(compressedContent);
        return result;
    }
}
