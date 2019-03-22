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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.error.BasicErrorMessageFactory;

import com.github.steveash.guavate.Guavate;

public class ZipAssert extends AbstractAssert<ZipAssert, ZipFile> implements AutoCloseable {
    interface EntryCheck {
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

    static ZipAssert assertThatZip(ZipFile zipFile) {
        return new ZipAssert(zipFile);
    }

    public static ZipAssert assertThatZip(ByteArrayOutputStream outputStream) throws IOException {
        return assertThatZip(new ZipFile(new SeekableInMemoryByteChannel(outputStream.toByteArray())));
    }

    private static BasicErrorMessageFactory shouldHaveSize(ZipFile zipFile, int expected, int actual) {
        return new BasicErrorMessageFactory("%nExpecting %s to have size %s but was %s", zipFile, expected, actual);
    }

    private static BasicErrorMessageFactory shouldBeEmpty(ZipFile zipFile) {
        return new BasicErrorMessageFactory("%nExpecting %s to be empty", zipFile);
    }

    private final ZipFile zipFile;

    private ZipAssert(ZipFile zipFile) {
        super(zipFile, ZipAssert.class);
        this.zipFile = zipFile;
    }

    public ZipAssert containsOnlyEntriesMatching(EntryChecks... entryChecks) throws Exception {
        isNotNull();
        List<EntryChecks> sortedEntryChecks = Arrays.stream(entryChecks)
            .sorted(Comparator.comparing(checks -> checks.name))
            .collect(Guavate.toImmutableList());
        List<ZipArchiveEntry> entries = Collections.list(zipFile.getEntries())
            .stream()
            .sorted(Comparator.comparing(ZipArchiveEntry::getName))
            .collect(Guavate.toImmutableList());
        if (entries.size() != entryChecks.length) {
            throwAssertionError(shouldHaveSize(zipFile, entryChecks.length, entries.size()));
        }
        for (int i = 0; i < entries.size(); i++) {
            sortedEntryChecks.get(i).check.test(assertThatZipEntry(zipFile, entries.get(i)));
        }
        return myself;
    }

    public ZipAssert hasNoEntry() {
        isNotNull();
        if (zipFile.getEntries().hasMoreElements()) {
            throwAssertionError(shouldBeEmpty(zipFile));
        }
        return myself;
    }

    @Override
    public void close() throws Exception {
        zipFile.close();
    }
}
