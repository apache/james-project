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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Charsets;

public class Zipper implements Backup {

    private static final String ANNOTATION_DIRECTORY = "annotations";
    private static final boolean AUTO_FLUSH = true;

    public Zipper() {
        ExtraFieldUtils.register(SizeExtraField.class);
        ExtraFieldUtils.register(UidExtraField.class);
        ExtraFieldUtils.register(MessageIdExtraField.class);
        ExtraFieldUtils.register(MailboxIdExtraField.class);
        ExtraFieldUtils.register(InternalDateExtraField.class);
        ExtraFieldUtils.register(UidValidityExtraField.class);
        ExtraFieldUtils.register(FlagsExtraField.class);
    }

    @Override
    public void archive(List<MailboxWithAnnotations> mailboxes, Stream<MailboxMessage> messages, OutputStream destination) throws IOException {
        try (ZipArchiveOutputStream archiveOutputStream = new ZipArchiveOutputStream(destination)) {
            storeMailboxes(mailboxes, archiveOutputStream);
            storeMessages(messages, archiveOutputStream);
            archiveOutputStream.finish();
        }
    }

    private void storeMailboxes(List<MailboxWithAnnotations> mailboxes, ZipArchiveOutputStream archiveOutputStream) throws IOException {
        mailboxes.forEach(Throwing.<MailboxWithAnnotations>consumer(mailbox ->
            storeInArchive(mailbox, archiveOutputStream)
        ).sneakyThrow());
    }

    private void storeMessages(Stream<MailboxMessage> messages, ZipArchiveOutputStream archiveOutputStream) throws IOException {
        messages.forEach(Throwing.<MailboxMessage>consumer(message ->
                storeInArchive(message, archiveOutputStream)
        ).sneakyThrow());
    }

    private void storeInArchive(MailboxWithAnnotations mailboxWithAnnotations, ZipArchiveOutputStream archiveOutputStream) throws IOException {
        Mailbox mailbox = mailboxWithAnnotations.mailbox;
        List<MailboxAnnotation> annotations = mailboxWithAnnotations.annotations;

        String name = mailbox.getName();
        ZipArchiveEntry archiveEntry = (ZipArchiveEntry) archiveOutputStream.createArchiveEntry(new Directory(name), name);

        archiveEntry.addExtraField(new MailboxIdExtraField(mailbox.getMailboxId().serialize()));
        archiveEntry.addExtraField(new UidValidityExtraField(mailbox.getUidValidity()));

        archiveOutputStream.putArchiveEntry(archiveEntry);
        archiveOutputStream.closeArchiveEntry();

        storeAllAnnotationsInArchive(archiveOutputStream, annotations, name);
    }

    private void storeAllAnnotationsInArchive(ZipArchiveOutputStream archiveOutputStream, List<MailboxAnnotation> annotations, String name) throws IOException {
        if (!annotations.isEmpty()) {
            String annotationsDirectoryPath = name + "/" + ANNOTATION_DIRECTORY;
            ZipArchiveEntry annotationDirectory = (ZipArchiveEntry) archiveOutputStream.createArchiveEntry(
                new Directory(annotationsDirectoryPath), annotationsDirectoryPath);
            archiveOutputStream.putArchiveEntry(annotationDirectory);
            archiveOutputStream.closeArchiveEntry();
            annotations.forEach(Throwing.consumer(annotation ->
                storeInArchive(annotation, annotationsDirectoryPath, archiveOutputStream)));
        }
    }

    private void storeInArchive(MailboxAnnotation annotation, String directory, ZipArchiveOutputStream archiveOutputStream) throws IOException {
        String entryId = directory + "/" + annotation.getKey().asString();
        ZipArchiveEntry archiveEntry = (ZipArchiveEntry) archiveOutputStream.createArchiveEntry(new File(entryId), entryId);
        archiveOutputStream.putArchiveEntry(archiveEntry);

        annotation.getValue().ifPresent(value -> {
            try (PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(archiveOutputStream, Charsets.UTF_8), AUTO_FLUSH)) {
                printWriter.print(value);
            }
        });

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
        archiveEntry.addExtraField(new FlagsExtraField(message.createFlags()));

        archiveOutputStream.putArchiveEntry(archiveEntry);
        IOUtils.copy(message.getFullContent(), archiveOutputStream);
        archiveOutputStream.closeArchiveEntry();
    }
}
