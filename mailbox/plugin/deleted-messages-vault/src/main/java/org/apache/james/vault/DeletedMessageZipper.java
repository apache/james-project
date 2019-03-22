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
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.backup.MessageIdExtraField;
import org.apache.james.mailbox.backup.SizeExtraField;
import org.apache.james.mailbox.model.MessageId;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ThrowingConsumer;
import com.google.common.annotations.VisibleForTesting;

public class DeletedMessageZipper {
    interface DeletedMessageContentLoader {
        InputStream load(DeletedMessage deletedMessage);
    }

    DeletedMessageZipper() {
        ExtraFieldUtils.register(MessageIdExtraField.class);
        ExtraFieldUtils.register(SizeExtraField.class);
    }

    public void zip(DeletedMessageContentLoader contentLoader, Stream<DeletedMessage> deletedMessages, OutputStream outputStream) throws IOException {
        try (ZipArchiveOutputStream zipOutputStream = newZipArchiveOutputStream(outputStream)) {
            ThrowingConsumer<DeletedMessage> putInZip = message -> putMessageToEntry(zipOutputStream, message, contentLoader);

            deletedMessages.forEach(Throwing.consumer(putInZip).sneakyThrow());

            zipOutputStream.finish();
        }
    }

    @VisibleForTesting
    ZipArchiveOutputStream newZipArchiveOutputStream(OutputStream outputStream) {
        return new ZipArchiveOutputStream(outputStream);
    }

    @VisibleForTesting
    void putMessageToEntry(ZipArchiveOutputStream zipOutputStream, DeletedMessage message, DeletedMessageContentLoader contentLoader) throws IOException {
        try (InputStream content = contentLoader.load(message)) {
            zipOutputStream.putArchiveEntry(createEntry(zipOutputStream, message));

            IOUtils.copy(content, zipOutputStream);

            zipOutputStream.closeArchiveEntry();
        }
    }

    @VisibleForTesting
    ZipArchiveEntry createEntry(ZipArchiveOutputStream zipOutputStream, DeletedMessage message) throws IOException {
        MessageId messageId = message.getMessageId();

        ZipArchiveEntry archiveEntry = (ZipArchiveEntry) zipOutputStream.createArchiveEntry(new File(messageId.serialize()), messageId.serialize());
        archiveEntry.addExtraField(new MessageIdExtraField(messageId));
        archiveEntry.addExtraField(new SizeExtraField(message.getSize()));

        return archiveEntry;
    }
}
