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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.error.BasicErrorMessageFactory;

public class ZipArchiveEntryAssert extends AbstractAssert<ZipArchiveEntryAssert, ZipArchiveEntry> {

    public static ZipArchiveEntryAssert assertThatZipEntry(ZipFile zipFile, ZipArchiveEntry zipArchiveEntry) {
        return new ZipArchiveEntryAssert(zipFile, zipArchiveEntry);
    }

    private static BasicErrorMessageFactory shouldHaveName(ZipArchiveEntry zipArchiveEntry, String expected) {
        return new BasicErrorMessageFactory("%nExpecting %s to have name %s but was %s", zipArchiveEntry, expected, zipArchiveEntry.getName());
    }

    private static BasicErrorMessageFactory contentShouldBePresent(ZipArchiveEntry zipArchiveEntry) {
        return new BasicErrorMessageFactory("%nCould not retrieve %s content", zipArchiveEntry);
    }

    private static BasicErrorMessageFactory shouldHaveContent(ZipArchiveEntry zipArchiveEntry, String expectedContent, String actualContent) {
        return new BasicErrorMessageFactory("%nExpecting %s to have content %s but was %s", zipArchiveEntry, expectedContent, actualContent);
    }

    private final ZipFile zipFile;
    private final ZipArchiveEntry actual;

    private ZipArchiveEntryAssert(ZipFile zipFile, ZipArchiveEntry zipArchiveEntry) {
        super(zipArchiveEntry, ZipArchiveEntryAssert.class);
        this.zipFile = zipFile;
        this.actual = zipArchiveEntry;
    }

    public ZipArchiveEntryAssert hasName(String name) {
        isNotNull();
        if (!actual.getName().equals(name)) {
            throwAssertionError(shouldHaveName(actual, name));
        }
        return myself;
    }

    public ZipArchiveEntryAssert hasStringContent(String content) throws IOException {
        isNotNull();
        InputStream inputStream = zipFile.getInputStream(actual);
        if (inputStream == null) {
            throwAssertionError(contentShouldBePresent(actual));
        }
        String actualContentAsString = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        if (!actualContentAsString.equals(content)) {
            throwAssertionError(shouldHaveContent(actual, content, actualContentAsString));
        }
        return myself;
    }
}
