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

package org.apache.james.mailbox.elasticsearch.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.format.DateTimeFormatter;

import org.junit.Test;

public class HeaderCollectionTest {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    @Test
    public void simpleValueAddressHeaderShouldBeAddedToTheAddressSet() {
        HeaderCollection headerCollection = HeaderCollection.builder().add(new FieldImpl("To", "btellier@linagora.com")).build();
        assertThat(headerCollection.getToAddressSet())
            .containsOnly(new EMailer("btellier@linagora.com", "btellier@linagora.com"));
    }

    @Test
    public void comaSeparatedAddressShouldBeBothAddedToTheAddressSet() {
        HeaderCollection headerCollection = HeaderCollection.builder().add(new FieldImpl("To", "btellier@linagora.com, benwa@minet.net")).build();
        assertThat(headerCollection.getToAddressSet())
            .containsOnly(
                new EMailer("btellier@linagora.com", "btellier@linagora.com"),
                new EMailer("benwa@minet.net", "benwa@minet.net"));
    }

    @Test
    public void addressesOfTwoFieldsHavingTheSameNameShouldBeMerged() {
        HeaderCollection headerCollection = HeaderCollection.builder().add(new FieldImpl("To", "btellier@linagora.com")).add(new FieldImpl("To", "btellier@linagora.com, benwa@minet.net")).build();
        assertThat(headerCollection.getToAddressSet())
            .containsOnly(
                new EMailer("btellier@linagora.com", "btellier@linagora.com"),
                new EMailer("benwa@minet.net", "benwa@minet.net"));
    }

    @Test
    public void displayNamesShouldBeRetreived() {
        HeaderCollection headerCollection = HeaderCollection.builder().add(new FieldImpl("To", "Christophe Hamerling <chamerling@linagora.com>")).build();
        assertThat(headerCollection.getToAddressSet())
            .containsOnly(new EMailer("Christophe Hamerling", "chamerling@linagora.com"));
    }

    @Test
    public void addressWithTwoDisplayNamesOnTheSameFieldShouldBeRetreived() {
        HeaderCollection headerCollection = HeaderCollection.builder().add(new FieldImpl("From", "Christophe Hamerling <chamerling@linagora.com>, Graham CROSMARIE <gcrosmarie@linagora.com>")).build();
        assertThat(headerCollection.getFromAddressSet())
            .containsOnly(new EMailer("Christophe Hamerling", "chamerling@linagora.com"),
                new EMailer("Graham CROSMARIE", "gcrosmarie@linagora.com"));

    }

    @Test
    public void mixingAddressWithDisplayNamesWithOthersShouldBeAllowed() {
        HeaderCollection headerCollection = HeaderCollection.builder().add(new FieldImpl("To", "Christophe Hamerling <chamerling@linagora.com>, gcrosmarie@linagora.com")).build();
        assertThat(headerCollection.getToAddressSet())
            .containsOnly(new EMailer("Christophe Hamerling", "chamerling@linagora.com"),
                new EMailer("gcrosmarie@linagora.com", "gcrosmarie@linagora.com"));
    }

    @Test
    public void displayNamesShouldBeRetreivedOnCc() {
        HeaderCollection headerCollection = HeaderCollection.builder().add(new FieldImpl("Cc", "Christophe Hamerling <chamerling@linagora.com>")).build();
        assertThat(headerCollection.getCcAddressSet())
            .containsOnly(new EMailer("Christophe Hamerling", "chamerling@linagora.com"));
    }

    @Test
    public void displayNamesShouldBeRetreivedOnBcc() {
        HeaderCollection headerCollection = HeaderCollection.builder().add(new FieldImpl("Bcc", "Christophe Hamerling <chamerling@linagora.com>")).build();
        assertThat(headerCollection.getBccAddressSet())
            .containsOnly(new EMailer("Christophe Hamerling", "chamerling@linagora.com"));
    }

    @Test
    public void headerContaingNoAddressShouldBeConsideredBothAsNameAndAddress() {
        HeaderCollection headerCollection = HeaderCollection.builder().add(new FieldImpl("Bcc", "Not an address")).build();
        assertThat(headerCollection.getBccAddressSet())
            .containsOnly(new EMailer("Not an address", "Not an address"));
    }

