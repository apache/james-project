# 76. S3 Object Compaction

Date: 2026-09-20

## Status

Proposed

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

A chunk object is a single binary object stored in S3 containing a format byte, sequential blob slots,
and a trailing suffix footer:

```
+-------------------------------------------------------------------------------+
| Format Byte (1 byte: 0x01)                                                    |
+-------------------------------------------------------------------------------+
| Slot 0: [Offset (8B)][CRC32C (4B)][Metadata lines\n\n][Raw Payload Bytes]     |
+-------------------------------------------------------------------------------+
| Slot 1: [Offset (8B)][CRC32C (4B)][Metadata lines\n\n][Raw Payload Bytes]     |
+-------------------------------------------------------------------------------+
| ...                                                                           |
+-------------------------------------------------------------------------------+
| Slot N-1: [Offset (8B)][CRC32C (4B)][Metadata lines\n\n][Raw Payload Bytes]   |
+-------------------------------------------------------------------------------+
| Footer: Comma-separated slot start offsets (ASCII, e.g. "1,1024,2048")        |
+-------------------------------------------------------------------------------+
| Footer Length (4 bytes int)                                                   |
+-------------------------------------------------------------------------------+
| Footer Position (8 bytes long: offset where Footer starts)                    |
+-------------------------------------------------------------------------------+
```

Key format invariants:
- **Direct Virtual Addressing:** Each slot is stored with its exact byte offset, 32-bit CRC32C checksum
  of the uncompressed payload, optional newline-delimited key=value metadata, and raw payload bytes. Clients
  can retrieve individual slots via HTTP byte-range requests without reading or buffering the entire chunk.
- **Suffix-Indexed Footer:** The chunk footer is located at the tail of the object, followed by a 12-byte
  metadata trailer (4-byte footer length + 8-byte footer position). Inspecting chunk metadata or slot
  allocations requires only reading the tail bytes of the object.

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

Compaction is exclusively supported on S3/MinIO object storage (`BlobStoreImplName.S3`).
Because client-side AES encryption (`CryptoConfig`) is incompatible with arbitrary HTTP byte-range
slicing without custom IV handling, compaction automatically disables itself when encryption,
whole-blob compression, or non-S3 storage implementations are configured, logging an informative warning.
Furthermore, WebAdmin endpoints enforce that only completed historical generations can be compacted,
strictly rejecting requests targeting the active generation, the 1-generation grace period (`generation + 1 >= currentGeneration`, matching `GenerationAwareBlobId.inActiveGeneration()`), or future generations
to prevent race conditions with in-flight mail ingestion.

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
