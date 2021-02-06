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

package org.apache.james.transport.mailets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WithStorageDirectiveTest {
    private static final DomainList NO_DOMAIN_LIST = null;

    private WithStorageDirective testee;

    @BeforeEach
    void setUp() {
        testee = new WithStorageDirective(MemoryUsersRepository.withVirtualHosting(NO_DOMAIN_LIST));
    }

    @Test
    void initShouldThrowWhenNoTargetFolderEntry() {
        assertThatThrownBy(() -> testee.init(FakeMailetConfig.builder()
            .build()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void initShouldThrowWhenEmptyTargetFolderEntry() {
        assertThatThrownBy(() -> testee.init(FakeMailetConfig.builder()
            .setProperty(WithStorageDirective.TARGET_FOLDER_NAME, "")
            .build()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void serviceShouldAddDeliveryPathForRecipients() throws Exception {
        String targetFolderName = "Spam";
        testee.init(FakeMailetConfig.builder()
            .setProperty(WithStorageDirective.TARGET_FOLDER_NAME, targetFolderName)
            .build());

        FakeMail mail = FakeMail.builder()
            .name("name")
            .recipients(MailAddressFixture.RECIPIENT1, MailAddressFixture.RECIPIENT2)
            .build();

        testee.service(mail);

        AttributeName recipient1 = AttributeName.of("DeliveryPath_recipient1@localhost");
        AttributeName recipient2 = AttributeName.of("DeliveryPath_recipient2@localhost");
        assertThat(mail.attributes())
            .containsOnly(
                new Attribute(recipient1, AttributeValue.of(targetFolderName)),
                new Attribute(recipient2, AttributeValue.of(targetFolderName)));
    }

    @Test
    void serviceShouldNotThrowWhenNoRecipients() throws Exception {
        String targetFolderName = "Spam";
        testee.init(FakeMailetConfig.builder()
            .setProperty(WithStorageDirective.TARGET_FOLDER_NAME, targetFolderName)
            .build());

        FakeMail mail = FakeMail.builder()
            .name("name")
            .recipients()
            .build();

        testee.service(mail);

        assertThat(mail.attributes())
            .isEmpty();
    }

    @Test
    void serviceShouldOverridePreviousStorageDirectives() throws Exception {
        AttributeName name1 = AttributeName.of("DeliveryPath_recipient1@localhost");
        AttributeName name2 = AttributeName.of("DeliveryPath_recipient2@localhost");
        AttributeValue<String> targetFolderName = AttributeValue.of("Spam");
        Attribute attribute1 = new Attribute(name1, targetFolderName);
        Attribute attribute2 = new Attribute(name2, targetFolderName);
        testee.init(FakeMailetConfig.builder()
            .setProperty(WithStorageDirective.TARGET_FOLDER_NAME, targetFolderName.value())
            .build());

        FakeMail mail = FakeMail.builder()
            .name("name")
            .recipients(MailAddressFixture.RECIPIENT1, MailAddressFixture.RECIPIENT2)
            .attribute(new Attribute(name2, AttributeValue.of("otherFolder")))
            .build();

        testee.service(mail);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mail.attributes())
                .containsExactlyInAnyOrder(attribute1, attribute2);
            softly.assertThat(mail.getAttribute(name1)).contains(attribute1);
            softly.assertThat(mail.getAttribute(name2)).contains(attribute2);
        });
    }

}