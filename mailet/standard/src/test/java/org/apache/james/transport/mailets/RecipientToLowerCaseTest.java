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

import java.util.Collection;

import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Before;
import org.junit.Test;

public class RecipientToLowerCaseTest {

    private RecipientToLowerCase testee;

    @Before
    public void setUp() {
        testee = new RecipientToLowerCase();
    }

    @Test
    public void serviceShouldPutRecipientToLowerCase() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .recipient(new MailAddress("THienan1234@gmail.com"))
            .build();

        testee.service(fakeMail);

        Collection<MailAddress> recipients = fakeMail.getRecipients();

        assertThat(recipients)
            .extracting(MailAddress::asString)
            .containsOnly("thienan1234@gmail.com");
    }

    @Test
    public void serviceShouldHaveNoEffectWhenNoRecipient() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .build();

        testee.service(fakeMail);

        assertThat(fakeMail.getRecipients())
            .isEmpty();
    }
}
