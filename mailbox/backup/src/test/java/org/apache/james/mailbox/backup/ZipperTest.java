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

import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_2;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_CONTENT_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_CONTENT_2;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_ID_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_ID_2;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.SIZE_1;
import static org.apache.james.mailbox.backup.ZipAssert.assertThatZip;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.james.junit.TemporaryFolderExtension;
import org.apache.james.junit.TemporaryFolderExtension.TemporaryFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.common.collect.ImmutableList;

@ExtendWith(TemporaryFolderExtension.class)
public class ZipperTest {
    private Zipper testee;
    private File destination;

    @BeforeEach
    void beforeEach(TemporaryFolder temporaryFolder) throws Exception {
        testee = new Zipper();
        destination = File.createTempFile("backup-test", ".zip", temporaryFolder.getTempDir());
    }

    @Test
    void archiveShouldWriteEmptyValidArchiveWhenNoMessage() throws Exception {
        testee.archive(ImmutableList.of(), new FileOutputStream(destination));

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatZip(zipFile).hasNoEntry();
        }
    }

    @Test
    void archiveShouldWriteOneMessageWhenOne() throws Exception {
        testee.archive(ImmutableList.of(MESSAGE_1), new FileOutputStream(destination));

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatZip(zipFile)
                .containsExactlyEntriesMatching(
                    zipEntryAssert -> zipEntryAssert
                        .hasName(MESSAGE_ID_1.serialize())
                        .hasStringContent(MESSAGE_CONTENT_1));
        }
    }

    @Test
    void archiveShouldWriteTwoMessagesWhenTwo() throws Exception {
        testee.archive(ImmutableList.of(MESSAGE_1, MESSAGE_2), new FileOutputStream(destination));

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatZip(zipFile)
                .containsExactlyEntriesMatching(
                    zipEntryAssert -> zipEntryAssert
                        .hasName(MESSAGE_ID_1.serialize())
                        .hasStringContent(MESSAGE_CONTENT_1),
                    zipEntryAssert -> zipEntryAssert
                        .hasName(MESSAGE_ID_2.serialize())
                        .hasStringContent(MESSAGE_CONTENT_2));
        }
    }

    @Test
    void archiveShouldOverwriteContent() throws Exception {
        testee.archive(ImmutableList.of(MESSAGE_1), new FileOutputStream(destination));
        testee.archive(ImmutableList.of(MESSAGE_2), new FileOutputStream(destination));

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatZip(zipFile)
                .containsExactlyEntriesMatching(
                    zipEntryAssert -> zipEntryAssert
                        .hasName(MESSAGE_ID_2.serialize())
                        .hasStringContent(MESSAGE_CONTENT_2));
        }
    }

    @Test
    void archiveShouldWriteSizeMetadata() throws Exception {
        testee.archive(ImmutableList.of(MESSAGE_1), new FileOutputStream(destination));

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatZip(zipFile)
                .containsExactlyEntriesMatching(
                    zipEntryAssert -> zipEntryAssert
                        .containsExactlyExtraFields(new SizeExtraField(SIZE_1)));
        }
    }
}
