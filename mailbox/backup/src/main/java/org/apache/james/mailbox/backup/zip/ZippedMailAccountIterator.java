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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.mailbox.backup.MailArchiveEntry;
import org.apache.james.mailbox.backup.MailArchiveIterator;
import org.apache.james.mailbox.backup.MailboxWithAnnotationsArchiveEntry;
import org.apache.james.mailbox.backup.SerializedMailboxId;
import org.apache.james.mailbox.backup.UnknownArchiveEntry;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class ZippedMailAccountIterator implements MailArchiveIterator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZippedMailAccountIterator.class);
    private static final List<MailboxAnnotation> NO_ANNOTATION = ImmutableList.of();
    private final ZipEntryIterator zipEntryIterator;
    private Optional<MailboxWithAnnotationsArchiveEntry> currentMailBox;
    private Optional<ZipEntry> next;

    public ZippedMailAccountIterator(ZipEntryIterator zipEntryIterator) {
        this.zipEntryIterator = zipEntryIterator;
        next = Optional.ofNullable(zipEntryIterator.next());
    }

    @Override
    public void close() throws IOException {
        zipEntryIterator.close();
    }

    @Override
    public boolean hasNext() {
        return next.isPresent();
    }

    @Override
    public MailArchiveEntry next() {
        return next.map(this::doNext).orElseThrow(() -> new NoSuchElementException());
    }

    private MailArchiveEntry doNext(ZipEntry currentElement) {
        next = Optional.ofNullable(zipEntryIterator.next());
        try {
            return getMailArchiveEntry(currentElement);
        } catch (Exception e) {
            LOGGER.error("Error when reading archive on entry : " + currentElement.getName(), e);
            next = Optional.empty();
            return new UnknownArchiveEntry(currentElement.getName());
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

    private MailArchiveEntry from(ZipEntry current, ZipEntryType currentEntryType) throws ZipException {
        switch (currentEntryType) {
            case MAILBOX:
                return fromMailboxEntry(current);
            default:
                return new UnknownArchiveEntry(current.getName());
        }
    }
}
