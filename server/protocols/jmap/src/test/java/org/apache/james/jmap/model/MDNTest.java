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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class MDNTest {

    public static final String TEXT_BODY = "text body";
    public static final String SUBJECT = "subject";
    public static final String REPORTING_UA = "reportingUA";
    public static final MDNDisposition DISPOSITION = MDNDisposition.builder()
        .actionMode(DispositionActionMode.Automatic)
        .sendingMode(DispositionSendingMode.Automatic)
        .type(DispositionType.Processed)
        .build();
    public static final TestMessageId MESSAGE_ID = TestMessageId.of(45);

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(MDN.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void builderShouldReturnObjectWhenAllFieldsAreValid() {
        assertThat(
            MDN.builder()
                .disposition(DISPOSITION)
                .messageId(MESSAGE_ID)
                .reportingUA(REPORTING_UA)
                .subject(SUBJECT)
                .textBody(TEXT_BODY)
                .build())
            .isEqualTo(new MDN(MESSAGE_ID, SUBJECT, TEXT_BODY, REPORTING_UA, DISPOSITION));
    }

    @Test
    public void dispositionIsCompulsory() {
        assertThatThrownBy(() ->
            MDN.builder()
                .messageId(MESSAGE_ID)
                .reportingUA(REPORTING_UA)
                .subject(SUBJECT)
                .textBody(TEXT_BODY)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void messageIdIsCompulsory() {
        assertThatThrownBy(() ->
            MDN.builder()
                .disposition(DISPOSITION)
                .reportingUA(REPORTING_UA)
                .subject(SUBJECT)
                .textBody(TEXT_BODY)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void reportingUAIsCompulsory() {
        assertThatThrownBy(() ->
            MDN.builder()
                .disposition(DISPOSITION)
                .messageId(MESSAGE_ID)
                .subject(SUBJECT)
                .textBody(TEXT_BODY)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void subjectIsCompulsory() {
        assertThatThrownBy(() ->
            MDN.builder()
                .disposition(DISPOSITION)
                .messageId(MESSAGE_ID)
                .reportingUA(REPORTING_UA)
                .textBody(TEXT_BODY)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void textBodyIsCompulsory() {
        assertThatThrownBy(() ->
            MDN.builder()
                .disposition(DISPOSITION)
                .messageId(MESSAGE_ID)
                .reportingUA(REPORTING_UA)
                .subject(SUBJECT)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

}