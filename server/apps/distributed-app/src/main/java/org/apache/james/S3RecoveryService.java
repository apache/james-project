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

package org.apache.james;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.apache.james.blob.api.BlobStore.StoragePolicy.SIZE_BASED;
import static org.apache.james.blob.api.BlobStoreDAO.RECOVERY_BLOB_PREFIX;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.HeaderAndBodyByteContent;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.user.api.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Walks the blob store looking for {@code recovery/} sidecars (written by
 * {@code CassandraMessageDAOV3} when {@code mailbox.blob.recovery.mode} is enabled) and restores the
 * associated messages into a {@code Restored-messages} mailbox of each local {@code Delivered-To} recipient.
 *
 * <p>Reads go through the configured {@link BlobStore}, so AES decryption and decompression are applied
 * transparently, exactly as the write path did.</p>
 */
public class S3RecoveryService {
    public record Report(long processed, long restored, long skippedByDate, long skippedNoLocalUser, long failed) {
        public static Report empty() {
            return new Report(0, 0, 0, 0, 0);
        }

        Report merge(Report other) {
            return new Report(processed + other.processed,
                restored + other.restored,
                skippedByDate + other.skippedByDate,
                skippedNoLocalUser + other.skippedNoLocalUser,
                failed + other.failed);
        }
    }

    private record RecoveredMessage(List<MailAddress> recipients, Optional<Date> date, Content content) {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(S3RecoveryService.class);
    private static final String RESTORE_MAILBOX = "Restored-messages";
    private static final int CONCURRENCY = 8;
    private static final String DELIVERED_TO = "Delivered-To";
    private static final Report RESTORED = new Report(1, 1, 0, 0, 0);
    private static final Report SKIPPED_BY_DATE = new Report(1, 0, 1, 0, 0);
    private static final Report SKIPPED_NO_LOCAL_USER = new Report(1, 0, 0, 1, 0);
    private static final Report FAILED = new Report(1, 0, 0, 0, 1);

    private final BlobStore blobStore;
    private final BlobStoreDAO blobStoreDAO;
    private final BlobId.Factory blobIdFactory;
    private final MailboxManager mailboxManager;
    private final UsersRepository usersRepository;
    private final RecoveryConfiguration configuration;
    private final Set<Username> ensuredMailboxes = ConcurrentHashMap.newKeySet();

    @Inject
    public S3RecoveryService(BlobStore blobStore, BlobStoreDAO blobStoreDAO, BlobId.Factory blobIdFactory,
                             MailboxManager mailboxManager, UsersRepository usersRepository,
                             RecoveryConfiguration configuration) {
        this.blobStore = blobStore;
        this.blobStoreDAO = blobStoreDAO;
        this.blobIdFactory = blobIdFactory;
        this.mailboxManager = mailboxManager;
        this.usersRepository = usersRepository;
        this.configuration = configuration;
    }

    public Mono<Report> run() {
        BucketName bucket = blobStore.getDefaultBucketName();
        String prefix = RECOVERY_BLOB_PREFIX + configuration.headerBlobPrefix();
        LOGGER.info("Starting S3 recovery on bucket {} (prefix: {}, restore after: {})",
            bucket.asString(), prefix, configuration.restoreAfter());
        return Flux.from(blobStoreDAO.listBlobs(bucket, prefix))
            .map(BlobId::asString)
            .flatMap(recoveryKey -> restoreOne(bucket, recoveryKey), CONCURRENCY)
            .reduce(Report.empty(), Report::merge)
            .doOnNext(report -> LOGGER.info("S3 recovery finished: {}", report));
    }

    private Mono<Report> restoreOne(BucketName bucket, String recoveryKey) {
        String headerKey = recoveryKey.substring(RECOVERY_BLOB_PREFIX.length());
        BlobId headerBlobId = blobIdFactory.parse(headerKey);
        BlobId recoveryBlobId = blobIdFactory.parse(recoveryKey);

        return Mono.from(blobStoreDAO.readBytes(bucket, recoveryBlobId))
            .map(sidecar -> blobIdFactory.parse(new String(sidecar.payload(), StandardCharsets.UTF_8).trim()))
            .flatMap(bodyBlobId -> recover(bucket, headerBlobId, bodyBlobId))
            .flatMap(this::restore)
            .onErrorResume(error -> {
                LOGGER.error("Failed to recover message from {}", recoveryKey, error);
                return Mono.just(FAILED);
            });
    }

