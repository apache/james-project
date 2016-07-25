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

package org.apache.james.jmap.model;

import static org.apache.james.jmap.model.CreationMessage.DraftEmailer;
import static org.apache.james.jmap.model.MessageProperties.MessageProperty;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.jmap.methods.ValidationResult;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class CreationMessageTest {

    private CreationMessage.Builder testedBuilder;

    @Before
    public void setUp() {
        testedBuilder = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("ba9-0f-dead-beef"))
                .headers(ImmutableMap.of());
    }

    @Test
    public void validateShouldReturnErrorWhenFromIsMissing() {
       testedBuilder = testedBuilder
               .subject("anything");

        CreationMessage sut = testedBuilder.build();

        assertThat(sut.validate()).contains(ValidationResult.builder()
                .message("'from' address is mandatory")
                .property(MessageProperty.from.asFieldName())
                .build()
        );
    }

    @Test
    public void validateShouldReturnErrorWhenFromIsInvalid() {
        testedBuilder = testedBuilder
                .subject("anything");

        CreationMessage sut = testedBuilder.from(DraftEmailer.builder().name("bob").email("bob@domain.com@example.com").build()).build();

        assertThat(sut.validate()).contains(ValidationResult.builder()
                .message("'from' address is mandatory")
                .property(MessageProperty.from.asFieldName())
                .build()
        );
    }

    @Test
    public void validateShouldReturnErrorWhenNoRecipientSet () {
        testedBuilder = testedBuilder
                .subject("anything");

        CreationMessage  sut = testedBuilder
                .from(DraftEmailer.builder().name("bob").email("bob@example.com").build()).build();

        assertThat(sut.validate()).extracting(ValidationResult::getErrorMessage).contains("no recipient address set");
    }

    @Test
    public void validateShouldReturnErrorWhenNoValidRecipientSet () {
        testedBuilder = testedBuilder
                .subject("anything");

        CreationMessage sut = testedBuilder
                .from(DraftEmailer.builder().name("bob").email("bob@example.com").build())
                .to(ImmutableList.of(DraftEmailer.builder().name("riri").email("riri@acme.com@example.com").build()))
                .cc(ImmutableList.of(DraftEmailer.builder().name("fifi").email("fifi@acme.com@example.com").build()))
                .bcc(ImmutableList.of(DraftEmailer.builder().name("loulou").email("loulou@acme.com@example.com").build()))
                .build();

        assertThat(sut.validate()).extracting(ValidationResult::getErrorMessage).contains("no recipient address set");
    }

    @Test
    public void validateShouldReturnEmptyListWhenNoErrors () {
        testedBuilder = testedBuilder
                .subject("anything");

        CreationMessage sut = testedBuilder
                .from(DraftEmailer.builder().name("bob").email("bob@example.com").build())
                .to(ImmutableList.of(DraftEmailer.builder().name("riri").email("riri@example.com").build()))
                .build();

        assertThat(sut.validate()).isEmpty();
    }

    @Test
    public void validateShouldReturnEmptyListWhenSubjectIsNull () {
        testedBuilder = testedBuilder
                .subject(null);

        CreationMessage sut = testedBuilder
                .from(DraftEmailer.builder().name("bob").email("bob@example.com").build())
                .to(ImmutableList.of(DraftEmailer.builder().name("riri").email("riri@example.com").build()))
                .build();

        assertThat(sut.validate()).isEmpty();
    }

    @Test
    public void validateShouldReturnEmptyListWhenSubjectIsEmpty () {
        testedBuilder = testedBuilder
                .subject("");

        CreationMessage sut = testedBuilder
                .from(DraftEmailer.builder().name("bob").email("bob@example.com").build())
                .to(ImmutableList.of(DraftEmailer.builder().name("riri").email("riri@example.com").build()))
                .build();

        assertThat(sut.validate()).isEmpty();
    }

}