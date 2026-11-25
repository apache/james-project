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

import java.util.zip.CRC32C;

/**
 * Version 1 of the chunk layout. All numbers are big-endian.
 *
 * <pre>
 * header  : magic "JCHK" | version
 * slot*   : metadataLength (int) | metadata | payload | crc32c of the preceding slot bytes (int)
 *           metadata = entryCount (unsigned short) then entryCount x [nameLength (unsigned short) | name (ASCII)
 *                      | valueLength (unsigned short) | value (UTF-8)]
 * footer  : slotCount (int) | slotCount x [offset (long) | length (long)]
 * trailer : footerOffset (long) | footerLength (int) | crc32c of the footer (int) | magic "JCHK" | version
 * </pre>
 *
 * <p>A slot address carries the slot offset and length, so reading a slot is a single ranged read that needs neither
 * the header nor the footer. The footer only serves chunk maintenance.</p>
 */
final class ChunkFormat {
    static final byte[] MAGIC = {'J', 'C', 'H', 'K'};
    static final byte VERSION = 1;
    static final int HEADER_SIZE = MAGIC.length + Byte.BYTES;
    static final int METADATA_LENGTH_SIZE = Integer.BYTES;
    static final int METADATA_COUNT_SIZE = Short.BYTES;
    static final int CRC_SIZE = Integer.BYTES;
    static final int MIN_SLOT_SIZE = METADATA_LENGTH_SIZE + METADATA_COUNT_SIZE + CRC_SIZE;
    static final int FOOTER_COUNT_SIZE = Integer.BYTES;
    static final int FOOTER_ENTRY_SIZE = 2 * Long.BYTES;
    static final int TRAILER_SIZE = Long.BYTES + Integer.BYTES + CRC_SIZE + HEADER_SIZE;
    static final int MAX_FIELD_LENGTH = 0xFFFF;
    // Chunks are handled as byte arrays.
    static final long MAX_CHUNK_SIZE = Integer.MAX_VALUE - 8;

    private ChunkFormat() {
    }

    static int crc32c(byte[] bytes, int offset, int length) {
        CRC32C crc = new CRC32C();
        crc.update(bytes, offset, length);
        return (int) crc.getValue();
    }
}
