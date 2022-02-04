/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.mailbox.store.streaming;

import java.io.ByteArrayInputStream;

import org.apache.james.util.ClassLoaderUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class CountingInputStreamTest {
    @Test
    void shouldCountBytesAndLines() throws Exception {
        byte[] bytes = ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithNonIndexableAttachment.eml");

        CountingInputStream countingInputStream = new CountingInputStream(new ByteArrayInputStream(bytes));
        countingInputStream.readAll();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(countingInputStream.getOctetCount()).isEqualTo(128259);
            softly.assertThat(countingInputStream.getLineCount()).isEqualTo(1655);
        });
    }
}