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

package org.apache.james.mailbox.opensearch.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

class HeaderCollectionTest {

    static class UTF8FromHeaderTestSource implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            return Stream.of(
                Arguments.of("=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fmartin@linagora.com>, Graham CROSMARIE <gcrosmarie@linagora.com>", "Frédéric MARTIN"),
                Arguments.of("=?UTF-8?Q?=C3=9Csteli=C4=9Fhan_Ma=C5=9Frapa?= <ustelighanmasrapa@domain.tld>", "Üsteliğhan Maşrapa"),
                Arguments.of("=?UTF-8?Q?Ke=C5=9Ffet_Turizm?= <kesfetturizm@domain.tld>", "Keşfet Turizm"),
                Arguments.of("=?UTF-8?Q?MODAL=C4=B0F?= <modalif@domain.tld>", "MODALİF"));
        }
    }

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    @Test
    void simpleValueAddressHeaderShouldBeAddedToTheAddressSet() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("To", "ben.tellier@linagora.com"))
            .build();

        assertThat(headerCollection.getToAddressSet())
            .containsOnly(new EMailer(Optional.empty(), "ben.tellier@linagora.com", "linagora.com"));
    }

    @Test
    void comaSeparatedAddressShouldBeBothAddedToTheAddressSet() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("To", "ben.tellier@linagora.com, btellier@minet.net"))
            .build();

        assertThat(headerCollection.getToAddressSet())
            .containsOnly(
                new EMailer(Optional.empty(), "ben.tellier@linagora.com", "linagora.com"),
                new EMailer(Optional.empty(), "btellier@minet.net", "minet.net"));
    }

    @Test
    void addressesOfTwoFieldsHavingTheSameNameShouldBeMerged() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("To", "ben.tellier@linagora.com"))
            .add(new FieldImpl("To", "ben.tellier@linagora.com, btellier@minet.net"))
            .build();

        assertThat(headerCollection.getToAddressSet())
            .containsOnly(
                new EMailer(Optional.empty(), "ben.tellier@linagora.com", "linagora.com"),
                new EMailer(Optional.empty(), "btellier@minet.net", "minet.net"));
    }

    @Test
    void shouldNormalizeSubject() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("Subject", "Re: test"))
            .build();

        assertThat(headerCollection.getSubjectSet())
            .containsOnly("test");
    }

    @Test
    void displayNamesShouldBeRetreived() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("To", "Christophe Hamerling <chri.hamerling@linagora.com>"))
            .build();

        assertThat(headerCollection.getToAddressSet())
            .containsOnly(new EMailer(Optional.of("Christophe Hamerling"), "chri.hamerling@linagora.com", "linagora.com"));
    }

    @ParameterizedTest
    @ArgumentsSource(UTF8FromHeaderTestSource.class)
    void displayNamesShouldBeRetrievedWhenEncodedWord(String encodedFromHeader, String nameOfFromAddress) {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("From", encodedFromHeader))
            .build();

        assertThat(headerCollection.getFromAddressSet())
            .extracting(EMailer::getName)
            .contains(Optional.ofNullable(nameOfFromAddress));
    }

    @Test
    void getHeadersShouldDecodeValues() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("From", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fmartin@linagora.com>, Graham CROSMARIE <gcrosmarie@linagora.com>"))
            .build();

        assertThat(headerCollection.getHeaders())
            .containsExactly(new HeaderCollection.Header("from",
                "Frédéric MARTIN <fmartin@linagora.com>, Graham CROSMARIE <gcrosmarie@linagora.com>"));
    }

    @Test
    void getHeadersShouldNotIgnoreHeadersWithDots() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("a.b.c", "value"))
            .build();

        assertThat(headerCollection.getHeaders())
            .containsExactly(new HeaderCollection.Header("a.b.c", "value"));
    }

    @Test
    void addressWithTwoDisplayNamesOnTheSameFieldShouldBeRetrieved() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("From", "Christophe Hamerling <chri.hamerling@linagora.com>, Graham CROSMARIE <grah.crosmarie@linagora.com>"))
            .build();

        assertThat(headerCollection.getFromAddressSet())
            .containsOnly(new EMailer(Optional.of("Christophe Hamerling"), "chri.hamerling@linagora.com", "linagora.com"),
                new EMailer(Optional.of("Graham CROSMARIE"), "grah.crosmarie@linagora.com", "linagora.com"));
    }

    @Test
    void foldedFromHeaderShouldBeSupported() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("From", "Christophe Hamerling <chri.hamerling@linagora.com>,\r\n" +
                " Graham CROSMARIE <grah.crosmarie@linagora.com>"))
            .build();

        assertThat(headerCollection.getFromAddressSet())
            .containsOnly(new EMailer(Optional.of("Christophe Hamerling"), "chri.hamerling@linagora.com", "linagora.com"),
                new EMailer(Optional.of("Graham CROSMARIE"), "grah.crosmarie@linagora.com", "linagora.com"));
    }

    @Test
    void foldedHeaderShouldBeSupported() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("From", "Christophe Hamerling <chri.hamerling@linagora.com>,\r\n" +
                " Graham CROSMARIE <grah.crosmarie@linagora.com>"))
            .build();


        assertThat(headerCollection.getHeaders())
            .containsExactly(new HeaderCollection.Header("from",
                "Christophe Hamerling <chri.hamerling@linagora.com>, Graham CROSMARIE <grah.crosmarie@linagora.com>"));
    }

    @Test
    void mixingAddressWithDisplayNamesWithOthersShouldBeAllowed() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("To", "Christophe Hamerling <chri.hamerling@linagora.com>, grah.crosmarie@linagora.com"))
            .build();

        assertThat(headerCollection.getToAddressSet())
            .containsOnly(new EMailer(Optional.of("Christophe Hamerling"), "chri.hamerling@linagora.com", "linagora.com"),
                new EMailer(Optional.empty(), "grah.crosmarie@linagora.com", "linagora.com"));
    }

    @Test
    void displayNamesShouldBeRetreivedOnCc() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("Cc", "Christophe Hamerling <chri.hamerling@linagora.com>"))
            .build();

        assertThat(headerCollection.getCcAddressSet())
            .containsOnly(new EMailer(Optional.of("Christophe Hamerling"), "chri.hamerling@linagora.com", "linagora.com"));
    }

    @Test
    void displayNamesShouldBeRetreivedOnReplyTo() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("Reply-To", "Christophe Hamerling <chri.hamerling@linagora.com>"))
            .build();

        assertThat(headerCollection.getReplyToAddressSet())
            .containsOnly(new EMailer(Optional.of("Christophe Hamerling"), "chri.hamerling@linagora.com", "linagora.com"));
    }

    @Test
    void displayNamesShouldBeRetreivedOnBcc() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("Bcc", "Christophe Hamerling <chri.hamerling@linagora.com>"))
            .build();

        assertThat(headerCollection.getBccAddressSet())
            .containsOnly(new EMailer(Optional.of("Christophe Hamerling"), "chri.hamerling@linagora.com", "linagora.com"));
    }

    @Test
    void unclosedAddressSubpartShouldBeWellHandled() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("Bcc", "Mickey <tricky@mouse.com"))
            .build();

        assertThat(headerCollection.getBccAddressSet())
            .containsOnly(new EMailer(Optional.of("Mickey"), "tricky@mouse.com", "mouse.com"));
    }

    @Test
    void notComaSeparatedAddressSubpartShouldBeWellHandled() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("Bcc", "Mickey <tricky@mouse.com> Miny<hello@polo.com>"))
            .build();

        assertThat(headerCollection.getBccAddressSet())
            .containsOnly(new EMailer(Optional.of("Mickey"), "tricky@mouse.com", "mouse.com"),
                new EMailer(Optional.of("Miny"), "hello@polo.com", "polo.com"));
    }

    @Test
    void notSeparatedAddressSubpartShouldBeWellHandled() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("Bcc", "Mickey <tricky@mouse.com>Miny<hello@polo.com>"))
            .build();

        assertThat(headerCollection.getBccAddressSet())
            .containsOnly(new EMailer(Optional.of("Mickey"), "tricky@mouse.com", "mouse.com"),
                new EMailer(Optional.of("Miny"), "hello@polo.com", "polo.com"));
    }

    @Test
    void dateShouldBeRetreived() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("Date", "Thu, 4 Jun 2015 06:08:41 +0200"))
            .build();

        assertThat(DATE_TIME_FORMATTER.format(headerCollection.getSentDate().get()))
            .isEqualTo("2015/06/04 06:08:41");
    }

    @Test
    void partialYearShouldBeCompleted() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("Date", "Thu, 4 Jun 15 06:08:41 +0200"))
            .build();

        assertThat(DATE_TIME_FORMATTER.format(headerCollection.getSentDate().get()))
            .isEqualTo("2015/06/04 06:08:41");
    }

    @Test
    void nonStandardDatesShouldBeRetreived() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("Date", "Thu, 4 Jun 2015 06:08:41 +0200 (UTC)"))
            .build();

        assertThat(DATE_TIME_FORMATTER.format(headerCollection.getSentDate().get()))
            .isEqualTo("2015/06/04 06:08:41");
    }

    @Test
    void dateShouldBeAbsentOnInvalidHeader() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("Date", "Not a date"))
            .build();

        assertThat(headerCollection.getSentDate().isPresent())
            .isFalse();
    }

    @Test
    void subjectsShouldBeWellRetrieved() {
        String subject = "A fantastic OpenSearch module will be available soon for JAMES";
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("Subject", subject))
            .build();

        assertThat(headerCollection.getSubjectSet()).containsOnly("A fantastic OpenSearch module will be available soon for JAMES");
    }

    @Test
    void getMessageIDShouldReturnMessageIdValue() {
        String messageID = "<abc@123>";
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("Message-ID", messageID))
            .build();

        assertThat(headerCollection.getMessageID())
            .contains(messageID);
    }

    @Test
    void getMessageIDShouldReturnLatestEncounteredMessageIdValue() {
        String messageID = "<abc@123>";
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("Message-ID", "<other@toto.com>"))
            .add(new FieldImpl("Message-ID", messageID))
            .build();

        assertThat(headerCollection.getMessageID())
            .contains(messageID);
    }

    @Test
    void getMessageIDShouldReturnEmptyWhenNoMessageId() {
        HeaderCollection headerCollection = HeaderCollection.builder()
            .add(new FieldImpl("Other", "value"))
            .build();

        assertThat(headerCollection.getMessageID())
            .isEmpty();
    }

    @Test
    void nullFieldShouldThrow() {
        assertThatThrownBy(() -> HeaderCollection.builder().add(null).build())
            .isInstanceOf(NullPointerException.class);
    }

}
