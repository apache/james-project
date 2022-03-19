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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.MailetContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public class AddressExtractorTest {

    private MailAddress postmaster;
    private MailetContext mailetContext;

    @BeforeEach
    void setup() throws Exception {
        postmaster = new MailAddress("postmaster", "james.org");
        mailetContext = mock(MailetContext.class);
        when(mailetContext.getPostmaster())
            .thenReturn(postmaster);

    }

    @Test
    void extractShouldThrowWhenMailetContextIsNull() {
        assertThatThrownBy(() -> AddressExtractor.withContext(null)
                .extract(Optional.of("user@james.org, user2@james.org")))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void extractShouldThrowWhenAllowedSpecialsIsNotGiven() {
        assertThatThrownBy(() -> AddressExtractor.withContext(mailetContext)
                .extract(Optional.of("user@james.org, user2@james.org")))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void extractShouldThrowWhenAllowedSpecialsIsNull() {
        assertThatThrownBy(() -> AddressExtractor.withContext(mailetContext)
                .allowedSpecials(null)
                .extract(Optional.of("user@james.org, user2@james.org")))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getSpecialAddressShouldThrowWhenMailetContextIsNull() {
        assertThatThrownBy(() -> AddressExtractor.withContext(null)
                .getSpecialAddress("user@james.org, user2@james.org"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getSpecialAddressShouldThrowWhenAllowedSpecialsIsNotGiven() {
        assertThatThrownBy(() -> AddressExtractor.withContext(mailetContext)
                .getSpecialAddress("user@james.org, user2@james.org"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getSpecialAddressShouldThrowWhenAllowedSpecialsIsNull() {
        assertThatThrownBy(() -> AddressExtractor.withContext(mailetContext)
                .allowedSpecials(null)
                .getSpecialAddress("user@james.org, user2@james.org"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void extractShouldReturnEmptyWhenAddressListIsAbsent() throws Exception {
        List<MailAddress> extract = AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.<String>of())
                .extract(Optional.empty());

        assertThat(extract).isEmpty();;
    }

    @Test
    void extractShouldReturnListWhenParsingSucceed() throws Exception {
        List<MailAddress> extract = AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.<String>of())
                .extract(Optional.of("user@james.org, user2@james.org"));

        assertThat(extract).containsOnly(new MailAddress("user", "james.org"),
                new MailAddress("user2", "james.org"));
    }

    @Test
    void extractShouldReturnSpecialAddressesWhenAddressesAreSpecial() throws Exception {
        List<MailAddress> extract = AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.<String>of("postmaster", "to"))
                .extract(Optional.of("postmaster, to"));

        assertThat(extract).containsOnly(new MailAddress("postmaster", "james.org"),
                new MailAddress("to", "address.marker"));
    }

    @Test
    void extractShouldThrowWhenParsingFail() {
        assertThatThrownBy(() -> AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.<String>of())
                .extract(Optional.of("user@james@org")))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getSpecialAddressShouldReturnAbsentWhenAddressIsNull() throws Exception {
        Optional<MailAddress> specialAddress = AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.<String>of())
                .getSpecialAddress(null);
        assertThat(specialAddress).isEmpty();
    }

    @Test
    void getSpecialAddressShouldReturnAbsentWhenAddressIsEmpty() throws Exception {
        Optional<MailAddress> specialAddress = AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.<String>of())
                .getSpecialAddress("");
        assertThat(specialAddress).isEmpty();
    }

    @Test
    void getSpecialAddressShouldReturnAbsentWhenAddressIsNotSpecial() throws Exception {
        Optional<MailAddress> specialAddress = AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.<String>of())
                .getSpecialAddress("user@james.org");
        assertThat(specialAddress).isEmpty();
    }

    @Test
    void getSpecialAddressShouldReturnPostmasterWhenAddressMatchesPostmasterSpecialAddress() throws Exception {
        Optional<MailAddress> specialAddress = AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.of("postmaster"))
                .getSpecialAddress("postmaster");
        assertThat(specialAddress).contains(postmaster);
    }

    @Test
    void getSpecialAddressShouldReturnSenderWhenAddressMatchesSenderSpecialAddress() throws Exception {
        Optional<MailAddress> specialAddress = AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.of("sender"))
                .getSpecialAddress("sender");
        assertThat(specialAddress).contains(new MailAddress("sender", "address.marker"));
    }

    @Test
    void getSpecialAddressShouldReturnReversePathWhenAddressMatchesReversePathSpecialAddress() throws Exception {
        Optional<MailAddress> specialAddress = AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.of("reversepath"))
                .getSpecialAddress("reversepath");
        assertThat(specialAddress).contains(new MailAddress("reverse.path", "address.marker"));
    }

    @Test
    void getSpecialAddressShouldReturnFromWhenAddressMatchesFromSpecialAddress() throws Exception {
        Optional<MailAddress> specialAddress = AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.of("from"))
                .getSpecialAddress("from");
        assertThat(specialAddress).contains(new MailAddress("from", "address.marker"));
    }

    @Test
    void getSpecialAddressShouldReturnReplyToWhenAddressMatchesReplyToSpecialAddress() throws Exception {
        Optional<MailAddress> specialAddress = AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.of("replyto"))
                .getSpecialAddress("replyto");
        assertThat(specialAddress).contains(new MailAddress("reply.to", "address.marker"));
    }

    @Test
    void getSpecialAddressShouldReturnToWhenAddressMatchesToSpecialAddress() throws Exception {
        Optional<MailAddress> specialAddress = AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.of("to"))
                .getSpecialAddress("to");
        assertThat(specialAddress).contains(new MailAddress("to", "address.marker"));
    }

    @Test
    void getSpecialAddressShouldReturnRecipientsWhenAddressMatchesRecipientsSpecialAddress() throws Exception {
        Optional<MailAddress> specialAddress = AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.of("recipients"))
                .getSpecialAddress("recipients");
        assertThat(specialAddress).contains(new MailAddress("recipients", "address.marker"));
    }

    @Test
    void getSpecialAddressShouldReturnDeleteWhenAddressMatchesDeleteSpecialAddress() throws Exception {
        Optional<MailAddress> specialAddress = AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.of("delete"))
                .getSpecialAddress("delete");
        assertThat(specialAddress).contains(new MailAddress("delete", "address.marker"));
    }

    @Test
    void getSpecialAddressShouldReturnUnalteredWhenAddressMatchesUnalteredSpecialAddress() throws Exception {
        Optional<MailAddress> specialAddress = AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.of("unaltered"))
                .getSpecialAddress("unaltered");
        assertThat(specialAddress).contains(new MailAddress("unaltered", "address.marker"));
    }

    @Test
    void getSpecialAddressShouldReturnNullWhenAddressMatchesNullSpecialAddress() throws Exception {
        Optional<MailAddress> specialAddress = AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.of("null"))
                .getSpecialAddress("null");
        assertThat(specialAddress).contains(new MailAddress("null", "address.marker"));
    }

    @Test
    void getSpecialAddressShouldThrowWhenSpecialAddressNotAllowed() {
        assertThatThrownBy(() -> AddressExtractor.withContext(mailetContext)
                .allowedSpecials(ImmutableList.<String>of("notallowed"))
                .getSpecialAddress("postmaster"))
            .isInstanceOf(MessagingException.class);
    }
}
