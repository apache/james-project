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
package org.apache.james.webadmin.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class MailQueueDTOTest {
    @Test
    void buildShouldThrowWhenNameIsNull() {
        assertThatThrownBy(() -> MailQueueDTO.builder().build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildShouldThrowWhenNameIsEmpty() {
        assertThatThrownBy(() -> MailQueueDTO.builder().name("").build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builderShouldCreateTheRightObject() {
        String name = "name";
        int size = 123;

        MailQueueDTO mailQueueDTO = MailQueueDTO.builder()
            .name(name)
            .size(size)
            .build();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailQueueDTO.getName()).isEqualTo(name);
            softly.assertThat(mailQueueDTO.getSize()).isEqualTo(size);
        });
    }
}
