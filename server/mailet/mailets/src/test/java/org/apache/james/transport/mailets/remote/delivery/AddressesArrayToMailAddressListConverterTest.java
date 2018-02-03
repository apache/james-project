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

import javax.mail.Address;
import javax.mail.internet.InternetAddress;

import org.apache.james.transport.mailets.remote.delivery.AddressesArrayToMailAddressListConverter;
import org.apache.mailet.base.MailAddressFixture;
import org.junit.Test;

public class AddressesArrayToMailAddressListConverterTest {

    private static final String WRONG_INTERNET_ADDRESS = "!!";

    @Test
    public void getAddressesAsMailAddressShouldReturnEmptyOnNull() {
        assertThat(AddressesArrayToMailAddressListConverter.getAddressesAsMailAddress(null)).isEmpty();
    }

    @Test
    public void getAddressesAsMailAddressShouldReturnEmptyOnEmpty() {
        assertThat(AddressesArrayToMailAddressListConverter.getAddressesAsMailAddress(new Address[]{})).isEmpty();
    }

    @Test
    public void getAddressesAsMailAddressShouldWorkWithSingleValue() throws Exception {
        assertThat(AddressesArrayToMailAddressListConverter.getAddressesAsMailAddress(new Address[]{
            new InternetAddress(MailAddressFixture.ANY_AT_JAMES.toString())}))
            .containsOnly(MailAddressFixture.ANY_AT_JAMES);
    }

    @Test
    public void getAddressesAsMailAddressShouldWorkWithTwoValues() throws Exception {
        assertThat(AddressesArrayToMailAddressListConverter.getAddressesAsMailAddress(new Address[]{
            new InternetAddress(MailAddressFixture.ANY_AT_JAMES.toString()),
            new InternetAddress(MailAddressFixture.OTHER_AT_JAMES.toString())}))
            .containsOnly(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES);
    }

    @Test
    public void getAddressesAsMailAddressShouldFilterErrorMailAddress() throws Exception {
        assertThat(AddressesArrayToMailAddressListConverter.getAddressesAsMailAddress(new Address[]{
            new InternetAddress(MailAddressFixture.ANY_AT_JAMES.toString()),
            new InternetAddress(WRONG_INTERNET_ADDRESS)}))
            .containsOnly(MailAddressFixture.ANY_AT_JAMES);
    }
}
