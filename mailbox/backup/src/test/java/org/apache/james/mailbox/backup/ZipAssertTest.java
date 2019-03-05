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

import static org.apache.james.mailbox.backup.ZipAssert.EntryChecks.hasName;
import static org.apache.james.mailbox.backup.ZipAssert.assertThatZip;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;

import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.apache.james.junit.TemporaryFolderExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TemporaryFolderExtension.class)
public class ZipAssertTest {
    private static final String ENTRY_NAME = "entryName";
    private static final String ENTRY_NAME_2 = "entryName2";
    private static final String DIRECTORY_NAME = "folder/";
    private static final String STRING_ENTRY_CONTENT = "abcdefghijkl";
    private static final String STRING_ENTRY_CONTENT_2 = "mnopqrstuvwxyz";
    private static final byte[] ENTRY_CONTENT = STRING_ENTRY_CONTENT.getBytes(StandardCharsets.UTF_8);
    private static final byte[] ENTRY_CONTENT_2 = STRING_ENTRY_CONTENT_2.getBytes(StandardCharsets.UTF_8);
    private static final SizeExtraField EXTRA_FIELD = new SizeExtraField(42);

    private static final SimpleImmutableEntry<String, byte[]> ENTRY = new SimpleImmutableEntry<>(ENTRY_NAME, ENTRY_CONTENT);
    private static final SimpleImmutableEntry<String, byte[]> ENTRY_2 = new SimpleImmutableEntry<>(ENTRY_NAME_2, ENTRY_CONTENT_2);
    private File destination;

    @BeforeEach
    void beforeEach(TemporaryFolderExtension.TemporaryFolder temporaryFolder) throws Exception {
        destination = File.createTempFile("backup-test", ".zip", temporaryFolder.getTempDir());

        ExtraFieldUtils.register(SizeExtraField.class);
    }

    private void buildZipFile(SimpleImmutableEntry<String, byte[]>... entries) throws Exception {
        try (ZipArchiveOutputStream archiveOutputStream = new ZipArchiveOutputStream(destination)) {
           for (SimpleImmutableEntry<String, byte[]> entry : entries) {
                    ZipArchiveEntry archiveEntry = (ZipArchiveEntry) archiveOutputStream.createArchiveEntry(new File("any"), entry.getKey());
                    archiveOutputStream.putArchiveEntry(archiveEntry);
                    IOUtils.copy(new ByteArrayInputStream(entry.getValue()), archiveOutputStream);
                    archiveOutputStream.closeArchiveEntry();
            }
            archiveOutputStream.finish();
        }
    }

