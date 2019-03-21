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

package org.apache.james.webadmin.vault.routes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.export.api.BlobExportMechanism;
import org.apache.james.core.MailAddress;
import org.apache.james.core.User;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.DeletedMessageZipper;
import org.apache.james.vault.search.Query;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.ThrowingFunction;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class ExportService {

    private class ZippedData {
        private final long contentLength;
        private final InputStream inputStream;

        private ZippedData(long contentLength, InputStream content) {
            this.contentLength = contentLength;
            this.inputStream = content;
        }
    }

    private static final String TEMPORARY_FILE_EXTENSION = ".temp";

    private final BlobExportMechanism blobExport;
    private final BlobStore blobStore;
    private final DeletedMessageZipper zipper;
    private final DeletedMessageVault vault;

    @Inject
    @VisibleForTesting
    ExportService(BlobExportMechanism blobExport, BlobStore blobStore, DeletedMessageZipper zipper, DeletedMessageVault vault) {
        this.blobExport = blobExport;
        this.blobStore = blobStore;
        this.zipper = zipper;
        this.vault = vault;
    }

    Mono<Void> export(User user, Query exportQuery, MailAddress exportToAddress,
                      Runnable messageToExportCallback) {

        return matchingMessages(user, exportQuery)
            .doOnNext(any -> messageToExportCallback.run())
            .collect(Guavate.toImmutableList())
            .map(Collection::stream)
            .flatMap(messages -> Mono.fromCallable(() -> zipData(user, messages)))
            .flatMap(sneakyThrow(zippedData -> blobStore.save(zippedData.inputStream, zippedData.contentLength)))
            .flatMap(blobId -> exportTo(user, exportToAddress, blobId))
            .then();
    }

    private Flux<DeletedMessage> matchingMessages(User user, Query exportQuery) {
        return Flux.from(vault.search(user, exportQuery))
            .publishOn(Schedulers.elastic());
    }

    private ZippedData zipData(User user, Stream<DeletedMessage> messages) throws IOException {
        File tempFile = temporaryFile();
        FileOutputStream fileOutputStream = new FileOutputStream(tempFile);

        zipper.zip(message -> loadMessageContent(user, message), messages, fileOutputStream);
        return new ZippedData(tempFile.length(), new FileInputStream(tempFile));
    }

    private File temporaryFile() throws IOException {
        String tempFileName = UUID.randomUUID().toString();
        File tempFile = Files.createTempFile(tempFileName, TEMPORARY_FILE_EXTENSION).toFile();
        tempFile.deleteOnExit();
        return tempFile;
    }

    private InputStream loadMessageContent(User user, DeletedMessage message) {
        return Mono.from(vault.loadMimeMessage(user, message.getMessageId()))
            .block();
    }

    private Mono<Void> exportTo(User user, MailAddress exportToAddress, BlobId blobId) {
        return Mono.fromRunnable(() -> blobExport
            .blobId(blobId)
            .with(exportToAddress)
            .explanation(String.format("Some deleted messages from user %s has been shared to you", user.asString()))
            .export());
    }

    private <T, R> Function<T, R> sneakyThrow(ThrowingFunction<T, R> function) {
        return Throwing.function(function).sneakyThrow();
    }
}
