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

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;

import java.io.IOException;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.export.api.BlobExportMechanism;
import org.apache.james.blob.export.api.FileExtension;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageContentNotFoundException;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.DeletedMessageZipper;
import org.apache.james.vault.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteSource;
import com.google.common.io.FileBackedOutputStream;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ExportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportService.class);
    private static final Predicate<Throwable> CONTENT_NOT_FOUND_PREDICATE =
        throwable -> throwable instanceof DeletedMessageContentNotFoundException;

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

    void export(Username username, Query exportQuery, MailAddress exportToAddress, Runnable messageToExportCallback) throws IOException {
        Flux<DeletedMessage> matchedMessages = Flux.from(vault.search(username, exportQuery))
            .doOnNext(any -> messageToExportCallback.run());

        BlobId blobId = zipToBlob(username, matchedMessages);

        blobExport.blobId(blobId)
            .with(exportToAddress)
            .explanation(exportMessage(username))
            .filePrefix(String.format("deleted-message-of-%s_", username.asString()))
            .fileExtension(FileExtension.ZIP)
            .export();
    }

    private BlobId zipToBlob(Username username, Flux<DeletedMessage> messages) throws IOException {
        try (FileBackedOutputStream fileOutputStream = new FileBackedOutputStream(FileUtils.ONE_MB_BI.intValue())) {
            zipper.zip(contentLoader(username), messages.toStream(), fileOutputStream);
            ByteSource byteSource = fileOutputStream.asByteSource();
            return Mono.from(blobStore.save(blobStore.getDefaultBucketName(), byteSource.openStream(), LOW_COST)).block();
        }
    }

    private DeletedMessageZipper.DeletedMessageContentLoader contentLoader(Username username) {
        return message -> Mono.from(vault.loadMimeMessage(username, message.getMessageId()))
            .onErrorResume(CONTENT_NOT_FOUND_PREDICATE, throwable -> {
                LOGGER.info(
                    "Error happened when loading mime message associated with id {} of user {} in the vault",
                    message.getMessageId().serialize(),
                    username.asString(),
                    throwable);
                return Mono.empty();
            })
            .blockOptional();
    }

    private String exportMessage(Username username) {
        return String.format("Some deleted messages from user %s has been shared to you", username.asString());
    }
}
