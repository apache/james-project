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

import java.util.Collections;
import java.util.List;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.error.BasicErrorMessageFactory;

public class ZipAssert extends AbstractAssert<ZipAssert, ZipFile> {
    interface EntryCheck {
        void test(ZipArchiveEntryAssert assertion) throws Exception;
    }

    public static ZipAssert assertThatZip(ZipFile zipFile) {
        return new ZipAssert(zipFile);
    }

    private static BasicErrorMessageFactory shouldHaveSize(ZipFile zipFile, int expected, int actual) {
        return new BasicErrorMessageFactory("%nExpecting %s to have side %d but was %d", zipFile, expected, actual);
    }

    private static BasicErrorMessageFactory shouldBeEmpty(ZipFile zipFile) {
        return new BasicErrorMessageFactory("%nExpecting %s to be empty", zipFile);
    }

    private final ZipFile zipFile;

    private ZipAssert(ZipFile zipFile) {
        super(zipFile, ZipAssert.class);
        this.zipFile = zipFile;
    }

    public ZipAssert containsExactlyEntriesMatching(EntryCheck... entryChecks) throws Exception {
        isNotNull();
        List<ZipArchiveEntry> entries = Collections.list(zipFile.getEntries());
        if (entries.size() != entryChecks.length) {
            throwAssertionError(shouldHaveSize(zipFile, entryChecks.length, entries.size()));
        }
        for (int i = 0; i < entries.size(); i++) {
            entryChecks[i].test(assertThatZipEntry(zipFile, entries.get(i)));
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

}
