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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.backup.zip.ZipEntryType;
import org.junit.jupiter.api.Test;

class ZipEntryTypeContract {

    private void assertZipEntryTypeDeserializedFromValue(int value, ZipEntryType expectedType) {
        assertThat(ZipEntryType.zipEntryType(value)).contains(expectedType);
    }

    @Test
    void mailboxShouldBeDeserializedFromOne() {
        assertZipEntryTypeDeserializedFromValue(0, ZipEntryType.MAILBOX);
    }

    @Test
    void mailboxAnnotationDirShouldBeDeserializedFromTwo() {
        assertZipEntryTypeDeserializedFromValue(1, ZipEntryType.MAILBOX_ANNOTATION_DIR);
    }

    @Test
    void mailboxAnnotationShouldBeDeserializedFromThree() {
        assertZipEntryTypeDeserializedFromValue(2, ZipEntryType.MAILBOX_ANNOTATION);
    }

    @Test
    void messageShouldBeDeserializedFromFour() {
        assertZipEntryTypeDeserializedFromValue(3, ZipEntryType.MESSAGE);
    }
}
