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

import java.io.IOException;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.export.api.BlobExportMechanism;
import org.apache.james.blob.export.api.FileExtension;
import org.apache.james.core.MailAddress;
import org.apache.james.core.User;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.DeletedMessageZipper;
import org.apache.james.vault.search.Query;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteSource;
import com.google.common.io.FileBackedOutputStream;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ExportService {
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

    void export(User user, Query exportQuery, MailAddress exportToAddress, Runnable messageToExportCallback) throws IOException {
        Flux<DeletedMessage> matchedMessages = Flux.from(vault.search(user, exportQuery))
            .doOnNext(any -> messageToExportCallback.run());

        BlobId blobId = zipToBlob(user, matchedMessages);

        blobExport.blobId(blobId)
            .with(exportToAddress)
            .explanation(exportMessage(user))
            .fileExtension(FileExtension.ZIP)
            .export();
    }

    private BlobId zipToBlob(User user, Flux<DeletedMessage> messages) throws IOException {
        try (FileBackedOutputStream fileOutputStream = new FileBackedOutputStream(FileUtils.ONE_MB_BI.intValue())) {
            zipper.zip(contentLoader(user), messages.toStream(), fileOutputStream);
            ByteSource byteSource = fileOutputStream.asByteSource();
            return blobStore.save(byteSource.openStream(), byteSource.size()).block();
        }
    }

    private DeletedMessageZipper.DeletedMessageContentLoader contentLoader(User user) {
        return message -> Mono.from(vault.loadMimeMessage(user, message.getMessageId())).blockOptional();
    }

    private String exportMessage(User user) {
        return String.format("Some deleted messages from user %s has been shared to you", user.asString());
    }
}
