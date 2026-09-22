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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobReferenceSource;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadata;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.apache.james.blob.compaction.BlobReferenceMappingSource.BlobIdMessageIdMapping;
import org.apache.james.blob.compaction.ChunkFormat.BlobSlotContent;
import org.apache.james.blob.compaction.ChunkFormat.ChunkWriteResult;
import org.apache.james.blob.compaction.ChunkFormat.SlotRange;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Executes object compaction and garbage collection for chunked blob storage in Apache James.
 *
 * <h3>Crash Safety and Liveness Invariants</h3>
 * The compaction algorithms follow a strict step ordering to ensure crash safety and liveness:
 * <ul>
 *   <li><b>Initial Compaction:</b>
 *     <ol>
 *       <li>Read candidates and accumulate slots into an immutable chunk.</li>
 *       <li>Save the new chunk to raw storage (unreferenced yet by source-of-truth tables).</li>
 *       <li>Update source-of-truth table references (Cassandra) to the new chunk-slot references.</li>
 *       <li>Delete original standalone objects from raw storage.</li>
 *     </ol>
 *     If interrupted before step 3, the saved chunk is an orphan with 0 references and will be
 *     safely reclaimed by the next {@code gc-compact} run. The original blobs and references remain untouched.
 *     If interrupted after step 3 but before step 4, the old standalone blobs have 0 references and will be
 *     cleaned up by standard GC.
 *   </li>
 *   <li><b>GC-Compact (Rewrite / Merge / Purge):</b>
 *     <ol>
 *       <li>Read existing chunk objects and inspect slot references against the BloomFilter/mapping.</li>
 *       <li>For chunks with dead slots (or pairs of small chunks to merge), assemble a new chunk with only live slots.</li>
 *       <li>Save the new chunk to raw storage.</li>
 *       <li>Update source-of-truth table references to the new chunk slot references.</li>
 *       <li>Delete the old chunk(s) from raw storage.</li>
 *     </ol>
 *     If interrupted before step 4, the newly created chunk is an orphan with no references and will be
 *     purged on the subsequent GC-compact pass. If interrupted after step 4, the old chunk has 0 references
 *     and will be purged as an orphan chunk on the next GC-compact pass.
 *   </li>
 *   <li><b>Orphan Chunk Purging:</b>
 *     Chunks with 0 live references (100% dead slots) are identified as orphan chunks and deleted immediately.
 *     This guarantees self-healing and liveness by construction across process crashes.
 *   </li>
 * </ul>
 *
 * <h3>Memory Bounds and Operational Characteristics</h3>
 * <ul>
 *   <li><b>Candidate Payload Streaming:</b> {@link #initialCompact(CompactionRequest)} streams candidate blob identifiers
 *       and partitions them into windows of {@value #DEFAULT_CANDIDATE_BATCH_SIZE} blobs. Candidate payloads are fetched
 *       and packed chunk-by-chunk. Payloads are persisted and freed window-by-window, ensuring that candidate byte arrays
 *       are never held in heap for the entire generation simultaneously. Candidate payload heap usage is bounded by
 *       {@code O(min(candidateBatchSize * avgBlobSize, chunkTargetSize))}.</li>
 *   <li><b>Reference Mapping Memory Ceiling:</b> {@code loadReferenceMapping()} materializes all live blob-to-messageId
 *       mappings for the generation into an in-memory multimap. Memory consumption is {@code O(liveGenerationReferences)}
 *       at approximately ~200 bytes per reference (~200MB heap for 1 million live references; ~2GB heap for 10 million).
 *       Because {@link BlobReferenceMappingSource} currently exposes a full stream without partition-paged query capabilities,
 *       this table is loaded per compaction pass. High-scale deployments exceeding tens of millions of live references per
 *       generation can introduce partition-paged reference lookups in future iterations.</li>
 *   <li><b>GC Compaction Memory Bounds:</b> {@link #gcCompact(CompactionRequest)} discovers chunks by reading only trailing
 *       64KB footers via HTTP ranged reads (metadata-only). Orphan chunks (100% dead slots) are deleted with 0 payload bytes read.
 *       During chunk purge or merge, surviving live slots are streamed individually via HTTP ranged reads, strictly bounding
 *       GC payload heap usage to {@code O(maxSlotSize)} (~1MB).</li>
 * </ul>
 */
public class BlobCompactionAlgorithm {
    public static final int DEFAULT_CANDIDATE_BATCH_SIZE = 1000;
    private static final Logger LOGGER = LoggerFactory.getLogger(BlobCompactionAlgorithm.class);

    private final BlobStoreDAO blobStoreDAO;
    private final BlobStoreDAO rawStore;
    private final BlobReferenceSource referenceSource;
    private final BlobReferenceMappingSource mappingSource;
    private final BlobIdUpdater blobIdUpdater;

    public BlobCompactionAlgorithm(BlobStoreDAO blobStoreDAO,
                                  BlobStoreDAO rawStore,
                                  BlobReferenceSource referenceSource,
                                  BlobReferenceMappingSource mappingSource,
                                  BlobIdUpdater blobIdUpdater) {
        this.blobStoreDAO = Preconditions.checkNotNull(blobStoreDAO, "'blobStoreDAO' must not be null");
        this.rawStore = Preconditions.checkNotNull(rawStore, "'rawStore' must not be null");
        this.referenceSource = Preconditions.checkNotNull(referenceSource, "'referenceSource' must not be null");
        this.mappingSource = Preconditions.checkNotNull(mappingSource, "'mappingSource' must not be null");
        this.blobIdUpdater = Preconditions.checkNotNull(blobIdUpdater, "'blobIdUpdater' must not be null");
    }

    public BlobCompactionAlgorithm(BlobStoreDAO blobStoreDAO,
                                  BlobStoreDAO rawStore,
                                  BlobReferenceMappingSource mappingSource,
                                  BlobIdUpdater blobIdUpdater) {
        this(blobStoreDAO, rawStore,
            () -> Flux.from(mappingSource.listBlobIdMessageIdMappings()).map(BlobIdMessageIdMapping::blobId),
            mappingSource, blobIdUpdater);
    }

    public Mono<CompactionResult> compact(CompactionRequest request) {
        return initialCompact(request)
            .flatMap(initialResult -> gcCompact(request).map(initialResult::combine));
    }

    public Mono<CompactionResult> initialCompact(CompactionRequest request) {
        Preconditions.checkNotNull(request, "'request' must not be null");

        return loadReferenceMapping()
            .flatMap(mapping -> {
                if (mapping.isEmpty()) {
                    LOGGER.info("No blob references found in mapping source; skipping initial compaction for generation {}", request.generation());
                    return Mono.just(CompactionResult.NONE);
                }

                Publisher<BlobId> candidatesListing = request.family()
                    .map(family -> rawStore.listBlobs(request.bucketName(), family + "_" + request.generation() + "_"))
                    .orElseGet(() -> rawStore.listBlobs(request.bucketName()));

                Flux<CandidateBlob> packableCandidates = Flux.from(candidatesListing)
                    .filter(blobId -> matchesGenerationAndFamily(blobId.asString(), request.generation(), request.family()))
                    .filter(blobId -> !ChunkId.isChunkRef(blobId))
                    .filter(mapping::containsKey)
                    .flatMap(blobId -> Mono.from(rawStore.readBytes(request.bucketName(), blobId))
                        .map(bytesBlob -> new CandidateBlob(blobId, bytesBlob.payload(), bytesBlob.metadata()))
                        .onErrorResume(error -> {
                            LOGGER.warn("Failed reading candidate blob {}", blobId.asString(), error);
                            return Mono.empty();
                        }), 16)
                    .filter(candidate -> candidate.payload.length < request.configuration().maxPackableSize());

                return windowByCumulativeSize(packableCandidates, request.configuration().chunkTargetSize())
                    .concatMap(batch -> persistChunkBatch(request, batch, mapping))
                    .reduce(CompactionResult.NONE, CompactionResult::combine);
            });
    }

    static Flux<List<CandidateBlob>> windowByCumulativeSize(Flux<CandidateBlob> candidates, long targetSize) {
        return Flux.defer(() -> {
            AtomicLong currentSize = new AtomicLong(0L);
            return candidates.bufferUntil(candidate -> {
                long payloadSize = candidate.payload().length;
                if (currentSize.get() > 0 && currentSize.get() + payloadSize > targetSize) {
                    currentSize.set(payloadSize);
                    return true;
                }
                currentSize.addAndGet(payloadSize);
                return false;
            }, true);
        });
    }

    /**
     * Executes GC compaction (purge dead slots, merge small chunks, delete orphan chunks).
     * <p>
     * Memory bound is O(maxSlotSize) (~1MB) rather than O(chunkSize) (~100MB).
     * Chunk footers are read via suffix ranged reads (last 64KB) without buffering chunk payloads.
     * Orphan chunks are deleted without reading any payload bytes.
     * Chunks being purged or merged stream individual live slots via ranged reads.
     * </p>
     */
    public Mono<CompactionResult> gcCompact(CompactionRequest request) {
        Preconditions.checkNotNull(request, "'request' must not be null");

        Publisher<BlobId> chunkListing = request.family()
            .map(family -> rawStore.listBlobs(request.bucketName(), family + "_" + request.generation() + "_chunk"))
            .orElseGet(() -> rawStore.listBlobs(request.bucketName()));

        return loadReferenceMapping()
            .flatMap(mapping -> Flux.from(chunkListing)
                .filter(blobId -> ChunkId.isChunkRef(blobId) && blobId.asString().indexOf('~') == -1)
                .filter(blobId -> matchesGenerationAndFamily(blobId.asString(), request.generation(), request.family()))
                .flatMap(chunkBlobId -> Mono.from(rawStore.readRange(request.bucketName(), chunkBlobId, -65536, -1))
                    .map(tailBlob -> {
                        try {
                            byte[] tailData = tailBlob.asBytes().payload();
                            long totalSize = BlobStoreDAO.totalObjectSize(tailBlob);
                            ChunkFooter footer = ChunkFormat.readFooter(tailData, totalSize);
                            return new ExistingChunk(chunkBlobId, totalSize, footer);
                        } catch (IOException e) {
                            LOGGER.warn("Failed reading footer for chunk object {}", chunkBlobId.asString(), e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .onErrorResume(error -> {
                        LOGGER.warn("Failed reading chunk footer {}", chunkBlobId.asString(), error);
                        return Mono.empty();
                    }))
                .collectList()
                .flatMap(existingChunks -> processExistingChunks(request, existingChunks, mapping)));
    }

    private Mono<CompactionResult> packAndPersistChunks(CompactionRequest request,
                                                       List<CandidateBlob> candidates,
                                                       Map<BlobId, Set<String>> mapping) {
        if (candidates.isEmpty()) {
            return Mono.just(CompactionResult.NONE);
        }
        return persistChunkBatch(request, candidates, mapping);
    }

    private Mono<CompactionResult> persistChunkBatch(CompactionRequest request,
                                                    List<CandidateBlob> batch,
                                                    Map<BlobId, Set<String>> mapping) {
        int family = request.family().orElseGet(() -> extractFamily(batch.get(0).blobId.asString()));
        ChunkId chunkId = ChunkId.ofChunk(family, request.generation());

        List<BlobSlotContent> slots = batch.stream()
            .map(candidate -> BlobSlotContent.of(candidate.payload, candidate.metadata))
            .toList();

        ChunkWriteResult writeResult;
        try {
            writeResult = ChunkFormat.writeChunk(slots);
        } catch (IOException e) {
            return Mono.error(new ObjectStoreIOException("Failed to write chunk " + chunkId.chunkId(), e));
        }

        // 1. Save chunk to raw storage
        return Mono.from(rawStore.save(request.bucketName(), chunkId.chunkBlobId(), BlobStoreDAO.BytesBlob.of(writeResult.chunkBytes())))
            // 2. Update source-of-truth table references
            .then(Flux.range(0, batch.size())
                .concatMap(i -> {
                    CandidateBlob candidate = batch.get(i);
                    SlotRange range = writeResult.slotRanges().get(i);
                    ChunkId slotRef = ChunkId.slotRef(chunkId, range.offset(), range.limit());
                    Collection<String> messageIds = mapping.getOrDefault(candidate.blobId, Set.of());
                    return blobIdUpdater.replaceReferences(candidate.blobId, slotRef, messageIds)
                        .thenReturn(Optional.of(candidate))
                        .onErrorResume(e -> {
                            LOGGER.error("Failed to update references for candidate blob {}, skipping deletion of original blob", candidate.blobId.asString(), e);
                            return Mono.just(Optional.empty());
                        });
                })
                .flatMap(opt -> opt.map(Flux::just).orElseGet(Flux::empty))
                .collectList()
                .flatMap(successfulCandidates -> Flux.fromIterable(successfulCandidates)
                    // 3. Delete original standalone blobs ONLY for successfully updated candidates
                    .concatMap(candidate -> Mono.from(rawStore.delete(request.bucketName(), candidate.blobId)))
                    .then()
                    .thenReturn(CompactionResult.builder()
                        .packedBlobs(successfulCandidates.size())
                        .packedBytes(successfulCandidates.stream().mapToLong(c -> c.payload.length).sum())
                        .chunksWritten(1)
                        .freedBytes(successfulCandidates.stream().mapToLong(c -> c.payload.length).sum())
                        .build())));
    }

    private Mono<CompactionResult> processExistingChunks(CompactionRequest request,
                                                        List<ExistingChunk> chunks,
                                                        Map<BlobId, Set<String>> mapping) {
        if (chunks.isEmpty()) {
            return Mono.just(CompactionResult.NONE);
        }

        List<ChunkAnalysis> analyses = new ArrayList<>();
        for (ExistingChunk chunk : chunks) {
            analyses.add(analyzeChunk(chunk, mapping));
        }

        // 1. Delete orphan chunks (100% dead slots)
        List<ChunkAnalysis> orphanChunks = analyses.stream()
            .filter(a -> a.liveSlots.isEmpty())
            .toList();

        Mono<CompactionResult> deleteOrphansMono = Flux.fromIterable(orphanChunks)
            .concatMap(orphan -> Mono.from(rawStore.delete(request.bucketName(), orphan.chunk.chunkBlobId))
                .thenReturn(CompactionResult.builder()
                    .deadPurged(orphan.totalSlotsCount)
                    .freedBytes(orphan.chunk.totalChunkSize)
                    .build()))
            .reduce(CompactionResult.NONE, CompactionResult::combine);

        List<ChunkAnalysis> remaining = analyses.stream()
            .filter(a -> !a.liveSlots.isEmpty())
            .toList();

        // 2. Purge dead slots for chunks with dead ratio >= threshold
        double purgeThreshold = Math.max(request.configuration().purgeDeadRatio(), request.configuration().gainThreshold());
        List<ChunkAnalysis> chunksToPurge = remaining.stream()
            .filter(a -> a.deadRatio() >= purgeThreshold)
            .toList();

        Mono<CompactionResult> purgeMono = Flux.fromIterable(chunksToPurge)
            .concatMap(analysis -> purgeDeadSlots(request, analysis, mapping))
            .reduce(CompactionResult.NONE, CompactionResult::combine);

        // 3. Merge candidates: chunks < 50% target size
        long mergeThresholdSize = (long) (request.configuration().mergeDeadRatio() * request.configuration().chunkTargetSize());
        List<ChunkAnalysis> nonPurged = remaining.stream()
            .filter(a -> a.deadRatio() < purgeThreshold)
            .toList();

        List<ChunkAnalysis> smallChunks = nonPurged.stream()
            .filter(a -> a.chunk.totalChunkSize < mergeThresholdSize)
            .toList();

        Mono<CompactionResult> mergeMono = mergeSmallChunks(request, smallChunks, mapping);

        return deleteOrphansMono
            .flatMap(res1 -> purgeMono.map(res1::combine))
            .flatMap(res2 -> mergeMono.map(res2::combine));
    }

    private Mono<CompactionResult> purgeDeadSlots(CompactionRequest request,
                                                  ChunkAnalysis analysis,
                                                  Map<BlobId, Set<String>> mapping) {
        int family = extractFamily(analysis.chunk.chunkBlobId.asString());
        ChunkId newChunkId = ChunkId.ofChunk(family, request.generation());

        return Flux.fromIterable(analysis.liveSlots)
            .concatMap(liveSlot -> Mono.from(rawStore.readRange(request.bucketName(), analysis.chunk.chunkBlobId, liveSlot.offset, liveSlot.offset + liveSlot.limit - 1))
                .publishOn(Schedulers.parallel())
                .flatMap(sliceBlob -> {
                    try {
                        byte[] sliceData = sliceBlob.asBytes().payload();
                        BlobSlot slot = ChunkFormat.parseSlot(sliceData, liveSlot.offset);
                        byte[] decompressed = slot.originalSize() == 0 ? new byte[0] : com.github.luben.zstd.Zstd.decompress(slot.compressedContent(), (int) slot.originalSize());
                        return Mono.just(new LiveSlotWithContent(liveSlot, decompressed, slot.metadata()));
                    } catch (IOException e) {
                        return Mono.error(new ObjectStoreIOException("Failed parsing slot", e));
                    }
                }))
            .collectList()
            .flatMap(liveSlotsWithContent -> {
                List<BlobSlotContent> liveSlotContents = liveSlotsWithContent.stream()
                    .map(slot -> BlobSlotContent.of(slot.decompressedContent, slot.metadata))
                    .toList();

                ChunkWriteResult writeResult;
                try {
                    writeResult = ChunkFormat.writeChunk(liveSlotContents);
                } catch (IOException e) {
                    return Mono.error(new ObjectStoreIOException("Failed writing purged chunk " + newChunkId.chunkId(), e));
                }

                // 1. Save new chunk to raw storage
                return Mono.from(rawStore.save(request.bucketName(), newChunkId.chunkBlobId(), BlobStoreDAO.BytesBlob.of(writeResult.chunkBytes())))
                    // 2. Update references to the new slot refs
                    .then(Flux.range(0, liveSlotsWithContent.size())
                        .concatMap(i -> {
                            LiveSlotWithContent liveSlot = liveSlotsWithContent.get(i);
                            SlotRange range = writeResult.slotRanges().get(i);
                            ChunkId newSlotRef = ChunkId.slotRef(newChunkId, range.offset(), range.limit());
                            return blobIdUpdater.replaceReferences(liveSlot.meta.slotRef, newSlotRef, liveSlot.meta.messageIds);
                        })
                        .then())
                    // 3. Delete old chunk from raw storage
                    .then(Mono.from(rawStore.delete(request.bucketName(), analysis.chunk.chunkBlobId)))
                    .thenReturn(CompactionResult.builder()
                        .deadPurged(analysis.deadSlotsCount)
                        .freedBytes(Math.max(0, analysis.chunk.totalChunkSize - writeResult.chunkBytes().length))
                        .build());
            });
    }

    private Mono<CompactionResult> mergeSmallChunks(CompactionRequest request,
                                                   List<ChunkAnalysis> smallChunks,
                                                   Map<BlobId, Set<String>> mapping) {
        if (smallChunks.size() < 2) {
            return Mono.just(CompactionResult.NONE);
        }

        List<ChunkPair> pairs = new ArrayList<>();
        int i = 0;
        while (i + 1 < smallChunks.size()) {
            ChunkAnalysis c1 = smallChunks.get(i);
            ChunkAnalysis c2 = smallChunks.get(i + 1);
            if (c1.chunk.totalChunkSize + c2.chunk.totalChunkSize <= request.configuration().chunkTargetSize()) {
                pairs.add(new ChunkPair(c1, c2));
                i += 2;
            } else {
                i++;
            }
        }

        return Flux.fromIterable(pairs)
            .concatMap(pair -> mergePair(request, pair))
            .reduce(CompactionResult.NONE, CompactionResult::combine);
    }

    private Mono<CompactionResult> mergePair(CompactionRequest request, ChunkPair pair) {
        int family = extractFamily(pair.c1.chunk.chunkBlobId.asString());
        ChunkId newChunkId = ChunkId.ofChunk(family, request.generation());

        Mono<List<LiveSlotWithContent>> c1Live = Flux.fromIterable(pair.c1.liveSlots)
            .concatMap(slot -> Mono.from(rawStore.readRange(request.bucketName(), pair.c1.chunk.chunkBlobId, slot.offset, slot.offset + slot.limit - 1))
                .publishOn(Schedulers.parallel())
                .flatMap(sliceBlob -> {
                    try {
                        byte[] sliceData = sliceBlob.asBytes().payload();
                        BlobSlot parsed = ChunkFormat.parseSlot(sliceData, slot.offset);
                        byte[] decompressed = parsed.originalSize() == 0 ? new byte[0] : com.github.luben.zstd.Zstd.decompress(parsed.compressedContent(), (int) parsed.originalSize());
                        return Mono.just(new LiveSlotWithContent(slot, decompressed, parsed.metadata()));
                    } catch (IOException e) {
                        return Mono.error(new ObjectStoreIOException("Failed parsing slot", e));
                    }
                }))
            .collectList();

        Mono<List<LiveSlotWithContent>> c2Live = Flux.fromIterable(pair.c2.liveSlots)
            .concatMap(slot -> Mono.from(rawStore.readRange(request.bucketName(), pair.c2.chunk.chunkBlobId, slot.offset, slot.offset + slot.limit - 1))
                .publishOn(Schedulers.parallel())
                .flatMap(sliceBlob -> {
                    try {
                        byte[] sliceData = sliceBlob.asBytes().payload();
                        BlobSlot parsed = ChunkFormat.parseSlot(sliceData, slot.offset);
                        byte[] decompressed = parsed.originalSize() == 0 ? new byte[0] : com.github.luben.zstd.Zstd.decompress(parsed.compressedContent(), (int) parsed.originalSize());
                        return Mono.just(new LiveSlotWithContent(slot, decompressed, parsed.metadata()));
                    } catch (IOException e) {
                        return Mono.error(new ObjectStoreIOException("Failed parsing slot", e));
                    }
                }))
            .collectList();

        return Mono.zip(c1Live, c2Live)
            .flatMap(tuple -> {
                List<LiveSlotWithContent> allLive = new ArrayList<>(tuple.getT1());
                allLive.addAll(tuple.getT2());

                List<BlobSlotContent> slotContents = allLive.stream()
                    .map(slot -> BlobSlotContent.of(slot.decompressedContent, slot.metadata))
                    .toList();

                ChunkWriteResult writeResult;
                try {
                    writeResult = ChunkFormat.writeChunk(slotContents);
                } catch (IOException e) {
                    return Mono.error(new ObjectStoreIOException("Failed to write merged chunk " + newChunkId.chunkId(), e));
                }

                // 1. Save merged chunk
                return Mono.from(rawStore.save(request.bucketName(), newChunkId.chunkBlobId(), BlobStoreDAO.BytesBlob.of(writeResult.chunkBytes())))
                    // 2. Update table references for all slots in both chunks
                    .then(Flux.range(0, allLive.size())
                        .concatMap(j -> {
                            LiveSlotWithContent liveSlot = allLive.get(j);
                            SlotRange range = writeResult.slotRanges().get(j);
                            ChunkId newSlotRef = ChunkId.slotRef(newChunkId, range.offset(), range.limit());
                            return blobIdUpdater.replaceReferences(liveSlot.meta.slotRef, newSlotRef, liveSlot.meta.messageIds);
                        })
                        .then())
                    // 3. Delete both old chunks
                    .then(Mono.from(rawStore.delete(request.bucketName(), pair.c1.chunk.chunkBlobId)))
                    .then(Mono.from(rawStore.delete(request.bucketName(), pair.c2.chunk.chunkBlobId)))
                    .thenReturn(CompactionResult.builder()
                        .mergedChunks(2)
                        .freedBytes(Math.max(0, (pair.c1.chunk.totalChunkSize + pair.c2.chunk.totalChunkSize) - writeResult.chunkBytes().length))
                        .build());
            });
    }

    private ChunkAnalysis analyzeChunk(ExistingChunk chunk, Map<BlobId, Set<String>> mapping) {
        ChunkId chunkId = ChunkId.parseChunk(chunk.chunkBlobId.asString());
        ChunkFooter footer = chunk.footer;
        List<Long> starts = footer.slotStarts();

        List<LiveSlotMeta> liveSlots = new ArrayList<>();
        int deadCount = 0;
        long deadBytes = 0;
        long totalSlotBytes = 0;

        for (int i = 0; i < starts.size(); i++) {
            long offset = starts.get(i);
            long limit = footer.slotLength(i);
            totalSlotBytes += limit;

            ChunkId slotRef = ChunkId.slotRef(chunkId, offset, limit);
            Set<String> messageIds = findMessageIdsForSlot(mapping, slotRef);

            if (messageIds.isEmpty()) {
                deadCount++;
                deadBytes += limit;
            } else {
                liveSlots.add(new LiveSlotMeta(offset, limit, slotRef, messageIds));
            }
        }

        return new ChunkAnalysis(chunk, liveSlots, starts.size(), deadCount, deadBytes, totalSlotBytes);
    }

    private Set<String> findMessageIdsForSlot(Map<BlobId, Set<String>> mapping, ChunkId slotRef) {
        Set<String> direct = mapping.get(slotRef);
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        ChunkId zeroLimit = ChunkId.slotRef(slotRef.chunkBlobId().asString(), slotRef.offset(), 0L);
        Set<String> zero = mapping.get(zeroLimit);
        if (zero != null && !zero.isEmpty()) {
            return zero;
        }
        for (Map.Entry<BlobId, Set<String>> entry : mapping.entrySet()) {
            String keyStr = entry.getKey().asString();
            if (ChunkId.isChunkRef(keyStr)) {
                try {
                    ChunkId parsed = ChunkId.parse(keyStr);
                    if (parsed.chunkId().equals(slotRef.chunkId()) && parsed.offset() == slotRef.offset()) {
                        return entry.getValue();
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        return Set.of();
    }

    /**
     * Loads generation live references from {@link BlobReferenceMappingSource}.
     * <p>
     * Operational ceiling: Materializes the generation's reference multimap into memory.
     * Memory consumption is O(liveReferences) * ~200 bytes/reference.
     * High-scale deployments with tens of millions of references can introduce partition-paged
     * reference lookups in future iterations.
     * </p>
     */
    private Mono<Map<BlobId, Set<String>>> loadReferenceMapping() {
        return Flux.from(mappingSource.listBlobIdMessageIdMappings())
            .collectMultimap(BlobIdMessageIdMapping::blobId, BlobIdMessageIdMapping::messageId)
            .map(multimap -> {
                Map<BlobId, Set<String>> result = new HashMap<>();
                multimap.forEach((k, v) -> result.put(k, new HashSet<>(v)));
                return result;
            });
    }

    static boolean matchesGenerationAndFamily(String blobIdStr, long targetGeneration, Optional<Integer> targetFamily) {
        char sep = '_';
        int firstSep = blobIdStr.indexOf(sep);
        if (firstSep == -1) {
            sep = '/';
            firstSep = blobIdStr.indexOf(sep);
        }
        if (firstSep == -1) {
            return false;
        }
        int secondSep = blobIdStr.indexOf(sep, firstSep + 1);
        if (secondSep == -1) {
            return false;
        }
        try {
            int family = Integer.parseInt(blobIdStr.substring(0, firstSep));
            long generation = Long.parseLong(blobIdStr.substring(firstSep + 1, secondSep));
            if (generation != targetGeneration) {
                return false;
            }
            return targetFamily.map(f -> f == family).orElse(true);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static int extractFamily(String blobIdStr) {
        int firstSep = blobIdStr.indexOf('_');
        if (firstSep == -1) {
            firstSep = blobIdStr.indexOf('/');
        }
        if (firstSep > 0) {
            try {
                return Integer.parseInt(blobIdStr.substring(0, firstSep));
            } catch (NumberFormatException e) {
                // fallback
            }
        }
        return 1;
    }

    record CandidateBlob(BlobId blobId, byte[] payload, BlobMetadata metadata) {}

    private record ExistingChunk(BlobId chunkBlobId, long totalChunkSize, ChunkFooter footer) {}

    private record LiveSlotMeta(long offset, long limit, ChunkId slotRef, Set<String> messageIds) {}

    private record LiveSlotWithContent(LiveSlotMeta meta, byte[] decompressedContent, BlobMetadata metadata) {}

    private record ChunkPair(ChunkAnalysis c1, ChunkAnalysis c2) {}

    private record ChunkAnalysis(ExistingChunk chunk,
                                List<LiveSlotMeta> liveSlots,
                                int totalSlotsCount,
                                int deadSlotsCount,
                                long deadBytes,
                                long totalSlotBytes) {
        double deadRatio() {
            if (totalSlotBytes == 0) {
                return 1.0;
            }
            return (double) deadBytes / totalSlotBytes;
        }
    }
}
