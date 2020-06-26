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
import static org.apache.james.mailbox.backup.ZipAssertTest.ZipEntryWithContent.entryBuilder;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.UUID;

import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.apache.james.junit.TemporaryFolderExtension;
import org.apache.james.mailbox.backup.ZipAssert.EntryChecks;
import org.apache.james.mailbox.backup.zip.SizeExtraField;
import org.apache.james.mailbox.backup.zip.UidExtraField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.common.collect.ImmutableList;

@ExtendWith(TemporaryFolderExtension.class)
class ZipAssertTest {

    static class ZipEntryWithContent {

        static class Builder {
            @FunctionalInterface
            interface RequireName {
                RequireContent name(String name);
            }

            @FunctionalInterface
            interface RequireContent {
                ReadyToBuild content(byte[] content);
            }

            static class ReadyToBuild {
                private final String name;
                private final byte[] content;
                private final ImmutableList.Builder<ZipExtraField> extraFieldBuilder;

                ReadyToBuild(String name, byte[] content) {
                    this.name = name;
                    this.content = content;
                    this.extraFieldBuilder = new ImmutableList.Builder<>();
                }

                ReadyToBuild addField(ZipExtraField zipExtraField) {
                    extraFieldBuilder.add(zipExtraField);
                    return this;
                }

                public ZipEntryWithContent build() {
                    return new ZipEntryWithContent(name, new ByteArrayInputStream(content), extraFieldBuilder.build());
                }
            }
        }

        static Builder.RequireName entryBuilder() {
            return name -> content -> new Builder.ReadyToBuild(name, content);
        }

        private final String name;
        private final InputStream content;
        private final ImmutableList<ZipExtraField> extraFields;

        ZipEntryWithContent(String name, InputStream content, ImmutableList<ZipExtraField> extraFields) {
            this.name = name;
            this.content = content;
            this.extraFields = extraFields;
        }
    }

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

    private static ZipFile zipFile(File destination, ZipEntryWithContent...entries) throws Exception {
        try (ZipArchiveOutputStream archiveOutputStream = new ZipArchiveOutputStream(destination)) {
            for (ZipEntryWithContent entry : entries) {
                ZipArchiveEntry archiveEntry = (ZipArchiveEntry) archiveOutputStream.createArchiveEntry(new File(UUID.randomUUID().toString()), entry.name);
                entry.extraFields
                    .forEach(archiveEntry::addExtraField);
                archiveOutputStream.putArchiveEntry(archiveEntry);
                IOUtils.copy(entry.content, archiveOutputStream);
                archiveOutputStream.closeArchiveEntry();
            }
            archiveOutputStream.finish();
        }

        return new ZipFile(destination);
    }

    private File destination;
    private File destination2;

    @BeforeEach
    void beforeEach(TemporaryFolderExtension.TemporaryFolder temporaryFolder) throws Exception {
        destination = File.createTempFile("backup-test", ".zip", temporaryFolder.getTempDir());
        destination2 = File.createTempFile("backup-test2", ".zip", temporaryFolder.getTempDir());

        ExtraFieldUtils.register(SizeExtraField.class);
        ExtraFieldUtils.register(UidExtraField.class);
    }

    @SafeVarargs
    private ZipFile buildZipFile(SimpleImmutableEntry<String, byte[]>... entries) throws Exception {
        try (ZipArchiveOutputStream archiveOutputStream = new ZipArchiveOutputStream(destination)) {
            for (SimpleImmutableEntry<String, byte[]> entry : entries) {
                ZipArchiveEntry archiveEntry = (ZipArchiveEntry) archiveOutputStream.createArchiveEntry(new File("any"), entry.getKey());
                archiveOutputStream.putArchiveEntry(archiveEntry);
                IOUtils.copy(new ByteArrayInputStream(entry.getValue()), archiveOutputStream);
                archiveOutputStream.closeArchiveEntry();
            }
            archiveOutputStream.finish();
        }

        return new ZipFile(destination);
    }

    @Test
    void hasNoEntryShouldNotThrowWhenEmpty() throws Exception {
        try (ZipFile zipFile = buildZipFile()) {
            assertThatCode(() -> assertThatZip(zipFile)
                .hasNoEntry())
                .doesNotThrowAnyException();
        }
    }