    private Mono<RecoveredMessage> recover(BucketName bucket, BlobId headerBlobId, BlobId bodyBlobId) {
        return Mono.from(blobStore.readBytes(bucket, headerBlobId, SIZE_BASED))
            .flatMap(headerBytes -> {
                MessageHeaders headers = parseHeaders(headerBytes);
                return Mono.from(blobStore.readBytes(bucket, bodyBlobId, LOW_COST))
                    .map(bodyBytes -> new RecoveredMessage(headers.recipients(), headers.date(),
                        new HeaderAndBodyByteContent(headerBytes, bodyBytes)));
            });
    }

    private Mono<Report> restore(RecoveredMessage message) {
        if (filteredOutByDate(message.date())) {
            return Mono.just(SKIPPED_BY_DATE);
        }
        return Flux.fromIterable(message.recipients())
            .concatMap(recipient -> localUser(recipient).flux())
            .collectList()
            .flatMap(users -> {
                if (users.isEmpty()) {
                    LOGGER.info("Skipping message: no local Delivered-To recipient among {}", message.recipients());
                    return Mono.just(SKIPPED_NO_LOCAL_USER);
                }
                return Flux.fromIterable(users)
                    .concatMap(user -> appendToRestored(user, message))
                    .then(Mono.just(RESTORED));
            });
    }

    private boolean filteredOutByDate(Optional<Date> date) {
        return configuration.restoreAfter()
            .flatMap(after -> date.map(value -> !value.toInstant().isAfter(after)))
            .orElse(false);
    }

    private Mono<Username> localUser(MailAddress recipient) {
        return Mono.fromCallable(() -> usersRepository.getUsername(recipient))
            .flatMap(username -> Mono.from(usersRepository.containsReactive(username))
                .<Username>handle((present, sink) -> {
                    if (present) {
                        sink.next(username);
                    }
                }))
            .onErrorResume(error -> {
                LOGGER.warn("Unable to resolve local user for {}", recipient.asString(), error);
                return Mono.empty();
            });
    }

    private Mono<Void> appendToRestored(Username user, RecoveredMessage message) {
        return Mono.fromRunnable(() -> doAppend(user, message))
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    private void doAppend(Username user, RecoveredMessage message) {
        try {
            MailboxSession session = mailboxManager.createSystemSession(user);
            MailboxPath path = ensureMailbox(user, session);
            MessageManager messageManager = mailboxManager.getMailbox(path, session);
            messageManager.appendMessage(MessageManager.AppendCommand.builder()
                .withInternalDate(message.date())
                .notRecent()
                .build(message.content()), session);
        } catch (Exception e) {
            throw new RuntimeException("Failed to append recovered message to " + user.asString(), e);
        }
    }

    private MailboxPath ensureMailbox(Username user, MailboxSession session) throws Exception {
        MailboxPath path = MailboxPath.forUser(user, RESTORE_MAILBOX);
        if (ensuredMailboxes.add(user)) {
            try {
                mailboxManager.createMailbox(path, session);
            } catch (MailboxExistsException e) {
                // The mailbox already exists, nothing to do.
            }
        }
        return path;
    }

    @VisibleForTesting
    static MessageHeaders parseHeaders(byte[] headerBytes) {
        try {
            DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
            messageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
            Message message = messageBuilder.parseMessage(new ByteArrayInputStream(headerBytes));
            List<MailAddress> recipients = message.getHeader().getFields(DELIVERED_TO).stream()
                .map(Field::getBody)
                .flatMap(value -> toMailAddress(value).stream())
                .toList();
            return new MessageHeaders(recipients, Optional.ofNullable(message.getDate()));
        } catch (Exception e) {
            LOGGER.warn("Unable to parse recovered message headers", e);
            return new MessageHeaders(List.of(), Optional.empty());
        }
    }

    private static Optional<MailAddress> toMailAddress(String value) {
        try {
            return Optional.of(new MailAddress(value.trim()));
        } catch (Exception e) {
            LOGGER.warn("Unable to parse Delivered-To value '{}'", value);
            return Optional.empty();
        }
    }

    record MessageHeaders(List<MailAddress> recipients, Optional<Date> date) {
    }
}
