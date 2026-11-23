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

package org.apache.james.mailbox.opensearch.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

class SubjectsDtoTest {
    @Test
    void fromShouldKeepMailingListQualifiers() {
        assertThat(SubjectsDto.from(ImmutableSet.of("[nas-backup.example.com] Backup done")).subjects())
            .contains("nas-backup.example.com", "example.com", "nas-backup", "Backup done");
    }

    @Test
    void fromShouldBoundTheQualifiersOfALongSubject() {
        String subject = "[" + "a.".repeat(16_000) + "] " + "[b]".repeat(5_000);

        int totalLength = SubjectsDto.from(ImmutableSet.of(subject)).subjects().stream()
            .mapToInt(String::length)
            .sum();

        assertThat(totalLength).isLessThan(1024 * 1024);
    }
}
