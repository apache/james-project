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

package org.apache.james.webadmin.service;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.export.api.BlobExportMechanism;
import org.apache.james.blob.export.api.FileExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.backup.MailboxBackup;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.Lists;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ExportService {

    public enum Stage {
        STARTING,
        ZIPPING,
        EXPORTING,
        COMPLETED,
    }

    public static class Progress {
        private final ConcurrentLinkedDeque<Stage> stages;

        public Progress() {
            this.stages = new ConcurrentLinkedDeque<>(Lists.newArrayList(Stage.STARTING));
        }

        public Stage getStage() {
            return stages.getLast();
        }

        public void setStage(Stage stage) {
            this.stages.add(stage);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportService.class);
    private static final String EXPLANATION = "The backup of your mailboxes has been exported to you";
    private static final String FILE_PREFIX = "mailbox-backup-";

    private final MailboxBackup mailboxBackup;
    private final BlobStore blobStore;
    private final BlobExportMechanism blobExport;
    private final UsersRepository usersRepository;

    @Inject
    ExportService(MailboxBackup mailboxBackup, BlobStore blobStore, BlobExportMechanism blobExport, UsersRepository usersRepository) {
        this.mailboxBackup = mailboxBackup;
        this.blobStore = blobStore;
        this.blobExport = blobExport;
        this.usersRepository = usersRepository;
    }

    static class ZippedMailboxes implements Closeable {
        private final InputStream content;
        private final Mono<Void> backupCompletion;

        ZippedMailboxes(InputStream content, Mono<Void> backupCompletion) {
            this.content = content;
            this.backupCompletion = backupCompletion;
        }

        @Override
        public void close() throws IOException {
            content.close();
        }
    }

    public Mono<Task.Result> export(Progress progress, Username username) {
        return Mono.usingWhen(
            Mono.fromCallable(() -> zipMailboxesContent(progress, username)),
            zippedMailboxes -> export(progress, username, zippedMailboxes.content, zippedMailboxes.backupCompletion),
            this::closeResource,
            (zippedMailboxes, throwable) -> closeResource(zippedMailboxes),
            this::closeResource
        );
    }

    ZippedMailboxes zipMailboxesContent(Progress progress, Username username) throws IOException {
        progress.setStage(Stage.ZIPPING);
        PipedOutputStream out = new PipedOutputStream();
        PipedInputStream in = new PipedInputStream(out);

        CompletableFuture<Void> backupCompletion = writeUserMailboxesContent(username, out)
            .subscribeOn(Schedulers.boundedElastic())
            .toFuture();

        return new ZippedMailboxes(in, Mono.fromFuture(backupCompletion));
    }

    Mono<Task.Result> export(Progress progress, Username username, InputStream inputStream) {
        return export(progress, username, inputStream, Mono.empty());
    }

    private Mono<Task.Result> export(Progress progress, Username username, InputStream inputStream, Mono<Void> backupCompletion) {
        return Mono.fromRunnable(() -> progress.setStage(Stage.EXPORTING))
            .then(Mono.usingWhen(
                blobStore.save(blobStore.getDefaultBucketName(), inputStream, BlobStore.StoragePolicy.LOW_COST),
                // The archive is only complete if the backup succeeded: a failed backup closes the pipe, which looks like a regular end of stream
                blobId -> backupCompletion.then(export(username, blobId)),
                this::deleteBlob)
            .doOnSuccess(any -> progress.setStage(Stage.COMPLETED))
            .thenReturn(Task.Result.COMPLETED)
            .onErrorResume(e -> {
                LOGGER.error("Error exporting mailboxes of user: {}", username.asString(), e);
                return Mono.just(Task.Result.PARTIAL);
            }));
    }

    private Mono<Void> export(Username username, BlobId blobId) {
        return Mono.fromRunnable(Throwing.runnable(() ->
            blobExport.blobId(blobId)
                .with(usersRepository.getMailAddressFor(username))
                .explanation(EXPLANATION)
                .filePrefix(FILE_PREFIX + username.asString() + "-")
                .fileExtension(FileExtension.ZIP)
                .export())
            .sneakyThrow())
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    private Mono<Void> deleteBlob(BlobId blobId) {
        return Mono.from(blobStore.delete(blobStore.getDefaultBucketName(), blobId))
            .onErrorResume(e -> {
                LOGGER.error("Error deleting Blob with blobId: {}", blobId.asString(), e);
                return Mono.empty();
            })
            .then();
    }

    private Mono<Void> writeUserMailboxesContent(Username username, PipedOutputStream out) {
        return Mono.usingWhen(
            Mono.fromCallable(() -> out),
            outputStream -> Mono.fromRunnable(Throwing.runnable(() -> mailboxBackup.backupAccount(username, outputStream))),
            this::closeResource,
            (outputStream, throwable) -> closeResource(outputStream)
                .doFinally(any -> LOGGER.error("Error while backing up mailboxes for user {}", username.asString(), throwable)),
            this::closeResource);
    }

    private Mono<Void> closeResource(Closeable resource) {
        return Mono.fromRunnable(() -> {
            try {
                resource.close();
            } catch (IOException e) {
                LOGGER.error("Error while closing resource", e);
            }
        });
    }
}
