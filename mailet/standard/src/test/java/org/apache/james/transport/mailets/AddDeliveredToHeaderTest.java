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

import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AddDeliveredToHeaderTest {

    private AddDeliveredToHeader testee;

    @BeforeEach
    public void setUp() throws Exception {
        testee = new AddDeliveredToHeader();
        testee.init(FakeMailetConfig.builder()
            .mailetName("AddDeliveredToHeader")
            .mailetContext(FakeMailContext.defaultContext())
            .build());
    }

    @Test
    public void serviceShouldHandleMailWithoutRecipient() throws Exception {
        FakeMail mail = FakeMail.builder().build();

        testee.service(mail);

        assertThat(mail.getPerRecipientSpecificHeaders())
            .isEqualTo(new PerRecipientHeaders());
    }

    @Test
    public void serviceShouldAddPerRecipientDeliveredToSpecificHeader() throws Exception {
        FakeMail mail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
            .build();

        testee.service(mail);

        PerRecipientHeaders expectedResult = new PerRecipientHeaders()
            .addHeaderForRecipient(
                PerRecipientHeaders.Header.builder()
                    .name(AddDeliveredToHeader.DELIVERED_TO)
                    .value(MailAddressFixture.ANY_AT_JAMES.asString())
                    .build(),
                MailAddressFixture.ANY_AT_JAMES)
            .addHeaderForRecipient(
                PerRecipientHeaders.Header.builder()
                    .name(AddDeliveredToHeader.DELIVERED_TO)
                    .value(MailAddressFixture.OTHER_AT_JAMES.asString())
                    .build(),
                MailAddressFixture.OTHER_AT_JAMES);

        assertThat(mail.getPerRecipientSpecificHeaders())
            .isEqualTo(expectedResult);
    }

}
