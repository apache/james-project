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

package org.apache.james.transport.mailets.redirect;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.MailAddress;
import org.junit.Test;

public class SpecialAddressTest {

    @Test
    public void senderSpecialAddressShouldMatchExpectedValue() {
        assertThat(SpecialAddress.AddressMarker.SENDER).isEqualTo("sender@address.marker");
    }

    @Test
    public void reverserPathSpecialAddressShouldMatchExpectedValue() {
        assertThat(SpecialAddress.AddressMarker.REVERSE_PATH).isEqualTo("reverse.path@address.marker");
    }

    @Test
    public void fromSpecialAddressShouldMatchExpectedValue() {
        assertThat(SpecialAddress.AddressMarker.FROM).isEqualTo("from@address.marker");
    }

    @Test
    public void replyToSpecialAddressShouldMatchExpectedValue() {
        assertThat(SpecialAddress.AddressMarker.REPLY_TO).isEqualTo("reply.to@address.marker");
    }

    @Test
    public void toSpecialAddressShouldMatchExpectedValue() {
        assertThat(SpecialAddress.AddressMarker.TO).isEqualTo("to@address.marker");
    }

    @Test
    public void recipientsSpecialAddressShouldMatchExpectedValue() {
        assertThat(SpecialAddress.AddressMarker.RECIPIENTS).isEqualTo("recipients@address.marker");
    }

    @Test
    public void deleteSpecialAddressShouldMatchExpectedValue() {
        assertThat(SpecialAddress.AddressMarker.DELETE).isEqualTo("delete@address.marker");
    }

    @Test
    public void unalteredSpecialAddressShouldMatchExpectedValue() {
        assertThat(SpecialAddress.AddressMarker.UNALTERED).isEqualTo("unaltered@address.marker");
    }

    @Test
    public void nullSpecialAddressShouldMatchExpectedValue() {
        assertThat(SpecialAddress.AddressMarker.NULL).isEqualTo("null@address.marker");
    }

    @Test
    public void isSpecialAddressShouldReturnTrueWhenMatchingSpecialDomain() throws Exception {
        assertThat(SpecialAddress.isSpecialAddress(new MailAddress("user", "address.marker"))).isTrue();
    }

    @Test
    public void isSpecialAddressShouldReturnFalseWhenNotMatchingSpecialDomain() throws Exception {
        assertThat(SpecialAddress.isSpecialAddress(new MailAddress("user", "james.org"))).isFalse();
    }
}
