# 76. S3 Object Compaction

Date: 2026-09-20

## Status

Implemented (JAMES-4231)

## Context

High email ingest volumes in Apache James deployments utilizing S3/MinIO object storage result in
hundreds of millions of small S3 objects (average mail body/header size ~20–50KB). While S3 provides
virtually limitless capacity, operating at this granularity introduces substantial challenges:
- High S3 API request costs (PUT/GET/LIST pricing per 1,000 operations).
- High metadata overhead and slow bucket listing during storage maintenance and garbage collection.
- Reduced overall object storage throughput due to connection setup and small payload overheads.

Apache James already features generation-aware blob IDs (`GenerationAwareBlobId`) and Bloom filter
garbage collection (`0049-deduplicated-blobs-gs-with-bloom-filters.md`). However, once a generation
becomes immutable, standalone small objects remain permanently fragmented across S3.

## Decision

We introduce an autonomous S3 object compaction engine (`server/blob/blob-compaction`) and chunked
blob store DAO (`ChunkedBlobStoreDAO`) that packs multiple small standalone blobs into large, immutable
chunk objects (~100MB target size) with slot-level virtual addressing and ranged read support.

### 1. Chunk File Format (`ChunkFormat`)

A chunk object is a single binary object stored in S3 containing multiple concatenated blob slots and
a trailer footer:

```
+-------------------------------------------------------------------------------+
| Chunk Header (16 bytes: magic 0x4A43484B, version 1, slot count, flags)      |
+-------------------------------------------------------------------------------+
| Slot 0: [Slot Header: compressed size, uncompressed size, flags][Payload]     |
+-------------------------------------------------------------------------------+
| Slot 1: [Slot Header: compressed size, uncompressed size, flags][Payload]     |
+-------------------------------------------------------------------------------+
| ...                                                                           |
+-------------------------------------------------------------------------------+
| Slot N-1: [Slot Header: compressed size, uncompressed size, flags][Payload]   |
+-------------------------------------------------------------------------------+
| Footer: [Slot 0 Offset (8B), Size (4B), Flags (4B)]...[Slot N-1 Offset, ...]  |
+-------------------------------------------------------------------------------+
| Footer Metadata Trailer (4 bytes: footer size) + End Magic (0x454F4643)       |
+-------------------------------------------------------------------------------+
```

Key format invariants:
- **Per-Slot Zstandard Compression:** Each slot is independently compressed using Zstandard
  (`content-encoding=zstd\n`), allowing individual slot reads via HTTP byte-range requests without
  decompressing or buffering the full 100MB chunk.
- **Suffix-Indexed Footer:** The chunk footer is located at the tail of the object. Inspecting chunk
  metadata or slot allocations requires only a single 64KB HTTP ranged read (`readRange(..., -65536, -1)`).

### 2. Virtual Slot Addressing (`ChunkId`)

Compacted slots are addressed via synthetic blob IDs formatted as:
```
<family>_<generation>_chunk<base64urlHash>~<offset>~<limit>
```
- **Transparent DAO Interception:** `ChunkedBlobStoreDAO` acts as a decorator around the underlying
  `BlobStoreDAO`. Requests for standalone blobs pass through to the delegate. Requests for virtual
  slot IDs parse the offset and limit, translating them into exact HTTP ranged reads
  (`readRange(bucket, chunkId, offset, offset + limit - 1)`).
- **Format Invariance:** Virtual slot IDs conform to `ChunkMarker` regex rules
  (`^\\d+_\\d+_chunk[A-Za-z0-9_-]{16,}(~\\d+~\\d+)?$`), ensuring seamless interoperability with
  Bloom filter GC without treating chunks as unreferenced blobs.

### 3. Compaction Algorithms & Crash Safety

Compaction runs as background distributed tasks (`BlobCompactionTask`) managed via WebAdmin endpoints:
- **Initial Compaction (`initialCompact`):**
  1. Windowed candidate scanning in batches of 1,000 blobs to bound heap usage.
  2. Candidate payloads packed into a new chunk object and written to S3 raw storage.
  3. Source-of-truth metadata tables updated in Cassandra (`messageV3`, `messageIdToImapUid`,
     `messageIdTable`) pointing old blob IDs to new slot refs.
  4. Original standalone blobs deleted from S3 only for candidates whose metadata references
     were successfully updated.
- **GC Compaction (`gcCompact`):**
  1. Inspect existing chunks using footer-only ranged reads (metadata-only).
  2. Orphan chunks (100% dead slots) deleted immediately with zero payload reads.
  3. Chunks with dead slot ratios exceeding thresholds rewritten to discard dead slots.
  4. Small adjacent chunks merged into target-sized chunks. Surviving slots are streamed
     one-by-one via HTTP ranged reads, bounding GC heap consumption to $O(\text{maxSlotSize})$.

### 4. Configuration Guards and Layering

Because client-side AES encryption (`CryptoConfig`) is incompatible with arbitrary HTTP byte-range
slicing without custom IV handling, compaction automatically disables itself when encryption or
whole-blob compression is configured on the target bucket, logging an informative warning.

## Consequences

### Positive
- **Object Count Reduction:** Decreases S3 object count by 10x to 100x for historical email generations.
- **Cost Reduction:** Drastically reduces S3 LIST and GET request volume and monthly storage request fees.
- **Low Read Latency:** Reading compacted emails requires only a single HTTP byte-range GET request,
  matching the latency of standalone object reads.
- **Crash Safety:** Step ordering guarantees no dangling references; failed runs leave either intact
  original blobs or orphan chunks that are automatically reclaimed on the next GC run.

### Negative / Trade-offs
- Compacting historical generations consumes temporary I/O to read candidates and write chunks.
- Cassandra reference updates introduce a transient inconsistency window during process crashes
  that is healed by `CassandraBlobIdRepairer`.
- Materializes generation live reference multimaps in memory during compaction passes (~200MB heap
  per 1M references); future iterations can introduce partition-paged reference lookups.
