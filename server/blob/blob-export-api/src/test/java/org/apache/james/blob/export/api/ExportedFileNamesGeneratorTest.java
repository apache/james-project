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

package org.apache.james.blob.export.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.TestBlobId;
import org.junit.jupiter.api.Test;

class ExportedFileNamesGeneratorTest {

    private static final TestBlobId.Factory BLOB_ID_FACTORY = new TestBlobId.Factory();
    private static final BlobId BLOB_ID = BLOB_ID_FACTORY.from("blobId");

    @Test
    void generateFileNameShouldNotHavePrefixWhenEmptyPrefix() {
        Optional<String> prefix = Optional.empty();
        Optional<FileExtension> extension = Optional.of(FileExtension.ZIP);

        assertThat(ExportedFileNamesGenerator.generateFileName(prefix, BLOB_ID, extension))
            .isEqualTo("blobId.zip");
    }

    @Test
    void generateFileNameShouldNotHaveSuffixWhenEmptySuffix() {
        Optional<FileExtension> extension = Optional.empty();

        assertThat(ExportedFileNamesGenerator.generateFileName(Optional.of("prefix-"), BLOB_ID, extension))
            .isEqualTo("prefix-blobId");
    }

    @Test
    void generateFileNameShouldNotHaveSuffixAndPrefixWhenNotSpecifying() {
        Optional<String> prefix = Optional.empty();
        Optional<FileExtension> extension = Optional.empty();

        assertThat(ExportedFileNamesGenerator.generateFileName(prefix, BLOB_ID, extension))
            .isEqualTo("blobId");
    }

    @Test
    void generateFileNameShouldHaveSuffixAndPrefixWhenSpecified() {
        Optional<FileExtension> extension = Optional.of(FileExtension.ZIP);

        assertThat(ExportedFileNamesGenerator.generateFileName(Optional.of("prefix-"), BLOB_ID, extension))
            .isEqualTo("prefix-blobId.zip");
    }

    @Test
    void generateFileNameShouldThrowWhenNullBlobId() {
        BlobId nullBlobId = null;
        assertThatThrownBy(() -> ExportedFileNamesGenerator.generateFileName(
                Optional.empty(),
                nullBlobId,
                Optional.empty()))
            .isInstanceOf(NullPointerException.class);
    }
}