    @Test
    public void hasNoEntryShouldNotThrowWhenEmpty() throws Exception {
       buildZipFile();

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatCode(() -> assertThatZip(zipFile)
                .hasNoEntry())
                .doesNotThrowAnyException();
        }
    }

    @Test
    public void hasNoEntryShouldThrowWhenNotEmpty() throws Exception {
        buildZipFile(ENTRY);

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatThrownBy(() -> assertThatZip(zipFile)
                    .hasNoEntry())
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    public void containsExactlyEntriesMatchingShouldNotThrowWhenBothEmpty() throws Exception {
        buildZipFile();

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatCode(() -> assertThatZip(zipFile)
                    .containsOnlyEntriesMatching())
                .doesNotThrowAnyException();
        }
    }

    @Test
    public void containsExactlyEntriesMatchingShouldNotThrowWhenRightOrder() throws Exception {
        buildZipFile(ENTRY, ENTRY_2);

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatCode(() -> assertThatZip(zipFile)
                    .containsOnlyEntriesMatching(
                        hasName(ENTRY_NAME),
                        hasName(ENTRY_NAME_2)))
                .doesNotThrowAnyException();
        }
    }

    @Test
    public void hasNameShouldThrowWhenWrongName() throws Exception {
        buildZipFile(ENTRY);

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatThrownBy(() -> assertThatZip(zipFile)
                    .containsOnlyEntriesMatching(
                        hasName(ENTRY_NAME_2)))
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    public void isDirectoryShouldThrowWhenNotADirectory() throws Exception {
        buildZipFile(ENTRY);

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatThrownBy(() -> assertThatZip(zipFile)
                    .containsOnlyEntriesMatching(
                        hasName(ENTRY_NAME)
                            .isDirectory()))
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    public void isDirectoryShouldNotThrowWhenDirectory() throws Exception {
        try (ZipArchiveOutputStream archiveOutputStream = new ZipArchiveOutputStream(destination)) {

            ZipArchiveEntry archiveEntry = (ZipArchiveEntry) archiveOutputStream.createArchiveEntry(new Directory("any"), DIRECTORY_NAME);
            archiveOutputStream.putArchiveEntry(archiveEntry);
            archiveOutputStream.closeArchiveEntry();
            archiveOutputStream.finish();
        }

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatCode(() -> assertThatZip(zipFile)
                    .containsOnlyEntriesMatching(
                        hasName(DIRECTORY_NAME)
                            .isDirectory()))
                .doesNotThrowAnyException();
        }
    }

    @Test
    public void containsExactlyEntriesMatchingShouldNotThrowWhenWrongOrder() throws Exception {
        buildZipFile(ENTRY, ENTRY_2);

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatCode(() -> assertThatZip(zipFile)
                    .containsOnlyEntriesMatching(
                        hasName(ENTRY_NAME),
                        hasName(ENTRY_NAME_2)))
                .doesNotThrowAnyException();
        }
    }

    @Test
    public void containsExactlyEntriesMatchingShouldThrowWhenExpectingMoreEntries() throws Exception {
        buildZipFile(ENTRY, ENTRY_2);

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatThrownBy(() -> assertThatZip(zipFile)
                    .containsOnlyEntriesMatching(
                        hasName(ENTRY_NAME),
                        hasName(ENTRY_NAME_2),
                        hasName("extraEntry")))
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    public void containsExactlyEntriesMatchingShouldThrowWhenExpectingLessEntries() throws Exception {
        buildZipFile(ENTRY, ENTRY_2);

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatThrownBy(() -> assertThatZip(zipFile)
                    .containsOnlyEntriesMatching(
                        hasName(ENTRY_NAME)))
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    public void hasStringContentShouldNotThrowWhenIdentical() throws Exception {
        buildZipFile(ENTRY);

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatCode(() -> assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(ENTRY_NAME)
                        .hasStringContent(STRING_ENTRY_CONTENT)))
                .doesNotThrowAnyException();
        }
    }

    @Test
    public void hasStringContentShouldThrowWhenDifferent() throws Exception {
        buildZipFile(ENTRY);

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatThrownBy(() -> assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(ENTRY_NAME)
                        .hasStringContent(STRING_ENTRY_CONTENT_2)))
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    public void containsExactlyExtraFieldsShouldNotThrowWhenBothEmpty() throws Exception {
        buildZipFile(ENTRY);

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatCode(() -> assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(ENTRY_NAME)
                        .containsExtraFields()))
                .doesNotThrowAnyException();
        }
    }

    @Test
    public void containsExactlyExtraFieldsShouldThrowWhenMissingExpectedField() throws Exception {
        buildZipFile(ENTRY);

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatThrownBy(() -> assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(ENTRY_NAME)
                        .containsExtraFields(EXTRA_FIELD)))
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    public void containsExactlyExtraFieldsShouldNotThrowWhenUnexpectedField() throws Exception {
        try (ZipArchiveOutputStream archiveOutputStream = new ZipArchiveOutputStream(destination)) {

            ZipArchiveEntry archiveEntry = (ZipArchiveEntry) archiveOutputStream.createArchiveEntry(new File("any"), ENTRY_NAME);
            archiveEntry.addExtraField(EXTRA_FIELD);
            archiveOutputStream.putArchiveEntry(archiveEntry);
            IOUtils.copy(new ByteArrayInputStream(ENTRY_CONTENT), archiveOutputStream);
            archiveOutputStream.closeArchiveEntry();

            archiveOutputStream.finish();
        }

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatCode(() -> assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(ENTRY_NAME)
                        .containsExtraFields()))
                .doesNotThrowAnyException();
        }
    }

    @Test
    public void containsExactlyExtraFieldsShouldNotThrowWhenContainingExpectedExtraFields() throws Exception {
        try (ZipArchiveOutputStream archiveOutputStream = new ZipArchiveOutputStream(destination)) {

            ZipArchiveEntry archiveEntry = (ZipArchiveEntry) archiveOutputStream.createArchiveEntry(new File("any"), ENTRY_NAME);
            archiveEntry.addExtraField(EXTRA_FIELD);
            archiveOutputStream.putArchiveEntry(archiveEntry);
            IOUtils.copy(new ByteArrayInputStream(ENTRY_CONTENT), archiveOutputStream);
            archiveOutputStream.closeArchiveEntry();

            archiveOutputStream.finish();
        }

        try (ZipFile zipFile = new ZipFile(destination)) {
            assertThatCode(() -> assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(ENTRY_NAME)
                        .containsExtraFields(EXTRA_FIELD)))
                .doesNotThrowAnyException();
        }
    }
}
