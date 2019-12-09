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

package org.apache.james.mailbox.elasticsearch.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;

class SubjectsTest {

    @Test
    void fromShouldThrowWhenSetIsNull() {
        assertThatThrownBy(() -> Subjects.from(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("'subjects' is mandatory");
    }

    @Test
    void serializeShouldReturnEmptyWhenEmptySet() {
        Subjects subjects = Subjects.from(ImmutableSet.of());

        assertThat(subjects.serialize()).isEmpty();
    }

    @Test
    void serializeShouldNotJoinWhenOneElement() {
        String expected = "subject";
        Subjects subjects = Subjects.from(ImmutableSet.of(expected));

        assertThat(subjects.serialize()).isEqualTo(expected);
    }

    @Test
    void serializeShouldJoinWhenMultipleElements() {
        String subject = "subject";
        String subject2 = "subject2";
        String subject3 = "subject3";

        String expected = Joiner.on(" ").join(subject, subject2, subject3);

        Subjects subjects = Subjects.from(ImmutableSet.of(subject, subject2, subject3));

        assertThat(subjects.serialize()).isEqualTo(expected);
    }
}
