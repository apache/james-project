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

import java.time.ZonedDateTime;
import java.util.List;

import org.apache.james.core.MailAddress;
import org.apache.james.queue.api.Mails;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.ManageableMailQueue.MailQueueItemView;
import org.apache.mailet.base.test.FakeMail;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

import com.github.steveash.guavate.Guavate;

public class MailQueueItemDTOTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void buildShouldThrowWhenNameIsNull() {
        assertThatThrownBy(() -> MailQueueItemDTO.builder().build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void buildShouldThrowWhenNameIsEmpty() {
        assertThatThrownBy(() -> MailQueueItemDTO.builder().name("").build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromShouldCreateTheRightObject() throws Exception {
        FakeMail mail = Mails.defaultMail().name("name").build();
        ZonedDateTime date = ZonedDateTime.parse("2018-01-02T11:22:02Z");
        MailQueueItemView mailQueueItemView = new ManageableMailQueue.DefaultMailQueueItemView(mail, date);
        MailQueueItemDTO mailQueueItemDTO = MailQueueItemDTO.from(mailQueueItemView);
        List<String> expectedRecipients = mail.getRecipients().stream()
                .map(MailAddress::asString)
                .collect(Guavate.toImmutableList());

        softly.assertThat(mailQueueItemDTO.getName()).isEqualTo(mail.getName());
        softly.assertThat(mailQueueItemDTO.getSender()).isEqualTo(mail.getMaybeSender().get().asString());
        softly.assertThat(mailQueueItemDTO.getRecipients()).isEqualTo(expectedRecipients);
        softly.assertThat(mailQueueItemDTO.getNextDelivery()).contains(date);
    }
}
