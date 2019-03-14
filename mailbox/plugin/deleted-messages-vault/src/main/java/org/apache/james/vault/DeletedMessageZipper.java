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

package org.apache.james.vault;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.backup.MessageIdExtraField;
import org.apache.james.mailbox.backup.SizeExtraField;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.util.OptionalUtils;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;

public class DeletedMessageZipper {

    interface DeletedMessageContentLoader {
        Publisher<InputStream> load(DeletedMessage deletedMessage);
    }

    DeletedMessageZipper() {
        ExtraFieldUtils.register(MessageIdExtraField.class);
        ExtraFieldUtils.register(SizeExtraField.class);
    }

    public void zip(DeletedMessageContentLoader contentLoader, Stream<DeletedMessage> deletedMessages,
                    OutputStream outputStream) throws IOException {
        try (ZipArchiveOutputStream zipOutputStream = newZipArchiveOutputStream(outputStream)) {
            deletedMessages
                .map(message -> messageWithContent(message, contentLoader))
                .flatMap(OptionalUtils::toStream)
                .forEach(Throwing.<DeletedMessageWithContent>consumer(
                    messageWithContent -> putMessageToEntry(zipOutputStream, messageWithContent)).sneakyThrow());

            zipOutputStream.finish();
        }
    }

    @VisibleForTesting
    Optional<DeletedMessageWithContent> messageWithContent(DeletedMessage message, DeletedMessageContentLoader loader) {
        return Mono.from(loader.load(message))
            .map(messageContent -> new DeletedMessageWithContent(message, messageContent))
            .blockOptional();
    }

    @VisibleForTesting
    ZipArchiveOutputStream newZipArchiveOutputStream(OutputStream outputStream) {
        return new ZipArchiveOutputStream(outputStream);
    }

    @VisibleForTesting
    void putMessageToEntry(ZipArchiveOutputStream zipOutputStream, DeletedMessageWithContent message) throws IOException {
        try (DeletedMessageWithContent closableMessage = message) {
            ZipArchiveEntry archiveEntry = createEntry(zipOutputStream, message);
            zipOutputStream.putArchiveEntry(archiveEntry);

            IOUtils.copy(message.getContent(), zipOutputStream);

            zipOutputStream.closeArchiveEntry();
        }
    }

    @VisibleForTesting
    ZipArchiveEntry createEntry(ZipArchiveOutputStream zipOutputStream,
                                        DeletedMessageWithContent fullMessage) throws IOException {
        DeletedMessage message = fullMessage.getDeletedMessage();
        MessageId messageId = message.getMessageId();

        ZipArchiveEntry archiveEntry = (ZipArchiveEntry) zipOutputStream.createArchiveEntry(new File(messageId.serialize()), messageId.serialize());
        archiveEntry.addExtraField(new MessageIdExtraField(messageId.serialize()));
        archiveEntry.addExtraField(new SizeExtraField(message.getSize()));

        return archiveEntry;
    }
}
