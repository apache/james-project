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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.james.blob.api.BlobStoreDAO.BytesBlob;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.DictionaryFile;
import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Whole chunks: footer integrity, writer lifecycle and agreement with {@link ReferenceChunkDecoder}. Normal builds
 * replay the committed inputs; fuzz one target with e.g.
 * {@code JAZZER_FUZZ=1 mvn -pl server/blob/blob-compaction test -Dtest=ChunkFuzzTest#parsedFootersShouldBeConsistent}.
 */
class ChunkFuzzTest {
    /** Whatever the bytes, a parsed footer lists contiguous slots between the header and the footer. */
    @FuzzTest(maxDuration = "600s")
    @DictionaryFile(resourcePath = "chunk.dict")
    void parsedFootersShouldBeConsistent(FuzzedDataProvider data) {
        byte[] chunk = data.consumeRemainingAsBytes();
        ChunkFooter footer;
        try {
            footer = ChunkFormatTest.readFooter(chunk);
        } catch (ChunkFormatException e) {
            return;
        }
        ChunkTrailer trailer = ChunkParser.parseTrailer(Arrays.copyOfRange(chunk, chunk.length - ChunkFormat.TRAILER_SIZE, chunk.length), chunk.length);

        long expectedOffset = ChunkFormat.HEADER_SIZE;
        for (SlotLocation slot : footer.slots()) {
            assertThat(slot.offset()).isEqualTo(expectedOffset);
            expectedOffset = slot.end();
        }
        assertThat(expectedOffset).isEqualTo(trailer.footerOffset());
        assertThat(trailer.footerOffset() + trailer.footerLength() + ChunkFormat.TRAILER_SIZE).isEqualTo(chunk.length);
    }

    /**
     * A damaged chunk either fails or keeps its original footer: never a different valid one. Damage covers byte
     * changes, truncation, extension and the footer of another chunk. A location inside the chunk either fails or
     * denotes a canonical slot: payloads may embed valid slots, which is why slot addresses are trusted.
     */
    @FuzzTest(maxDuration = "600s")
    @DictionaryFile(resourcePath = "chunk.dict")
    void damagedChunksShouldFailOrKeepTheirFooter(FuzzedDataProvider data) {
        FuzzedBlobs.WrittenChunk chunk = FuzzedBlobs.chunk(data);
        checkArbitraryLocation(chunk.bytes(), data);
        byte[] damaged = damage(chunk.bytes(), data);

        try {
            assertThat(ChunkFormatTest.readFooter(damaged).slots()).isEqualTo(chunk.slots());
        } catch (ChunkFormatException e) {
            // Expected for most damages
        }
    }

    /** A rejected slot leaves the writer unchanged; once finished, every call is refused. */
    @FuzzTest(maxDuration = "600s")
    @DictionaryFile(resourcePath = "chunk.dict")
    void writerShouldFollowItsLifecycle(FuzzedDataProvider data) {
        ChunkWriter writer = new ChunkWriter();
        List<BytesBlob> accepted = new ArrayList<>();
        Optional<byte[]> chunk = Optional.empty();
        int operationCount = data.consumeInt(1, 12);
        for (int i = 0; i < operationCount; i++) {
            boolean add = data.consumeBoolean();
            if (chunk.isPresent()) {
                BytesBlob blob = FuzzedBlobs.blob(data);
                assertThatThrownBy(() -> {
                    if (add) {
                        writer.addSlot(blob);
                    } else {
                        writer.finish();
                    }
                }).isInstanceOf(IllegalStateException.class);
            } else if (add) {
                BytesBlob blob = FuzzedBlobs.blob(data);
                if (FuzzedBlobs.encodable(blob)) {
                    writer.addSlot(blob);
                    accepted.add(FuzzedBlobs.normalized(blob));
                } else {
                    assertThatThrownBy(() -> writer.addSlot(blob)).isInstanceOf(IllegalArgumentException.class);
                }
            } else {
                chunk = Optional.of(writer.finish());
            }
        }
        byte[] bytes = chunk.orElseGet(writer::finish);

        assertThat(decode(bytes)).isEqualTo(accepted);
    }

    /** Both decoders accept the same chunks and read the same blobs. */
    @FuzzTest(maxDuration = "600s")
    @DictionaryFile(resourcePath = "chunk.dict")
    void parserShouldAgreeWithTheReferenceDecoder(FuzzedDataProvider data) {
        byte[] chunk = data.consumeBoolean() ? damage(FuzzedBlobs.chunk(data).bytes(), data) : data.consumeRemainingAsBytes();

        Optional<List<BytesBlob>> parsed = parse(chunk);
        Optional<List<BytesBlob>> reference;
        try {
            reference = Optional.of(ReferenceChunkDecoder.decode(chunk));
        } catch (Exception e) {
            reference = Optional.empty();
        }

        assertThat(parsed).isEqualTo(reference);
    }

    private static void checkArbitraryLocation(byte[] chunk, FuzzedDataProvider data) {
        long offset = data.consumeLong(ChunkFormat.HEADER_SIZE, chunk.length);
        long length = data.consumeLong(ChunkFormat.MIN_SLOT_SIZE, Math.max(ChunkFormat.MIN_SLOT_SIZE, chunk.length - offset));
        SlotLocation location = new SlotLocation(offset, length);
        try {
            byte[] slot = ChunkParser.sliceSlot(chunk, location);
            assertThat(ChunkFormatTest.encodeSlot(ChunkParser.parseSlot(slot, location))).isEqualTo(slot);
        } catch (ChunkFormatException e) {
            // Expected unless the location matches a slot boundary
        }
    }

    private static byte[] damage(byte[] chunk, FuzzedDataProvider data) {
        return switch (data.consumeInt(0, 3)) {
            case 0 -> {
                byte[] damaged = chunk.clone();
                int changes = data.consumeInt(1, 4);
                for (int i = 0; i < changes; i++) {
                    damaged[data.consumeInt(0, chunk.length - 1)] ^= data.consumeByte();
                }
                yield damaged;
            }
            case 1 -> Arrays.copyOf(chunk, data.consumeInt(0, chunk.length - 1));
            case 2 -> {
                byte[] extra = data.consumeBytes(16);
                byte[] extended = Arrays.copyOf(chunk, chunk.length + extra.length);
                System.arraycopy(extra, 0, extended, chunk.length, extra.length);
                yield extended;
            }
            default -> footerOfAnotherChunk(chunk, FuzzedBlobs.chunk(data).bytes());
        };
    }

    /** The header and slots of {@code chunk} followed by the footer and trailer of {@code other}. */
    private static byte[] footerOfAnotherChunk(byte[] chunk, byte[] other) {
        int footerOffset = footerOffset(chunk);
        int otherFooterOffset = footerOffset(other);
        byte[] spliced = Arrays.copyOf(chunk, footerOffset + other.length - otherFooterOffset);
        System.arraycopy(other, otherFooterOffset, spliced, footerOffset, other.length - otherFooterOffset);
        return spliced;
    }

    private static int footerOffset(byte[] chunk) {
        return Math.toIntExact(ChunkParser.parseTrailer(
            Arrays.copyOfRange(chunk, chunk.length - ChunkFormat.TRAILER_SIZE, chunk.length), chunk.length).footerOffset());
    }

    private static List<BytesBlob> decode(byte[] chunk) {
        return ChunkFormatTest.readFooter(chunk).slots().stream()
            .map(location -> ChunkParser.parseSlot(ChunkParser.sliceSlot(chunk, location), location))
            .toList();
    }

    private static Optional<List<BytesBlob>> parse(byte[] chunk) {
        try {
            return Optional.of(decode(chunk));
        } catch (ChunkFormatException e) {
            return Optional.empty();
        }
    }
}
