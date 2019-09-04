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

package org.apache.james.transport.mailets.remote.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.internet.InternetAddress;

import org.apache.mailet.base.MailAddressFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;

public class InternetAddressConverterTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void convertShouldWorkWithEmptyAddressList() {
        assertThat(InternetAddressConverter.convert(ImmutableList.of())).isEmpty();
    }

    @Test
    public void convertShouldThrowOnNullAddress() {
        expectedException.expect(NullPointerException.class);

        InternetAddressConverter.convert(null);
    }

    @Test
    public void convertShouldWorkWithOneAddress() throws Exception {
        assertThat(InternetAddressConverter.convert(ImmutableList.of(MailAddressFixture.ANY_AT_JAMES)))
            .containsOnly(new InternetAddress(MailAddressFixture.ANY_AT_JAMES.asString()));
    }

    @Test
    public void convertShouldWorkWithTwoAddress() throws Exception {
        assertThat(InternetAddressConverter.convert(ImmutableList.of(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)))
            .containsOnly(new InternetAddress(MailAddressFixture.ANY_AT_JAMES.asString()), new InternetAddress(MailAddressFixture.OTHER_AT_JAMES.asString()));
    }
}
