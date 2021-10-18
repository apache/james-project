# 39. Distributed blob garbage collector

Date: 2020-02-18

## Status

Proposed, not implemented yet.

Work had been started on this topic.

An alternative is proposed in [Deduplicated blobs GC with bloom filters](0049-deduplicated-blobs-gs-with-bloom-filters.md)
and implemented.

## Context

The body, headers, attachments of the mails are stored as blobs in a blob store.
In order to save space in those stores, those blobs are de-duplicated using a hash of their content.
To attain that the current blob store will read the content of the blob before saving it, and generate its id based on
a hash of this content. This way two blobs with the same content will share the same id and thus be saved only once.
This makes the safe deletion of one of those blobs a non trivial problem as we can't delete one blob without ensuring
that all references to it are themselves deleted. For example if two messages share the same blob, when we delete
one message there is at the time being no way to tell if the blob is still referenced by another message.

## Decision

To address this issue, we propose to implement a distributed blob garbage collector built upon the previously developed
Distributed Task Manager.
The de-duplicating blob store will keep track of the references pointing toward a blob in a `References` table.
It will also keep track of the deletion requests for a blob in a `Deletions` table.
When the garbage collector algorithm runs it will fetch from the `Deletions` table the blobs considered to be effectively deleted,
and will check in the `References` table if there are still some references to them. If there is no more reference to a blob,
it will be effectively deleted from the blob store.

To avoid concurrency issues, where we could garbage collect a blob at the same time a new reference to it appear,
a `reference generation` notion will be added. The de-duplicating id of the blobs which before where constructed
using only the hash of their content,  will now include this `reference generation` too.
At a given interval a new `reference generation` will be emitted, since then all new blobs will point to this new generation.

So a `garbage collection iteration` will run only on the `reference generation` `n-2` to avoid concurrency issues.

The switch of generation will be triggered by a task running on the distributed task manager. This task will
emit an event into the event sourcing system to increment the `reference generation`.


## Alternatives

Not de-duplicating the blobs' content, this simple approach which involves storing the same
blob a lot of times can in some scenario be really slow and costly. Albeit it can in some case be preferred for the sake of
simplicity, data security...

## Consequences

This change will necessitate to extract the base blob store responsibilities (store a blob, delete a blob, read a blob)
from the current blob store implementation which is doing the de-duplication, id generation...
The garbage collector will use this low level blob store in order to effectively delete the blobs.

One other consequence of this work, is the fact that there will be no  de-duplication on different `reference generation`,
i.e two blobs with the same content will be stored twice now, if they were created during two different `reference generation`.

When writing a blob into the de-duplicating blob store, we will need to specify the reference to the object (MessageId, AttachmentId...) we
store the blob for. This can make some components harder to implement as we will have to propagate the references.

Since we will not build a distributed task scheduler. To increment the `reference generation` and launch periodically a
`garbage collection iteration`, the scheduling will be done by an external scheduler (cron job, kubernetes cronjob ...)
 which will call a webadmin endpoint to launch this task periodically.
 
## Algorithm visualisation

### Generation 1 and Iteration 1

 * Events
   * `rg1` reference generation is emitted
   * `gci1` garbage collection iteration is emitted
   * An email is sent to `user1`, a `m1` message, and a blob `b1` are stored with `rg1`
   * An email is sent to `user1` and `user2`, `m2` and `m3` messages, and a blob `b2` are stored with `rg1`

#### Tables

##### Generations

| reference generation id |
|-------------------------|
| rg1                     |

| garbage collection iteration id |
|---------------------------------|
| gci1                            |

##### Blobs

| blob id | reference generation id |
|---------|-------------------------|
| b1      | rg1                     |
| b2      | rg1                     |

##### References

| message id | blob id | reference generation id |
|------------|---------|-------------------------|
| m1         | b1      | rg1                     |
| m2         | b2      | rg1                     |
| m3         | b2      | rg1                     |

##### Deletions

Empty

### Generation 2 / Iteration 2

 * Events
   * `rg2` reference generation is emitted
   * `gci2` garbage collection iteration is emitted
   * An email is sent to `user1`, a `m4` message, and a blob `b3` are stored with `rg2`
   * An email is sent to `user1` and `user2`, `m5` and `m6` messages, and a blob `b4` are stored with `rg2`

#### Tables

