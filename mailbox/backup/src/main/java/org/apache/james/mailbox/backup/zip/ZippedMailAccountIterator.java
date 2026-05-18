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

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import jakarta.mail.Flags;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.mailbox.backup.MailArchiveEntry;
import org.apache.james.mailbox.backup.MailArchiveIterator;
import org.apache.james.mailbox.backup.MailboxWithAnnotationsArchiveEntry;
import org.apache.james.mailbox.backup.MessageArchiveEntry;
import org.apache.james.mailbox.backup.SerializedMailboxId;
import org.apache.james.mailbox.backup.SerializedMessageId;
import org.apache.james.mailbox.backup.UnknownArchiveEntry;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class ZippedMailAccountIterator implements MailArchiveIterator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZippedMailAccountIterator.class);
    private static final List<MailboxAnnotation> NO_ANNOTATION = ImmutableList.of();
    private final ZipInputStream zipInputStream;
    private Optional<ZipEntry> currentEntry;
    private boolean closed;

    public ZippedMailAccountIterator(ZipInputStream zipInputStream) {
        this.zipInputStream = zipInputStream;
        this.currentEntry = Optional.empty();
        this.closed = false;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        zipInputStream.close();
    }

    @Override
    public boolean hasNext() {
        if (closed) {
            return false;
        }
        if (currentEntry.isPresent()) {
            return true;
        }
        try {
            closeCurrentEntryIfNeeded();
            ZipEntry nextEntry = zipInputStream.getNextEntry();
            currentEntry = Optional.ofNullable(nextEntry);
            return currentEntry.isPresent();
        } catch (IOException e) {
            LOGGER.error("Error reading next zip entry", e);
            return false;
        }
    }

    @Override
    public MailArchiveEntry next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        ZipEntry entry = currentEntry.get();
        currentEntry = Optional.empty();
        try {
            return getMailArchiveEntry(entry);
        } catch (Exception e) {
            LOGGER.error("Error when reading archive on entry : " + entry.getName(), e);
            return new UnknownArchiveEntry(entry.getName());
        }
    }

    private MailArchiveEntry getMailArchiveEntry(ZipEntry currentElement) throws Exception {
        Optional<ZipEntryType> entryType = ExtraFieldExtractor.getEntryType(currentElement);
        return entryType
            .map(Throwing.<ZipEntryType, MailArchiveEntry>function(type ->
                from(currentElement, type)).sneakyThrow()
            )
            .orElseGet(() -> new UnknownArchiveEntry(currentElement.getName()));
    }

    private void closeCurrentEntryIfNeeded() {
        try {
            zipInputStream.closeEntry();
        } catch (IOException e) {
            LOGGER.warn("Error closing zip entry", e);
        }
    }

    private Optional<SerializedMailboxId> getMailBoxId(ZipEntry entry) throws ZipException {
        return ExtraFieldExtractor.getStringExtraField(MailboxIdExtraField.ID_AM, entry)
            .map(SerializedMailboxId::new);
    }

    private String getMailboxName(ZipEntry current) {
        return StringUtils.chop(current.getName());
    }

    private MailArchiveEntry fromMailboxEntry(ZipEntry current) throws ZipException {
        return new MailboxWithAnnotationsArchiveEntry(getMailboxName(current), getMailBoxId(current).get(), NO_ANNOTATION);
    }

    private MailArchiveEntry fromMessageEntry(ZipEntry current) throws ZipException {
        SerializedMessageId messageId = ExtraFieldExtractor.getStringExtraField(MessageIdExtraField.ID_AL, current)
            .map(SerializedMessageId::new)
            .orElseThrow(() -> new ZipException("Message entry missing messageId"));
        SerializedMailboxId mailboxId = getMailBoxId(current)
            .orElseThrow(() -> new ZipException("Message entry missing mailboxId"));
        long size = ExtraFieldExtractor.getLongExtraField(SizeExtraField.ID_AJ, current).orElse(0L);
        Date internalDate = ExtraFieldExtractor.getDateExtraField(InternalDateExtraField.ID_AO, current).orElse(new Date());
        Flags flags = ExtraFieldExtractor.getFlagsExtraField(FlagsExtraField.ID_AP, current).orElse(new Flags());

        return new MessageArchiveEntry(messageId, mailboxId, size, internalDate, flags, zipInputStream);
    }

    private MailArchiveEntry from(ZipEntry current, ZipEntryType currentEntryType) throws ZipException {
        switch (currentEntryType) {
            case MAILBOX:
                return fromMailboxEntry(current);
            case MESSAGE:
                return fromMessageEntry(current);
            default:
                return new UnknownArchiveEntry(current.getName());
        }
    }
}