    @Test
    public void unclosedAddressSubpartShouldBeWellHandled() {
        HeaderCollection headerCollection = HeaderCollection.builder().add(new FieldImpl("Bcc", "Mickey <tricky@mouse.com")).build();
        assertThat(headerCollection.getBccAddressSet())
            .containsOnly(new EMailer("Mickey", "tricky@mouse.com"));
    }

    @Test
    public void notComaSeparatedAddressSubpartShouldBeWellHandled() {
        HeaderCollection headerCollection = HeaderCollection.builder().add(new FieldImpl("Bcc", "Mickey <tricky@mouse.com> Miny<hello@polo.com>")).build();
        assertThat(headerCollection.getBccAddressSet())
            .containsOnly(new EMailer("Mickey", "tricky@mouse.com"),
                new EMailer("Miny", "hello@polo.com"));
    }

    @Test
    public void notSeparatedAddressSubpartShouldBeWellHandled1() {
        HeaderCollection headerCollection = HeaderCollection.builder().add(new FieldImpl("Bcc", "Mickey <tricky@mouse.com>Miny<hello@polo.com>")).build();
        assertThat(headerCollection.getBccAddressSet())
            .containsOnly(new EMailer("Mickey", "tricky@mouse.com"),
                new EMailer("Miny", "hello@polo.com"));
    }

    @Test
    public void dateShouldBeRetreived() {
        HeaderCollection headerCollection = HeaderCollection.builder().add(new FieldImpl("Date", "Thu, 4 Jun 2015 06:08:41 +0200")).build();
        assertThat(DATE_TIME_FORMATTER.format(headerCollection.getSentDate().get()))
            .isEqualTo("2015/06/04 06:08:41");
    }

    @Test
    public void nonStandardDatesShouldBeRetreived() {
        HeaderCollection headerCollection = HeaderCollection.builder().add(new FieldImpl("Date", "Thu, 4 Jun 2015 06:08:41 +0200 (UTC)")).build();
        assertThat(DATE_TIME_FORMATTER.format(headerCollection.getSentDate().get()))
            .isEqualTo("2015/06/04 06:08:41");
    }

    @Test
    public void dateShouldBeAbsentOnInvalidHeader() {
        HeaderCollection headerCollection = HeaderCollection.builder().add(new FieldImpl("Date", "Not a date")).build();
        assertThat(headerCollection.getSentDate().isPresent())
            .isFalse();
    }

    @Test
    public void subjectsShouldBeWellRetrieved() {
        String subject = "A fantastic ElasticSearch module will be available soon for JAMES";
        HeaderCollection headerCollection = HeaderCollection.builder().add(new FieldImpl("Subject", subject)).build();
        assertThat(headerCollection.getSubjectSet()).containsOnly("A fantastic ElasticSearch module will be available soon for JAMES");
    }

    @Test(expected = NullPointerException.class)
    public void nullFieldShouldThrow() {
        HeaderCollection.builder().add(null).build();
    }

    @Test
    public void sanitizeDateStringHeaderValueShouldRemoveCESTPart() {
        assertThat(HeaderCollection.builder()
            .sanitizeDateStringHeaderValue("Thu, 18 Jun 2015 04:09:35 +0200 (CEST)"))
            .isEqualTo("Thu, 18 Jun 2015 04:09:35 +0200");
    }

    @Test
    public void sanitizeDateStringHeaderValueShouldRemoveUTCPart() {
        assertThat(HeaderCollection.builder()
            .sanitizeDateStringHeaderValue("Thu, 18 Jun 2015 04:09:35 +0200  (UTC)  "))
            .isEqualTo("Thu, 18 Jun 2015 04:09:35 +0200");
    }

    @Test
    public void sanitizeDateStringHeaderValueShouldNotChangeAcceptableString() {
        assertThat(HeaderCollection.builder()
            .sanitizeDateStringHeaderValue("Thu, 18 Jun 2015 04:09:35 +0200"))
            .isEqualTo("Thu, 18 Jun 2015 04:09:35 +0200");
    }

    @Test
    public void sanitizeDateStringHeaderValueShouldNotChangeEmptyString() {
        assertThat(HeaderCollection.builder()
            .sanitizeDateStringHeaderValue(""))
            .isEqualTo("");
    }

}