    @Test
    void hasNoEntryShouldThrowWhenNotEmpty() throws Exception {
        try (ZipFile zipFile = buildZipFile(ENTRY)) {
            assertThatThrownBy(() -> assertThatZip(zipFile)
                    .hasNoEntry())
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void containsOnlyEntriesMatchingShouldNotThrowWhenBothEmpty() throws Exception {
        try (ZipFile zipFile = buildZipFile()) {
            assertThatCode(() -> assertThatZip(zipFile)
                    .containsOnlyEntriesMatching())
                .doesNotThrowAnyException();
        }
    }

    @Test
    void containsOnlyEntriesMatchingShouldNotThrowWhenRightOrder() throws Exception {
        try (ZipFile zipFile = buildZipFile(ENTRY, ENTRY_2)) {
            assertThatCode(() -> assertThatZip(zipFile)
                    .containsOnlyEntriesMatching(
                        hasName(ENTRY_NAME),
                        hasName(ENTRY_NAME_2)))
                .doesNotThrowAnyException();
        }
    }

    @Test
    void hasNameShouldThrowWhenWrongName() throws Exception {
        try (ZipFile zipFile = buildZipFile(ENTRY)) {
            assertThatThrownBy(() -> assertThatZip(zipFile)
                    .containsOnlyEntriesMatching(
                        hasName(ENTRY_NAME_2)))
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void isDirectoryShouldThrowWhenNotADirectory() throws Exception {
        try (ZipFile zipFile = buildZipFile(ENTRY)) {
            assertThatThrownBy(() -> assertThatZip(zipFile)
                    .containsOnlyEntriesMatching(
                        hasName(ENTRY_NAME)
                            .isDirectory()))
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void isDirectoryShouldNotThrowWhenDirectory() throws Exception {
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
    void containsOnlyEntriesMatchingShouldNotThrowWhenWrongOrder() throws Exception {
        try (ZipFile zipFile = buildZipFile(ENTRY, ENTRY_2)) {
            assertThatCode(() -> assertThatZip(zipFile)
                    .containsOnlyEntriesMatching(
                        hasName(ENTRY_NAME_2),
                        hasName(ENTRY_NAME)))
                .doesNotThrowAnyException();
        }
    }

    @Test
    void containsOnlyEntriesMatchingShouldThrowWhenExpectingMoreEntries() throws Exception {
        try (ZipFile zipFile = buildZipFile(ENTRY, ENTRY_2)) {
            assertThatThrownBy(() -> assertThatZip(zipFile)
                    .containsOnlyEntriesMatching(
                        hasName(ENTRY_NAME),
                        hasName(ENTRY_NAME_2),
                        hasName("extraEntry")))
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void containsOnlyEntriesMatchingShouldThrowWhenExpectingLessEntries() throws Exception {
        try (ZipFile zipFile = buildZipFile(ENTRY, ENTRY_2)) {
            assertThatThrownBy(() -> assertThatZip(zipFile)
                    .containsOnlyEntriesMatching(
                        hasName(ENTRY_NAME)))
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void containsExactlyEntriesMatchingShouldNotThrowWhenBothEmpty() throws Exception {
        try (ZipFile zipFile = buildZipFile()) {
            assertThatCode(() -> assertThatZip(zipFile)
                .containsExactlyEntriesMatching())
                .doesNotThrowAnyException();
        }
    }

    @Test
    void containsExactlyEntriesMatchingShouldThrowWhenWrongOrder() throws Exception {
        try (ZipFile zipFile = buildZipFile(ENTRY, ENTRY_2)) {
            assertThatThrownBy(() -> assertThatZip(zipFile)
                .containsExactlyEntriesMatching(
                    hasName(ENTRY_NAME_2),
                    hasName(ENTRY_NAME)))
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void containsExactlyEntriesMatchingShouldNotThrowWhenRightOrder() throws Exception {
        try (ZipFile zipFile = buildZipFile(ENTRY, ENTRY_2)) {
            assertThatCode(() -> assertThatZip(zipFile)
                .containsExactlyEntriesMatching(
                    hasName(ENTRY_NAME),
                    hasName(ENTRY_NAME_2)))
                .doesNotThrowAnyException();
        }
    }

    @Test
    void containsExactlyEntriesMatchingShouldThrowWhenExpectingMoreEntries() throws Exception {
        try (ZipFile zipFile = buildZipFile(ENTRY, ENTRY_2)) {
            assertThatThrownBy(() -> assertThatZip(zipFile)
                .containsExactlyEntriesMatching(
                    hasName(ENTRY_NAME),
                    hasName(ENTRY_NAME_2),
                    hasName("extraEntry")))
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void containsExactlyEntriesMatchingShouldThrowWhenExpectingLessEntries() throws Exception {
        try (ZipFile zipFile = buildZipFile(ENTRY, ENTRY_2)) {
            assertThatThrownBy(() -> assertThatZip(zipFile)
                .containsExactlyEntriesMatching(
                    hasName(ENTRY_NAME)))
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void hasStringContentShouldNotThrowWhenIdentical() throws Exception {
        try (ZipFile zipFile = buildZipFile(ENTRY)) {
            assertThatCode(() -> assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(ENTRY_NAME)
                        .hasStringContent(STRING_ENTRY_CONTENT)))
                .doesNotThrowAnyException();
        }
    }

    @Test
    void hasStringContentShouldThrowWhenDifferent() throws Exception {
        try (ZipFile zipFile = buildZipFile(ENTRY)) {
            assertThatThrownBy(() -> assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(ENTRY_NAME)
                        .hasStringContent(STRING_ENTRY_CONTENT_2)))
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void containsOnlyExtraFieldsShouldNotThrowWhenBothEmpty() throws Exception {
        try (ZipFile zipFile = buildZipFile(ENTRY)) {
            assertThatCode(() -> assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(ENTRY_NAME)
                        .containsExtraFields()))
                .doesNotThrowAnyException();
        }
    }

    @Test
    void containsOnlyExtraFieldsShouldThrowWhenMissingExpectedField() throws Exception {
        try (ZipFile zipFile = buildZipFile(ENTRY)) {
            assertThatThrownBy(() -> assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(ENTRY_NAME)
                        .containsExtraFields(EXTRA_FIELD)))
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void containsOnlyExtraFieldsShouldNotThrowWhenUnexpectedField() throws Exception {
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
    void containsOnlyExtraFieldsShouldNotThrowWhenContainingExpectedExtraFields() throws Exception {
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

    @Nested
    class HasSameContentTest {

        @Test
        void hasSameContentShouldThrowWhenExpectedZipFileIsNull() throws Exception {
            try (ZipFile assertedZipFile = zipFile(destination, entryBuilder().name(ENTRY_NAME).content(ENTRY_CONTENT).build())) {
                ZipFile expectedZipFile = null;
                assertThatThrownBy(() -> assertThatZip(assertedZipFile)
                    .hasSameContentWith(expectedZipFile))
                    .isInstanceOf(AssertionError.class);
            }
        }

        @Test
        void hasSameContentShouldThrowWhenAssertedZipFileIsNull() throws Exception {
            try (ZipFile expectedZipFile = zipFile(destination, entryBuilder().name(ENTRY_NAME).content(ENTRY_CONTENT).build())) {
                ZipFile assertedZipFile = null;
                assertThatThrownBy(() -> assertThatZip(assertedZipFile)
                    .hasSameContentWith(expectedZipFile))
                    .isInstanceOf(AssertionError.class);
            }
        }

        @Test
        void hasSameContentShouldThrowWhenAssertedZipFileHasDifferentSizeWithExpectedZipFile() throws Exception {
            ZipEntryWithContent sameEntry = entryBuilder().name(ENTRY_NAME).content(ENTRY_CONTENT).build();
            ZipEntryWithContent additionalEntry = entryBuilder().name(ENTRY_NAME_2).content(ENTRY_CONTENT).build();

            try (ZipFile expectedZipFile = zipFile(destination, sameEntry);
                    ZipFile assertedZipFile = zipFile(destination2, sameEntry, additionalEntry)) {
                assertThatThrownBy(() -> assertThatZip(assertedZipFile)
                    .hasSameContentWith(expectedZipFile))
                    .isInstanceOf(AssertionError.class);
            }
        }

        @Test
        void hasSameContentShouldThrowWhenAssertedEntriesHaveDifferentContent() throws Exception {
            try (ZipFile expectedZipFile = zipFile(destination, entryBuilder().name(ENTRY_NAME).content(ENTRY_CONTENT).build());
                    ZipFile assertedZipFile = zipFile(destination2, entryBuilder().name(ENTRY_NAME).content(ENTRY_CONTENT_2).build())) {
                assertThatThrownBy(() -> assertThatZip(assertedZipFile)
                    .hasSameContentWith(expectedZipFile))
                    .isInstanceOf(AssertionError.class);
            }
        }

        @Test
        void hasSameContentShouldThrowWhenAssertedEntriesHaveDifferentNames() throws Exception {
            try (ZipFile expectedZipFile = zipFile(destination, entryBuilder().name(ENTRY_NAME).content(ENTRY_CONTENT).build());
                    ZipFile assertedZipFile = zipFile(destination2, entryBuilder().name(ENTRY_NAME_2).content(ENTRY_CONTENT).build())) {
                assertThatThrownBy(() -> assertThatZip(assertedZipFile)
                    .hasSameContentWith(expectedZipFile))
                    .isInstanceOf(AssertionError.class);
            }
        }

        @Test
        void hasSameContentShouldThrowWhenEntryHasDifferentExtraFieldsSize() throws Exception {
            ZipEntryWithContent expectedEntry = entryBuilder().name(ENTRY_NAME).content(ENTRY_CONTENT)
                .addField(new UidExtraField(1L))
                .addField(new UidExtraField(2L))
                .build();

            ZipEntryWithContent assertedEntry = entryBuilder().name(ENTRY_NAME).content(ENTRY_CONTENT)
                .addField(new UidExtraField(1L))
                .build();

            try (ZipFile expectedZipFile = zipFile(destination, expectedEntry);
                    ZipFile assertedZipFile = zipFile(destination2, assertedEntry)) {
                assertThatThrownBy(() -> assertThatZip(assertedZipFile)
                    .hasSameContentWith(expectedZipFile))
                    .isInstanceOf(AssertionError.class);
            }
        }

        @Test
        void hasSameContentShouldThrowWhenEntryHasSameExtraFieldsSizeButDifferentOrder() throws Exception {
            ZipEntryWithContent expectedEntry = entryBuilder().name(ENTRY_NAME).content(ENTRY_CONTENT)
                .addField(new UidExtraField(1L))
                .addField(new SizeExtraField(2L))
                .build();

            ZipEntryWithContent assertedEntry = entryBuilder().name(ENTRY_NAME).content(ENTRY_CONTENT)
                .addField(new SizeExtraField(2L))
                .addField(new UidExtraField(1L))
                .build();

            try (ZipFile expectedZipFile = zipFile(destination, expectedEntry);
                    ZipFile assertedZipFile = zipFile(destination2, assertedEntry)) {
                assertThatThrownBy(() -> assertThatZip(assertedZipFile)
                    .hasSameContentWith(expectedZipFile))
                    .isInstanceOf(AssertionError.class);
            }
        }

        @Test
        void hasSameContentShouldThrowWhenEntryHasSameExtraFieldsSizeAndOrder() throws Exception {
            ZipEntryWithContent expectedEntry = entryBuilder().name(ENTRY_NAME).content(ENTRY_CONTENT)
                .addField(new UidExtraField(1L))
                .addField(new SizeExtraField(2L))
                .build();

            ZipEntryWithContent assertedEntry = entryBuilder().name(ENTRY_NAME).content(ENTRY_CONTENT)
                .addField(new UidExtraField(1L))
                .addField(new SizeExtraField(2L))
                .build();

            try (ZipFile expectedZipFile = zipFile(destination, expectedEntry);
                    ZipFile assertedZipFile = zipFile(destination2, assertedEntry)) {
                assertThatCode(() -> assertThatZip(assertedZipFile)
                    .hasSameContentWith(expectedZipFile))
                    .doesNotThrowAnyException();
            }
        }
    }

    @Nested
    class HasEntriesSize {

        @Test
        void hasEntriesSizeShouldNotThrowWhenExpectingSizeEqualsEntriesSize() throws Exception {
            ZipEntryWithContent firstEntry = entryBuilder().name(ENTRY_NAME).content(ENTRY_CONTENT).build();
            ZipEntryWithContent secondEntry = entryBuilder().name(ENTRY_NAME_2).content(ENTRY_CONTENT).build();

            try (ZipFile assertedZipFile = zipFile(destination, firstEntry, secondEntry)) {
                assertThatCode(() -> assertThatZip(assertedZipFile)
                    .hasEntriesSize(2))
                    .doesNotThrowAnyException();
            }
        }

        @Test
        void hasEntriesSizeShouldNotThrowWhenNoEntriesAndExpectingSizeIsZero() throws Exception {
            try (ZipFile assertedZipFile = zipFile(destination)) {
                assertThatCode(() -> assertThatZip(assertedZipFile)
                    .hasEntriesSize(0))
                    .doesNotThrowAnyException();
            }
        }

        @Test
        void hasEntriesSizeShouldThrowWhenExpectingSizeIsNegative() throws Exception {
            try (ZipFile assertedZipFile = zipFile(destination)) {
                assertThatThrownBy(() -> assertThatZip(assertedZipFile)
                    .hasEntriesSize(-1))
                    .isInstanceOf(AssertionError.class);
            }
        }

        @Test
        void hasEntriesSizeShouldThrowWhenExpectingSizeDoesntEqualsEntriesSize() throws Exception {
            ZipEntryWithContent firstEntry = entryBuilder().name(ENTRY_NAME).content(ENTRY_CONTENT).build();
            ZipEntryWithContent secondEntry = entryBuilder().name(ENTRY_NAME_2).content(ENTRY_CONTENT).build();

            try (ZipFile assertedZipFile = zipFile(destination, firstEntry, secondEntry)) {
                assertThatThrownBy(() -> assertThatZip(assertedZipFile)
                    .hasEntriesSize(3))
                    .isInstanceOf(AssertionError.class);
            }
        }
    }

    @Nested
    class AllSatisfies {

        @Test
        void allSatisfiesShouldNotThrowWhenNoEntries() throws Exception {
            try (ZipFile assertedZipFile = zipFile(destination)) {
                assertThatCode(() -> assertThatZip(assertedZipFile)
                    .allSatisfies(entry -> entry.hasStringContent("sub string")))
                    .doesNotThrowAnyException();
            }
        }

        @Test
        void allSatisfiesShouldNotThrowWhenAllEntriesMatchAssertion() throws Exception {
            ZipEntryWithContent firstEntry = entryBuilder().name("entry 1").content(ENTRY_CONTENT).build();
            ZipEntryWithContent secondEntry = entryBuilder().name("entry 2").content(ENTRY_CONTENT).build();

            try (ZipFile assertedZipFile = zipFile(destination, firstEntry, secondEntry)) {
                assertThatCode(() -> assertThatZip(assertedZipFile)
                    .allSatisfies(entry -> entry.hasStringContent(STRING_ENTRY_CONTENT)))
                    .doesNotThrowAnyException();
            }
        }

        @Test
        void allSatisfiesShouldNotThrowWhenAllEntriesMatchAllAssertions() throws Exception {
            UidExtraField zipExtraField = new UidExtraField(1L);
            ZipEntryWithContent firstEntry = entryBuilder().name("entry 1").content(ENTRY_CONTENT)
                .addField(zipExtraField)
                .build();
            ZipEntryWithContent secondEntry = entryBuilder().name("entry 2").content(ENTRY_CONTENT)
                .addField(zipExtraField)
                .build();

            try (ZipFile assertedZipFile = zipFile(destination, firstEntry, secondEntry)) {
                assertThatCode(() -> assertThatZip(assertedZipFile)
                    .allSatisfies(entry -> entry.hasStringContent(STRING_ENTRY_CONTENT))
                    .allSatisfies(entry -> entry.containsExtraFields(zipExtraField)))
                    .doesNotThrowAnyException();
            }
        }

        @Test
        void allSatisfiesShouldThrowWhenNotAllEntriesMatchAssertion() throws Exception {
            ZipEntryWithContent firstEntry = entryBuilder().name("entry 1").content(ENTRY_CONTENT).build();
            ZipEntryWithContent secondEntry = entryBuilder().name("entry 2").content(ENTRY_CONTENT).build();

            try (ZipFile assertedZipFile = zipFile(destination, firstEntry, secondEntry)) {
                assertThatThrownBy(() -> assertThatZip(assertedZipFile)
                    .allSatisfies(entry -> EntryChecks.hasName("entry 1")))
                    .isInstanceOf(AssertionError.class);
            }
        }
    }
}