##### Generations


| reference generation id |
|-------------------------|
| rg1                     |
| rg2                     |

| garbage collection iteration id |
|---------------------------------|
| gci1                            |
| gci2                            |

##### Blobs

| blob id | reference generation id |
|---------|-------------------------|
| b1      | rg1                     |
| b2      | rg1                     |
| b3      | rg2                     |
| b4      | rg2                     |

##### References

| message id | blob id | reference generation id |
|------------|---------|-------------------------|
| m1         | b1      | rg1                     |
| m2         | b2      | rg1                     |
| m3         | b2      | rg1                     |
| m4         | b3      | rg2                     |
| m5         | b4      | rg2                     |
| m6         | b4      | rg2                     |

##### Deletions

Empty

### Generation 3 / Iteration 3

 * Events
   * `rg3` reference generation is emitted
   * `gci3` garbage collection iteration is emitted
   * An email is sent to `user1`, a `m7` message, and a blob `b5` are stored with `rg3`
   * An email is sent to `user1` and `user2`, `m8` and `m9` messages, and a blob `b6` are stored with `rg3`
   * `user1` deletes `m1`, `m2`, `m7`, and `m8` with `gi3`
   * `user2` deletes `m3` with `gi3`

#### Tables: before deletions

##### Generations

| reference generation id |
|-------------------------|
| rg1                     |
| rg2                     |
| rg3                     |

| garbage collection iteration id |
|---------------------------------|
| gci1                            |
| gci2                            |
| gci3                            |

##### Blobs

| blob id | reference generation id |
|---------|-------------------------|
| b1      | rg1                     |
| b2      | rg1                     |
| b3      | rg2                     |
| b4      | rg2                     |
| b5      | rg3                     |
| b6      | rg3                     |

##### References

| message id | blob id | reference generation id |
|------------|---------|-------------------------|
| m1         | b1      | rg1                     |
| m2         | b2      | rg1                     |
| m3         | b2      | rg1                     |
| m4         | b3      | rg2                     |
| m5         | b4      | rg2                     |
| m6         | b4      | rg2                     |
| m7         | b5      | rg3                     |
| m8         | b6      | rg3                     |
| m9         | b6      | rg3                     |

##### Deletions

Empty


#### Tables: after deletions


##### Generations

| reference generation id |
|-------------------------|
| rg1                     |
| rg2                     |
| rg3                     |

| garbage collection iteration id |
|---------------------------------|
| gci1                            |
| gci2                            |
| gci3                            |

##### Blobs

| blob id | reference generation id |
|---------|-------------------------|
| b1      | rg1                     |
| b2      | rg1                     |
| b3      | rg2                     |
| b4      | rg2                     |
| b5      | rg3                     |
| b6      | rg3                     |

##### References

| message id | blob id | reference generation id |
|------------|---------|-------------------------|
| m4         | b3      | rg2                     |
| m5         | b4      | rg2                     |
| m6         | b4      | rg2                     |
| m9         | b6      | rg3                     |

##### Deletions

| blob id | reference generation id | date  | garbage collection iteration id |
|---------|-------------------------|-------|---------------------------------|
| b1      | rg1                     | 10:42 | gci3                            |
| b2      | rg1                     | 10:42 | gci3                            |
| b2      | rg1                     | 13:37 | gci3                            |
| b5      | rg3                     | 10:42 | gci3                            |
| b6      | rg3                     | 10:42 | gci3                            |

#### Running the algorithm

 * fetch `Deletions` for `gci3` in `deletions`
 * find distinct `reference-generation-id` of `deletions` in `generations = {rg1, rg3}`
 * For each generation
   * *rg1*
     * filter `deletions` to keep only `rg1` entries and extract `blob-ids` in `concernedBlobs = {b1, b2}`
     * fetch all references to `concernedBlobs` and build a Bloom-Filter in `foundedReferences = {}`
     * filter `concernedBlobs` to keep only those which are not present in `foundedReferences` in `blobsToDelete = {b1, b2}`
     * Remove `blobsToDelete` from `Blobs` and `Deletions`
   * *rg3*
     * filter `deletions` to keep only `rg3` entries and extract `blob-ids` in `concernedBlobs = {b5, b6}`
     * fetch all references to `concernedBlobs` and build a Bloom-Filter in `foundedReferences = {b6}`
     * filter `concernedBlobs` to keep only those which are not present in `foundedReferences` in `blobsToDelete = {b5}`
     * Remove `blobsToDelete` from `Blobs` and `Deletions`


