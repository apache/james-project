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

import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class WithStorageDirectiveTest {

    private WithStorageDirective testee;

    @Rule
    public JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Before
    public void setUp() {
        testee = new WithStorageDirective(MemoryUsersRepository.withVirtualHosting());
    }

    @Test
    public void initShouldThrowWhenNoTargetFolderEntry() {
        assertThatThrownBy(() -> testee.init(FakeMailetConfig.builder()
            .build()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void initShouldThrowWhenEmptyTargetFolderEntry() {
        assertThatThrownBy(() -> testee.init(FakeMailetConfig.builder()
            .setProperty(WithStorageDirective.TARGET_FOLDER_NAME, "")
            .build()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void serviceShouldAddDeliveryPathForRecipients() throws Exception {
        String targetFolderName = "Spam";
        testee.init(FakeMailetConfig.builder()
            .setProperty(WithStorageDirective.TARGET_FOLDER_NAME, targetFolderName)
            .build());

        FakeMail mail = FakeMail.builder()
            .recipients(MailAddressFixture.RECIPIENT1, MailAddressFixture.RECIPIENT2)
            .build();

        testee.service(mail);

        softly.assertThat(mail.getAttributeNames())
            .containsOnly("DeliveryPath_recipient2@localhost", "DeliveryPath_recipient1@localhost");
        softly.assertThat(mail.getAttribute("DeliveryPath_recipient1@localhost")).isEqualTo(targetFolderName);
        softly.assertThat(mail.getAttribute("DeliveryPath_recipient2@localhost")).isEqualTo(targetFolderName);
    }

    @Test
    public void serviceShouldNotThrowWhenNoRecipients() throws Exception {
        String targetFolderName = "Spam";
        testee.init(FakeMailetConfig.builder()
            .setProperty(WithStorageDirective.TARGET_FOLDER_NAME, targetFolderName)
            .build());

        FakeMail mail = FakeMail.builder()
            .recipients()
            .build();

        testee.service(mail);

        assertThat(mail.getAttributeNames())
            .isEmpty();
    }

    @Test
    public void serviceShouldOverridePreviousStorageDirectives() throws Exception {
        String targetFolderName = "Spam";
        testee.init(FakeMailetConfig.builder()
            .setProperty(WithStorageDirective.TARGET_FOLDER_NAME, targetFolderName)
            .build());

        FakeMail mail = FakeMail.builder()
            .recipients(MailAddressFixture.RECIPIENT1, MailAddressFixture.RECIPIENT2)
            .attribute("DeliveryPath_recipient2@localhost", "otherFolder")
            .build();

        testee.service(mail);

        softly.assertThat(mail.getAttributeNames())
            .containsOnly("DeliveryPath_recipient2@localhost", "DeliveryPath_recipient1@localhost");
        softly.assertThat(mail.getAttribute("DeliveryPath_recipient1@localhost")).isEqualTo(targetFolderName);
        softly.assertThat(mail.getAttribute("DeliveryPath_recipient2@localhost")).isEqualTo(targetFolderName);
    }

}