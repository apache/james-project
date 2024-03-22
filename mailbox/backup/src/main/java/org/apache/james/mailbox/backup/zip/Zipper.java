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
package org.apache.james.mailbox.backup.zip;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.backup.ArchiveService;
import org.apache.james.mailbox.backup.Directory;
import org.apache.james.mailbox.backup.MailboxWithAnnotations;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MessageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Charsets;

public class Zipper implements ArchiveService {

    public static final String ANNOTATION_DIRECTORY = "annotations";
    private static final boolean AUTO_FLUSH = true;
    private static final Logger LOGGER = LoggerFactory.getLogger(Zipper.class);

    @Inject
    public Zipper() {
        ExtraFieldUtils.register(SizeExtraField.class);
        ExtraFieldUtils.register(UidExtraField.class);
        ExtraFieldUtils.register(MessageIdExtraField.class);
        ExtraFieldUtils.register(MailboxIdExtraField.class);
        ExtraFieldUtils.register(InternalDateExtraField.class);
        ExtraFieldUtils.register(UidValidityExtraField.class);
        ExtraFieldUtils.register(FlagsExtraField.class);
        ExtraFieldUtils.register(EntryTypeExtraField.class);
    }

    @Override
    public void archive(List<MailboxWithAnnotations> mailboxes, Stream<MessageResult> messages, OutputStream destination) throws IOException {
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

    private void storeMessages(Stream<MessageResult> messages, ZipArchiveOutputStream archiveOutputStream) throws IOException {
        messages.forEach(Throwing.<MessageResult>consumer(message ->
            storeInArchive(message, archiveOutputStream)
        ).sneakyThrow());
    }

    private void storeInArchive(MailboxWithAnnotations mailboxWithAnnotations, ZipArchiveOutputStream archiveOutputStream) throws IOException {
        Mailbox mailbox = mailboxWithAnnotations.mailbox;
        List<MailboxAnnotation> annotations = mailboxWithAnnotations.annotations;

        String name = mailbox.getName();
        ZipArchiveEntry archiveEntry = (ZipArchiveEntry) archiveOutputStream.createArchiveEntry(new Directory(name), name);

        archiveEntry.addExtraField(EntryTypeExtraField.TYPE_MAILBOX);
        archiveEntry.addExtraField(new MailboxIdExtraField(mailbox.getMailboxId().serialize()));
        archiveEntry.addExtraField(new UidValidityExtraField(mailbox.getUidValidity().asLong()));

        archiveOutputStream.putArchiveEntry(archiveEntry);
        archiveOutputStream.closeArchiveEntry();

        storeAllAnnotationsInArchive(archiveOutputStream, annotations, name);
    }

    private void storeAllAnnotationsInArchive(ZipArchiveOutputStream archiveOutputStream, List<MailboxAnnotation> annotations, String name) throws IOException {
        if (!annotations.isEmpty()) {
            String annotationsDirectoryPath = name + "/" + ANNOTATION_DIRECTORY;
            ZipArchiveEntry annotationDirectory = (ZipArchiveEntry) archiveOutputStream.createArchiveEntry(
                new Directory(annotationsDirectoryPath), annotationsDirectoryPath);
            annotationDirectory.addExtraField(EntryTypeExtraField.TYPE_MAILBOX_ANNOTATION_DIR);
            archiveOutputStream.putArchiveEntry(annotationDirectory);
            archiveOutputStream.closeArchiveEntry();
            annotations.forEach(Throwing.consumer(annotation ->
                storeInArchive(annotation, annotationsDirectoryPath, archiveOutputStream)));
        }
    }

    private void storeInArchive(MailboxAnnotation annotation, String directory, ZipArchiveOutputStream archiveOutputStream) throws IOException {
        String entryId = directory + "/" + annotation.getKey().asString();
        ZipArchiveEntry archiveEntry = (ZipArchiveEntry) archiveOutputStream.createArchiveEntry(new File(entryId), entryId);
        archiveEntry.addExtraField(EntryTypeExtraField.TYPE_MAILBOX_ANNOTATION);
        archiveOutputStream.putArchiveEntry(archiveEntry);

        annotation.getValue().ifPresent(value -> {
            try (PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(archiveOutputStream, Charsets.UTF_8), AUTO_FLUSH)) {
                printWriter.print(value);
            }
        });

        archiveOutputStream.closeArchiveEntry();
    }

    private void storeInArchive(MessageResult message, ZipArchiveOutputStream archiveOutputStream) throws IOException {
        String entryId = message.getMessageId().serialize();
        ZipArchiveEntry archiveEntry = createMessageZipArchiveEntry(message, archiveOutputStream, entryId);

        archiveOutputStream.putArchiveEntry(archiveEntry);
        try {
            Content content = message.getFullContent();
            try (InputStream stream = content.getInputStream()) {
                IOUtils.copy(stream, archiveOutputStream);
            }
        } catch (MailboxException e) {
            LOGGER.error("Error while storing message in archive", e);
        }

        archiveOutputStream.closeArchiveEntry();
    }

    private ZipArchiveEntry createMessageZipArchiveEntry(MessageResult message, ZipArchiveOutputStream archiveOutputStream, String entryId) throws IOException {
        ZipArchiveEntry archiveEntry = (ZipArchiveEntry) archiveOutputStream.createArchiveEntry(new File(entryId), entryId);

        archiveEntry.addExtraField(EntryTypeExtraField.TYPE_MESSAGE);
        archiveEntry.addExtraField(new SizeExtraField(message.getSize()));
        archiveEntry.addExtraField(new UidExtraField(message.getUid().asLong()));
        archiveEntry.addExtraField(new MessageIdExtraField(message.getMessageId().serialize()));
        archiveEntry.addExtraField(new MailboxIdExtraField(message.getMailboxId().serialize()));
        archiveEntry.addExtraField(new InternalDateExtraField(message.getInternalDate()));
        archiveEntry.addExtraField(new FlagsExtraField(message.getFlags()));
        return archiveEntry;
    }
}
