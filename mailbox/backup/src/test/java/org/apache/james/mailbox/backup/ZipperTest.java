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

import static org.apache.james.mailbox.backup.MailboxMessageFixture.MAILBOX_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MAILBOX_1_SUB_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MAILBOX_2;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MAILBOX_ID_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_2;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_CONTENT_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_CONTENT_2;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_ID_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_ID_2;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_UID_1_VALUE;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.SIZE_1;
import static org.apache.james.mailbox.backup.ZipAssert.assertThatZip;
import static org.apache.james.mailbox.backup.ZipAssert.EntryChecks.hasName;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class ZipperTest {
    private static final List<Mailbox> NO_MAILBOXES = ImmutableList.of();
    private Zipper testee;
    private ByteArrayOutputStream output;

    @BeforeEach
    void beforeEach() {
        testee = new Zipper();
        output = new ByteArrayOutputStream();
    }

    @Test
    void archiveShouldWriteEmptyValidArchiveWhenNoMessage() throws Exception {
        testee.archive(NO_MAILBOXES, Stream.of(), output);
        try (ZipFile zipFile = new ZipFile(toSeekableByteChannel(output))) {
            assertThatZip(zipFile).hasNoEntry();
        }
    }

    @Test
    void archiveShouldWriteOneMessageWhenOne() throws Exception {
        testee.archive(NO_MAILBOXES, Stream.of(MESSAGE_1), output);

        try (ZipFile zipFile = new ZipFile(toSeekableByteChannel(output))) {
            assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(MESSAGE_ID_1.serialize())
                        .hasStringContent(MESSAGE_CONTENT_1));
        }
    }

    @Test
    void archiveShouldWriteTwoMessagesWhenTwo() throws Exception {
        testee.archive(NO_MAILBOXES, Stream.of(MESSAGE_1, MESSAGE_2), output);

        try (ZipFile zipFile = new ZipFile(toSeekableByteChannel(output))) {
            assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(MESSAGE_ID_1.serialize())
                        .hasStringContent(MESSAGE_CONTENT_1),
                    hasName(MESSAGE_ID_2.serialize())
                        .hasStringContent(MESSAGE_CONTENT_2));
        }
    }

    @Test
    void archiveShouldWriteMetadata() throws Exception {
        testee.archive(NO_MAILBOXES, Stream.of(MESSAGE_1), output);

        try (ZipFile zipFile = new ZipFile(toSeekableByteChannel(output))) {
            assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(MESSAGE_ID_1.serialize())
                        .containsExtraFields(new SizeExtraField(SIZE_1))
                        .containsExtraFields(new UidExtraField(MESSAGE_UID_1_VALUE))
                        .containsExtraFields(new MessageIdExtraField(MESSAGE_ID_1.serialize()))
                        .containsExtraFields(new InternalDateExtraField(MESSAGE_1.getInternalDate())));
        }
    }

    @Test
    void archiveShouldWriteOneMailboxWhenPresent() throws Exception {
        testee.archive(ImmutableList.of(MAILBOX_1), Stream.of(), output);

        try (ZipFile zipFile = new ZipFile(toSeekableByteChannel(output))) {
            assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(MAILBOX_1.getName() + "/")
                        .isDirectory());
        }
    }

    @Test
    void archiveShouldWriteMailboxesWhenPresent() throws Exception {
        testee.archive(ImmutableList.of(MAILBOX_1, MAILBOX_2), Stream.of(), output);

        try (ZipFile zipFile = new ZipFile(toSeekableByteChannel(output))) {
            assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(MAILBOX_1.getName() + "/")
                        .isDirectory(),
                    hasName(MAILBOX_2.getName() + "/")
                        .isDirectory());
        }
    }

    @Test
    void archiveShouldWriteMailboxHierarchyWhenPresent() throws Exception {
        testee.archive(ImmutableList.of(MAILBOX_1, MAILBOX_1_SUB_1, MAILBOX_2), Stream.of(), output);

        try (ZipFile zipFile = new ZipFile(toSeekableByteChannel(output))) {
            assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(MAILBOX_1.getName() + "/")
                        .isDirectory(),
                    hasName(MAILBOX_1_SUB_1.getName() + "/")
                        .isDirectory(),
                    hasName(MAILBOX_2.getName() + "/")
                        .isDirectory());
        }
    }

    @Test
    void archiveShouldWriteMailboxHierarchyWhenMissingParent() throws Exception {
        testee.archive(ImmutableList.of(MAILBOX_1_SUB_1, MAILBOX_2), Stream.of(), output);

        try (ZipFile zipFile = new ZipFile(toSeekableByteChannel(output))) {
            assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(MAILBOX_1_SUB_1.getName() + "/")
                        .isDirectory(),
                    hasName(MAILBOX_2.getName() + "/")
                        .isDirectory());
        }
    }

    @Test
    void archiveShouldWriteMailboxMetadataWhenPresent() throws Exception {
        testee.archive(ImmutableList.of(MAILBOX_1), Stream.of(), output);

        try (ZipFile zipFile = new ZipFile(toSeekableByteChannel(output))) {
            assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(MAILBOX_1.getName() + "/")
                        .containsExtraFields(new MailboxIdExtraField(MAILBOX_1.getMailboxId().serialize())));
        }
    }

    private SeekableInMemoryByteChannel toSeekableByteChannel(ByteArrayOutputStream output) {
        return new SeekableInMemoryByteChannel(output.toByteArray());
    }
}
