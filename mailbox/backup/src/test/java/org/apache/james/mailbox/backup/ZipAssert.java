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

import static org.apache.james.mailbox.backup.ZipArchiveEntryAssert.assertThatZipEntry;
import static org.apache.james.mailbox.backup.ZipAssert.EntryCheck.defaultNoCheck;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.backup.zip.WithZipHeader;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.error.BasicErrorMessageFactory;
import org.assertj.core.error.ErrorMessageFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class ZipAssert extends AbstractAssert<ZipAssert, ZipFile> implements AutoCloseable {
    interface EntryCheck {

        static EntryCheck defaultNoCheck() {
            return assertion -> assertion;
        }

        default EntryCheck compose(EntryCheck other) {
            return assertion -> other.test(this.test(assertion));
        }

        ZipArchiveEntryAssert test(ZipArchiveEntryAssert assertion) throws Exception;
    }

    public static class EntryChecks {
        public static EntryChecks hasName(String name) {
            return new EntryChecks(name, assertion -> assertion.hasName(name));
        }

        private final String name;
        private final EntryCheck check;

        private EntryChecks(String name, EntryCheck check) {
            this.name = name;
            this.check = check;
        }

        public EntryChecks check(EntryCheck additionalCheck) {
            return new EntryChecks(name,
                check.compose(additionalCheck));
        }

        public EntryChecks hasStringContent(String stringContent) {
            return check(check.compose(assertion -> assertion.hasStringContent(stringContent)));
        }

        public EntryChecks isDirectory() {
            return check(check.compose(assertion -> assertion.isDirectory()));
        }

        public EntryChecks containsExtraFields(ZipExtraField... expectedExtraFields) {
            return check(check.compose(assertion -> assertion.containsExtraFields(expectedExtraFields)));
        }
    }

    public static ZipAssert assertThatZip(ZipFile zipFile) {
        return new ZipAssert(zipFile);
    }

    public static ZipAssert assertThatZip(ByteArrayOutputStream outputStream) throws IOException {
        return assertThatZip(new ZipFile(new SeekableInMemoryByteChannel(outputStream.toByteArray())));
    }

    public static ZipAssert assertThatZip(InputStream inputStream) throws IOException {
        return assertThatZip(zipFileFromInputStream(inputStream));
    }

    private static ZipFile zipFileFromInputStream(InputStream inputStream) throws IOException {
        return new ZipFile(new SeekableInMemoryByteChannel(IOUtils.toByteArray(inputStream)));
    }

    private static BasicErrorMessageFactory shouldHaveSize(ZipFile zipFile, int expected, int actual) {
        return new BasicErrorMessageFactory("%nExpecting %s to have size %s but was %s", zipFile, expected, actual);
    }

    private static BasicErrorMessageFactory shouldBeEmpty(ZipFile zipFile) {
        return new BasicErrorMessageFactory("%nExpecting %s to be empty", zipFile);
    }

    private static BasicErrorMessageFactory shouldBeNonNull() {
        return new BasicErrorMessageFactory("%nExpecting zipFile to be non-null");
    }

    private static BasicErrorMessageFactory shouldHaveSameEntriesSize(List<ZipArchiveEntry> entries, List<ZipArchiveEntry> expectedEntries) {
        return new BasicErrorMessageFactory("%nExpecting zipFile to contains %s entries (%s) but actually contains (%s)",
            expectedEntries.size(), expectedEntries, entries);
    }

    private static BasicErrorMessageFactory shouldHaveEntriesSize(int entriesSize, int expectedEntriesSize) {
        return new BasicErrorMessageFactory("%nExpecting zipFile to contains %s entries but actually contains (%s) entries",
            expectedEntriesSize, entriesSize);
    }

    private static ErrorMessageFactory entriesShouldHaveSameContentAt(int entryIndex) {
        return new BasicErrorMessageFactory("%nExpecting zipFile entry at index %s has same content", entryIndex);
    }

    private static ErrorMessageFactory entriesShouldHaveSameName(ZipArchiveEntry entry, ZipArchiveEntry expectedEntry, int entryIndex) {
        return new BasicErrorMessageFactory("%nExpecting zipFile entry name (%s) at index %s but actually (%s)",
            expectedEntry.getName(), entryIndex, entry.getName());
    }

    private static ErrorMessageFactory entriesShouldHaveSameExtraFields(ZipArchiveEntry entry, ZipArchiveEntry expectedEntry, int entryIndex) {
        return new BasicErrorMessageFactory("%nExpecting zipFile entry at index %s has extra fields (%s) but actually (%s)", entryIndex,
            expectedEntry.getExtraFields(), entry.getExtraFields());
    }

    private final ZipFile zipFile;

    private ZipAssert(ZipFile zipFile) {
        super(zipFile, ZipAssert.class);
        this.zipFile = zipFile;
    }

    private ZipAssert containsEntriesMatchingWithComparator(Optional<Comparator<ZipArchiveEntry>> sortEntriesBy,
                                                            Optional<Comparator<EntryChecks>> sortEntryChecksBy,
                                                            EntryChecks... entryChecks) throws Exception {
        isNotNull();

        Stream<EntryChecks> entryChecksStream = Arrays.stream(entryChecks);
        List<EntryChecks> sortedEntryChecks = sortAndCollect(sortEntryChecksBy, entryChecksStream);

        Stream<ZipArchiveEntry> entryStream = Collections.list(zipFile.getEntries())
            .stream();
        List<ZipArchiveEntry> entries = sortAndCollect(sortEntriesBy, entryStream);

        if (entries.size() != entryChecks.length) {
            throwAssertionError(shouldHaveSize(zipFile, entryChecks.length, entries.size()));
        }
        for (int i = 0; i < entries.size(); i++) {
            sortedEntryChecks.get(i).check.test(assertThatZipEntry(zipFile, entries.get(i)));
        }
        return myself;
    }

    private <T> List<T> sortAndCollect(Optional<Comparator<T>> sortBy, Stream<T> stream) {
        Stream<T> sortedStream = sortBy.map(comparator -> stream.sorted(comparator))
            .orElse(stream);
        return sortedStream.collect(Guavate.toImmutableList());
    }

    /**
     * Check that the zip entries in the zip file contains only the entries matching
     * the entryChecks, with the same number of checks as entries.
     * The order of the entries in the zip file is independent from the one of the entryChecks.
     */
    public ZipAssert containsOnlyEntriesMatching(EntryChecks... entryChecks) throws Exception {
        Comparator<ZipArchiveEntry> entryComparator = Comparator.comparing(ZipArchiveEntry::getName);
        Comparator<EntryChecks> entryCheckComparator = Comparator.comparing(check -> check.name);

        return containsEntriesMatchingWithComparator(Optional.of(entryComparator), Optional.of(entryCheckComparator), entryChecks);
    }

    /**
     * Check that the zip entries in the zip file contains only the entries matching
     * the entryChecks, with the same number of checks as entries.
     * The order of the entries in the zip file must match the one of the entryChecks.
     */
    public ZipAssert containsExactlyEntriesMatching(EntryChecks... entryChecks) throws Exception {
        return containsEntriesMatchingWithComparator(Optional.empty(), Optional.empty(), entryChecks);
    }

    public ZipAssert hasNoEntry() {
        isNotNull();
        if (zipFile.getEntries().hasMoreElements()) {
            throwAssertionError(shouldBeEmpty(zipFile));
        }
        return myself;
    }

    public ZipAssert hasEntriesSize(int expectedSize) {
        isNotNull();
        assertThat(expectedSize).describedAs("expectedSize cannot be a negative number")
            .isGreaterThanOrEqualTo(0);

        ArrayList<ZipArchiveEntry> zipEntries = Collections.list(zipFile.getEntries());
        if (zipEntries.size() != expectedSize) {
            throwAssertionError(shouldHaveEntriesSize(zipEntries.size(), expectedSize));
        }
        return myself;
    }

    public ZipAssert allSatisfies(UnaryOperator<EntryChecks> entryChecksOperator) throws Exception {
        isNotNull();
        List<ZipArchiveEntry> entries = Collections.list(zipFile.getEntries());
        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            ZipArchiveEntry entry = entries.get(entryIndex);
            EntryChecks composedEntryChecks = entryChecksOperator.apply(new EntryChecks(entry.getName(), defaultNoCheck()));
            ZipArchiveEntryAssert zipAssertionOfEntry = assertThatZipEntry(zipFile, entry);

            composedEntryChecks.check.test(zipAssertionOfEntry);
        }

        return myself;
    }

    @Override
    public void close() throws Exception {
        zipFile.close();
    }

    public ZipAssert hasSameContentWith(InputStream inputStream) throws IOException {
        return hasSameContentWith(zipFileFromInputStream(inputStream));
    }

    public ZipAssert hasSameContentWith(ZipFile anotherZipFile) throws IOException {
        validateNonNull(zipFile);
        validateNonNull(anotherZipFile);

        ArrayList<ZipArchiveEntry> entries = Collections.list(zipFile.getEntries());
        ArrayList<ZipArchiveEntry> entriesOfAnother = Collections.list(anotherZipFile.getEntries());

        if (entries.size() != entriesOfAnother.size()) {
            throwAssertionError(shouldHaveSameEntriesSize(entries, entriesOfAnother));
        }

        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            ZipArchiveEntry entry = entries.get(entryIndex);
            ZipArchiveEntry entryOfAnother = entriesOfAnother.get(entryIndex);
            haveSameName(entry, entryOfAnother, entryIndex);
            haveSameExtraFields(entry, entryOfAnother, entryIndex);
            haveSameContentAt(zipFile.getInputStream(entry), anotherZipFile.getInputStream(entryOfAnother), entryIndex);
        }

        return myself;
    }

    private void haveSameName(ZipArchiveEntry entry, ZipArchiveEntry expectedEntry, int entryIndex) {
        try {
            assertThat(entry.getName()).isEqualTo(expectedEntry.getName());
        } catch (AssertionError assertionError) {
            throwAssertionError(entriesShouldHaveSameName(entry, expectedEntry, entryIndex));
        }
    }

    private void haveSameExtraFields(ZipArchiveEntry entry, ZipArchiveEntry expectedEntry, int entryIndex) {
        try {
            assertThat(extractJamesExtraFields(entry))
                .containsExactlyElementsOf(extractJamesExtraFields(expectedEntry));
        } catch (AssertionError assertionError) {
            throwAssertionError(entriesShouldHaveSameExtraFields(entry, expectedEntry, entryIndex));
        }
    }

    private void haveSameContentAt(InputStream entryContent, InputStream expectingEntryContent, int entryIndex) {
        try {
            assertThat(entryContent)
                .hasSameContentAs(expectingEntryContent);
        } catch (AssertionError assertionError) {
            throwAssertionError(entriesShouldHaveSameContentAt(entryIndex));
        }
    }

    private void validateNonNull(ZipFile zipFile) {
        if (zipFile == null) {
            throwAssertionError(shouldBeNonNull());
        }
    }

    /**
     * Because there are always some extra fields not belong to James, and their equals() method doesn't work
     *
     * @param entry
     * @return
     */
    private ImmutableList<ZipExtraField> extractJamesExtraFields(ZipArchiveEntry entry) {
        return Stream.of(entry.getExtraFields())
            .filter(field -> field instanceof WithZipHeader)
            .collect(Guavate.toImmutableList());
    }
}
