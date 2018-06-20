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
package org.apache.james.mailbox.backup;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.github.fge.lambdas.Throwing;

public class Zipper implements Backup {
    public Zipper() {
        ExtraFieldUtils.register(SizeExtraField.class);
        ExtraFieldUtils.register(UidExtraField.class);
        ExtraFieldUtils.register(MessageIdExtraField.class);
        ExtraFieldUtils.register(MailboxIdExtraField.class);
        ExtraFieldUtils.register(InternalDateExtraField.class);
        ExtraFieldUtils.register(UidValidityExtraField.class);
    }

    @Override
    public void archive(List<Mailbox> mailboxes, Stream<MailboxMessage> messages, OutputStream destination) throws IOException {
        try (ZipArchiveOutputStream archiveOutputStream = new ZipArchiveOutputStream(destination)) {
            for (Mailbox mailbox: mailboxes) {
                storeInArchive(mailbox, archiveOutputStream);
            }
            messages.forEach(Throwing.<MailboxMessage>consumer(message -> {
                storeInArchive(message, archiveOutputStream);
            }).sneakyThrow());
            archiveOutputStream.finish();
        }
    }

    private void storeInArchive(Mailbox mailbox, ZipArchiveOutputStream archiveOutputStream) throws IOException {
        String name = mailbox.getName();
        ZipArchiveEntry archiveEntry = (ZipArchiveEntry) archiveOutputStream.createArchiveEntry(new Directory(name), name);

        archiveEntry.addExtraField(new MailboxIdExtraField(mailbox.getMailboxId().serialize()));
        archiveEntry.addExtraField(new UidValidityExtraField(mailbox.getUidValidity()));

        archiveOutputStream.putArchiveEntry(archiveEntry);
        archiveOutputStream.closeArchiveEntry();
    }

    private void storeInArchive(MailboxMessage message, ZipArchiveOutputStream archiveOutputStream) throws IOException {
        String entryId = message.getMessageId().serialize();
        ZipArchiveEntry archiveEntry = (ZipArchiveEntry) archiveOutputStream.createArchiveEntry(new File(entryId), entryId);

        archiveEntry.addExtraField(new SizeExtraField(message.getFullContentOctets()));
        archiveEntry.addExtraField(new UidExtraField(message.getUid().asLong()));
        archiveEntry.addExtraField(new MessageIdExtraField(message.getMessageId().serialize()));
        archiveEntry.addExtraField(new MailboxIdExtraField(message.getMailboxId().serialize()));
        archiveEntry.addExtraField(new InternalDateExtraField(message.getInternalDate()));

        archiveOutputStream.putArchiveEntry(archiveEntry);
        IOUtils.copy(message.getFullContent(), archiveOutputStream);
        archiveOutputStream.closeArchiveEntry();
    }
}