#### Tables: after garbage collection

##### Generations


| reference generation id |
|-------------------------|
| rg1                     |
| rg2                     |
| rg3                     |

| garbage collection iteration id |
|---------------------------------|
| gci1                            |
| gci2                            |
| gci3                            |

##### Blobs

| blob id | reference generation id |
|---------|-------------------------|
| b3      | rg2                     |
| b4      | rg2                     |
| b6      | rg3                     |

##### References

| message id | blob id | generation id |
|------------|---------|---------------|
| m4         | b3      | g2            |
| m5         | b4      | g2            |
| m6         | b4      | g2            |
| m9         | b6      | g3            |

##### Deletions

| blob id | reference generation id | date  | garbage collection iteration id |
|---------|-------------------------|-------|---------------------------------|
| b6      | rg3                     | 10:42 | gci3                            |

### Generations 4

 * Events
   * `rg4` reference generation is emitted
   * `gci4` garbage collection iteration is emitted
   * `user2` deletes `m9` with `gcg4`

#### Tables: before deletions

##### Generations


| reference generation id |
|-------------------------|
| rg1                     |
| rg2                     |
| rg3                     |
| rg4                     |

| garbage collection iteration id |
|---------------------------------|
| gci1                            |
| gci2                            |
| gci3                            |
| gci4                            |

##### Blobs

| blob id | reference generation id |
|---------|-------------------------|
| b3      | rg2                     |
| b4      | rg2                     |
| b6      | rg3                     |

##### References

| message id | blob id | reference generation id |
|------------|---------|-------------------------|
| m4         | b3      | rg2                     |
| m5         | b4      | rg2                     |
| m6         | b4      | rg2                     |
| m9         | b6      | rg3                     |

##### Deletions

| blob id | reference generation id | date  | garbage collection iteration id |
|---------|-------------------------|-------|---------------------------------|
| b6      | rg3                     | 10:42 | gci3                            |

#### Tables: after deletions

##### Generations

| reference generation id |
|-------------------------|
| rg1                     |
| rg2                     |
| rg3                     |
| rg4                     |

| garbage collection iteration id |
|---------------------------------|
| gci1                            |
| gci2                            |
| gci3                            |
| gci4                            |

##### Blobs

| blob id | reference generation id |
|---------|-------------------------|
| b3      | rg2                     |
| b4      | rg2                     |
| b6      | rg3                     |

##### References

| message id | blob id | reference generation id |
|------------|---------|-------------------------|
| m4         | b3      | rg2                     |
| m5         | b4      | rg2                     |
| m6         | b4      | rg2                     |

##### Deletions

| blob id | reference generation id | date  | garbage collection iteration id |
|---------|-------------------------|-------|---------------------------------|
| b6      | rg3                     | 10:42 | gci3                            |
| b6      | rg3                     | 18:42 | gci4                            |                  |

#### Running the algorithm

 * fetch `Deletions` for `gci4` in `deletions`
 * find distinct `generation-id` of `deletions` in `generations = {rg3}`
 * For each generation
   * *rg3*
     * filter `deletions` to keep only `rg3` entries and extract `blob-ids` in `concernedBlobs = {b6}`
     * fetch all references to `concernedBlobs` and build a Bloom-Filter in `foundedReferences = {}`
     * filter `concernedBlobs` to keep only those which are not present in `foundedReferences` in `blobsToDelete = {b6}`
     * Remove `blobsToDelete` from `Blobs` and `Deletions`


#### Tables: after garbage collection

##### Generations


| reference generation id |
|-------------------------|
| rg1                     |
| rg2                     |
| rg3                     |
| rg4                     |

| garbage collection iteration id |
|---------------------------------|
| gci1                            |
| gci2                            |
| gci3                            |
| gci4                            |

##### Blobs

| blob id | reference generation id |
|---------|-------------------------|
| b3      | rg2                     |
| b4      | rg2                     |

##### References

| message id | blob id | reference generation id |
|------------|---------|-------------------------|
| m4         | b3      | rg2                     |
| m5         | b4      | rg2                     |
| m6         | b4      | rg2                     |

##### Deletions

Empty


