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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobIdUpdater;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadata;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectStoreIOException;
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
 */
public class BlobCompactionAlgorithm {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlobCompactionAlgorithm.class);

    private final BlobStoreDAO blobStoreDAO;
    private final BlobStoreDAO rawStore;
    private final BlobIdUpdater.Factory updaterFactory;

    public BlobCompactionAlgorithm(BlobStoreDAO blobStoreDAO,
                                  BlobStoreDAO rawStore,
                                  BlobIdUpdater.Factory updaterFactory) {
        this.blobStoreDAO = Preconditions.checkNotNull(blobStoreDAO, "'blobStoreDAO' must not be null");
        this.rawStore = Preconditions.checkNotNull(rawStore, "'rawStore' must not be null");
        this.updaterFactory = Preconditions.checkNotNull(updaterFactory, "'updaterFactory' must not be null");
    }

    public Mono<CompactionResult> initialCompact(CompactionRequest request) {
        Preconditions.checkNotNull(request, "'request' must not be null");

        Set<BlobId> referencedBlobs = ConcurrentHashMap.newKeySet();
        Predicate<BlobId> generationCondition = blobId -> matchesGenerationAndFamily(blobId.asString(), request.generation(), request.family());

        return updaterFactory.forPredicate(generationCondition, referencedBlobs::add)
            .flatMap(blobIdUpdater -> {
                Publisher<BlobId> candidatesListing = request.family()
                    .map(family -> Flux.concat(
                        rawStore.listBlobs(request.bucketName(), family + "/" + request.generation() + "/"),
                        rawStore.listBlobs(request.bucketName(), family + "_" + request.generation() + "_")))
                    .orElseGet(() -> Flux.from(rawStore.listBlobs(request.bucketName())));

                return Flux.from(candidatesListing)
                    .filter(blobId -> matchesGenerationAndFamily(blobId.asString(), request.generation(), request.family()))
                    .filter(blobId -> !ChunkId.isChunkRef(blobId))
                    .filter(referencedBlobs::contains)
                    .flatMap(blobId -> Mono.from(rawStore.readBytes(request.bucketName(), blobId))
                        .map(bytesBlob -> new CandidateBlob(blobId, bytesBlob.payload(), bytesBlob.metadata()))
                        .onErrorResume(error -> {
                            LOGGER.warn("Failed reading candidate blob {}", blobId.asString(), error);
                            return Mono.empty();
                        }), 16)
                    .filter(candidate -> candidate.payload.length < request.configuration().maxPackableSize())
                    .transform(candidates -> windowByCumulativeSize(candidates, request.configuration().chunkTargetSize()))
                    .filter(batch -> batch.size() > 1)
                    .concatMap(batch -> persistChunkBatch(request, batch, blobIdUpdater))
                    .reduce(CompactionResult.NONE, CompactionResult::combine)
                    .defaultIfEmpty(CompactionResult.NONE);
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

    public Mono<CompactionResult> gcCompact(CompactionRequest request) {
        Preconditions.checkNotNull(request, "'request' must not be null");

        Set<BlobId> referencedBlobs = ConcurrentHashMap.newKeySet();
        Predicate<BlobId> generationCondition = blobId -> matchesGenerationAndFamily(blobId.asString(), request.generation(), request.family());

        return updaterFactory.forPredicate(generationCondition, referencedBlobs::add)
            .flatMap(blobIdUpdater -> {
                Publisher<BlobId> chunkListing = request.family()
                    .map(family -> Flux.concat(
                        rawStore.listBlobs(request.bucketName(), family + "/" + request.generation() + "/chunk"),
                        rawStore.listBlobs(request.bucketName(), family + "_" + request.generation() + "_chunk")))
                    .orElseGet(() -> Flux.from(rawStore.listBlobs(request.bucketName())));

                return Flux.from(chunkListing)
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
                    .buffer(request.configuration().windowBatchSize())
                    .concatMap(existingChunks -> processExistingChunks(request, existingChunks, referencedBlobs, blobIdUpdater))
                    .reduce(CompactionResult.NONE, CompactionResult::combine)
                    .defaultIfEmpty(CompactionResult.NONE);
            });
    }

    private Mono<CompactionResult> persistChunkBatch(CompactionRequest request,
                                                    List<CandidateBlob> batch,
                                                    BlobIdUpdater blobIdUpdater) {
        if (batch.size() <= 1) {
            LOGGER.debug("Skipping compaction for single candidate batch");
            return Mono.just(CompactionResult.NONE);
        }
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

        return Mono.from(rawStore.save(request.bucketName(), chunkId.chunkBlobId(), BlobStoreDAO.BytesBlob.of(writeResult.chunkBytes())))
            .then(updateReferencesAndDeleteOriginalBlobs(request.bucketName(), chunkId, batch, writeResult.slotRanges(), blobIdUpdater));
    }

    private Mono<CompactionResult> updateReferencesAndDeleteOriginalBlobs(BucketName bucketName,
                                                                         ChunkId chunkId,
                                                                         List<CandidateBlob> batch,
                                                                         List<SlotRange> slotRanges,
                                                                         BlobIdUpdater blobIdUpdater) {
        return Flux.range(0, batch.size())
            .concatMap(i -> {
                CandidateBlob candidate = batch.get(i);
                SlotRange range = slotRanges.get(i);
                ChunkId slotRef = ChunkId.slotRef(chunkId, range.offset(), range.limit());
                return blobIdUpdater.replaceReferences(candidate.blobId, slotRef)
                    .thenReturn(Optional.of(candidate))
                    .onErrorResume(e -> {
                        LOGGER.error("Failed to update references for candidate blob {}, skipping deletion of original blob", candidate.blobId.asString(), e);
                        return Mono.just(Optional.empty());
                    });
            })
            .flatMap(opt -> opt.map(Flux::just).orElseGet(Flux::empty))
            .collectList()
            .flatMap(successfulCandidates -> Flux.fromIterable(successfulCandidates)
                .concatMap(candidate -> Mono.from(rawStore.delete(bucketName, candidate.blobId)))
                .then()
                .thenReturn(CompactionResult.builder()
                    .packedBlobs(successfulCandidates.size())
                    .packedBytes(successfulCandidates.stream().mapToLong(c -> c.payload.length).sum())
                    .chunksWritten(1)
                    .freedBytes(successfulCandidates.stream().mapToLong(c -> c.payload.length).sum())
                    .build()));
    }

    private Mono<CompactionResult> processExistingChunks(CompactionRequest request,
                                                        List<ExistingChunk> chunks,
                                                        Set<BlobId> referencedBlobs,
                                                        BlobIdUpdater blobIdUpdater) {
        if (chunks.isEmpty()) {
            return Mono.just(CompactionResult.NONE);
        }

        List<ChunkAnalysis> analyses = new ArrayList<>();
        for (ExistingChunk chunk : chunks) {
            analyses.add(analyzeChunk(chunk, referencedBlobs));
        }

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

        double purgeThreshold = Math.max(request.configuration().purgeDeadRatio(), request.configuration().gainThreshold());
        List<ChunkAnalysis> chunksToPurge = remaining.stream()
            .filter(a -> a.deadRatio() >= purgeThreshold)
            .toList();

        Mono<CompactionResult> purgeMono = Flux.fromIterable(chunksToPurge)
            .concatMap(analysis -> purgeDeadSlots(request, analysis, blobIdUpdater))
            .reduce(CompactionResult.NONE, CompactionResult::combine);

        long mergeThresholdSize = (long) (request.configuration().mergeDeadRatio() * request.configuration().chunkTargetSize());
        List<ChunkAnalysis> nonPurged = remaining.stream()
            .filter(a -> a.deadRatio() < purgeThreshold)
            .toList();

        List<ChunkAnalysis> smallChunks = nonPurged.stream()
            .filter(a -> a.chunk.totalChunkSize < mergeThresholdSize)
            .toList();

        Mono<CompactionResult> mergeMono = mergeSmallChunks(request, smallChunks, blobIdUpdater);

        return Flux.merge(deleteOrphansMono, purgeMono, mergeMono)
            .reduce(CompactionResult.NONE, CompactionResult::combine);
    }

    private ChunkAnalysis analyzeChunk(ExistingChunk chunk, Set<BlobId> referencedBlobs) {
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
            boolean isLive = isSlotReferenced(referencedBlobs, slotRef);

            if (!isLive) {
                deadCount++;
                deadBytes += limit;
            } else {
                liveSlots.add(new LiveSlotMeta(offset, limit, slotRef));
            }
        }

        return new ChunkAnalysis(chunk, liveSlots, starts.size(), deadCount, deadBytes, totalSlotBytes);
    }

    private boolean isSlotReferenced(Set<BlobId> referencedBlobs, ChunkId slotRef) {
        if (referencedBlobs.contains(slotRef)) {
            return true;
        }
        ChunkId zeroLimit = ChunkId.slotRef(slotRef.chunkBlobId().asString(), slotRef.offset(), 0L);
        if (referencedBlobs.contains(zeroLimit)) {
            return true;
        }
        for (BlobId ref : referencedBlobs) {
            String keyStr = ref.asString();
            if (ChunkId.isChunkRef(keyStr)) {
                try {
                    ChunkId parsed = ChunkId.parse(keyStr);
                    if (parsed.chunkId().equals(slotRef.chunkId()) && parsed.offset() == slotRef.offset()) {
                        return true;
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        return false;
    }

    private Mono<CompactionResult> purgeDeadSlots(CompactionRequest request,
                                                  ChunkAnalysis analysis,
                                                  BlobIdUpdater blobIdUpdater) {
        int family = extractFamily(analysis.chunk.chunkBlobId.asString());
        ChunkId newChunkId = ChunkId.ofChunk(family, request.generation());

        return streamSlotPayloads(request.bucketName(), analysis.chunk.chunkBlobId, analysis.liveSlots)
            .collectList()
            .flatMap(liveSlotsWithContent -> {
                List<BlobSlotContent> liveSlotContents = liveSlotsWithContent.stream()
                    .map(slot -> BlobSlotContent.of(slot.payload, slot.metadata))
                    .toList();

                ChunkWriteResult writeResult;
                try {
                    writeResult = ChunkFormat.writeChunk(liveSlotContents);
                } catch (IOException e) {
                    return Mono.error(new ObjectStoreIOException("Failed writing purged chunk " + newChunkId.chunkId(), e));
                }

                return Mono.from(rawStore.save(request.bucketName(), newChunkId.chunkBlobId(), BlobStoreDAO.BytesBlob.of(writeResult.chunkBytes())))
                    .then(updateLiveSlotReferences(newChunkId, liveSlotsWithContent, writeResult.slotRanges(), blobIdUpdater))
                    .then(Mono.from(rawStore.delete(request.bucketName(), analysis.chunk.chunkBlobId)))
                    .thenReturn(CompactionResult.builder()
                        .deadPurged(analysis.deadSlotsCount)
                        .freedBytes(Math.max(0, analysis.chunk.totalChunkSize - writeResult.chunkBytes().length))
                        .build());
            });
    }

    private Mono<CompactionResult> mergeSmallChunks(CompactionRequest request,
                                                   List<ChunkAnalysis> smallChunks,
                                                   BlobIdUpdater blobIdUpdater) {
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
            .concatMap(pair -> mergePair(request, pair, blobIdUpdater))
            .reduce(CompactionResult.NONE, CompactionResult::combine);
    }

    private Mono<CompactionResult> mergePair(CompactionRequest request, ChunkPair pair, BlobIdUpdater blobIdUpdater) {
        int family = extractFamily(pair.c1.chunk.chunkBlobId.asString());
        ChunkId newChunkId = ChunkId.ofChunk(family, request.generation());

        Mono<List<LiveSlotWithContent>> c1Live = streamSlotPayloads(request.bucketName(), pair.c1.chunk.chunkBlobId, pair.c1.liveSlots).collectList();
        Mono<List<LiveSlotWithContent>> c2Live = streamSlotPayloads(request.bucketName(), pair.c2.chunk.chunkBlobId, pair.c2.liveSlots).collectList();

        return Mono.zip(c1Live, c2Live)
            .flatMap(tuple -> {
                List<LiveSlotWithContent> allLive = new ArrayList<>(tuple.getT1());
                allLive.addAll(tuple.getT2());

                List<BlobSlotContent> slotContents = allLive.stream()
                    .map(slot -> BlobSlotContent.of(slot.payload, slot.metadata))
                    .toList();

                ChunkWriteResult writeResult;
                try {
                    writeResult = ChunkFormat.writeChunk(slotContents);
                } catch (IOException e) {
                    return Mono.error(new ObjectStoreIOException("Failed to write merged chunk " + newChunkId.chunkId(), e));
                }

                return Mono.from(rawStore.save(request.bucketName(), newChunkId.chunkBlobId(), BlobStoreDAO.BytesBlob.of(writeResult.chunkBytes())))
                    .then(updateLiveSlotReferences(newChunkId, allLive, writeResult.slotRanges(), blobIdUpdater))
                    .then(Mono.from(rawStore.delete(request.bucketName(), pair.c1.chunk.chunkBlobId)))
                    .then(Mono.from(rawStore.delete(request.bucketName(), pair.c2.chunk.chunkBlobId)))
                    .thenReturn(CompactionResult.builder()
                        .mergedChunks(2)
                        .freedBytes(Math.max(0, (pair.c1.chunk.totalChunkSize + pair.c2.chunk.totalChunkSize) - writeResult.chunkBytes().length))
                        .build());
            });
    }

    private Mono<Void> updateLiveSlotReferences(ChunkId newChunkId,
                                                List<LiveSlotWithContent> liveContents,
                                                List<SlotRange> newRanges,
                                                BlobIdUpdater blobIdUpdater) {
        return Flux.range(0, liveContents.size())
            .concatMap(i -> {
                LiveSlotMeta oldSlot = liveContents.get(i).meta;
                SlotRange newRange = newRanges.get(i);
                ChunkId newSlotRef = ChunkId.slotRef(newChunkId, newRange.offset(), newRange.limit());
                return blobIdUpdater.replaceReferences(oldSlot.slotRef, newSlotRef);
            })
            .then();
    }

    private Flux<LiveSlotWithContent> streamSlotPayloads(BucketName bucketName, BlobId chunkBlobId, List<LiveSlotMeta> liveSlots) {
        return Flux.fromIterable(liveSlots)
            .concatMap(liveSlot -> Mono.from(rawStore.readRange(bucketName, chunkBlobId, liveSlot.offset, liveSlot.offset + liveSlot.limit - 1))
                .publishOn(Schedulers.parallel())
                .flatMap(sliceBlob -> {
                    try {
                        byte[] sliceData = sliceBlob.asBytes().payload();
                        BlobSlot slot = ChunkFormat.parseSlot(sliceData, liveSlot.offset);
                        return Mono.just(new LiveSlotWithContent(liveSlot, slot.payload(), slot.metadata()));
                    } catch (IOException e) {
                        return Mono.error(new ObjectStoreIOException("Failed parsing slot", e));
                    }
                }));
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

    private record LiveSlotMeta(long offset, long limit, ChunkId slotRef) {}

    private record LiveSlotWithContent(LiveSlotMeta meta, byte[] payload, BlobMetadata metadata) {}

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
            return (double) deadBytes / (double) totalSlotBytes;
        }
    }
}
