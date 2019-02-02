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

package org.apache.james.transport.matchers.dlp;

import static org.apache.mailet.base.MailAddressFixture.ANY_AT_JAMES;
import static org.apache.mailet.base.MailAddressFixture.JAMES_APACHE_ORG;
import static org.apache.mailet.base.MailAddressFixture.JAMES_APACHE_ORG_DOMAIN;
import static org.apache.mailet.base.MailAddressFixture.OTHER_AT_JAMES;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT2;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT3;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.dlp.api.DLPConfigurationItem.Id;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;

class DlpTest {

    private static final DlpRulesLoader MATCH_ALL_FOR_ALL_DOMAINS = (Domain domain) -> DlpDomainRules.matchAll();
    private static final DlpRulesLoader MATCH_NOTHING_FOR_ALL_DOMAINS = (Domain domain) -> DlpDomainRules.matchNothing();

    private static DlpRulesLoader asRulesLoaderFor(Domain domain, DlpDomainRules rules) {
        return (Domain d) -> Optional
                .of(d)
                .filter(domain::equals)
                .map(ignore -> rules)
                .orElse(DlpDomainRules.matchNothing());
    }

    @Test
    void matchShouldReturnEmptyWhenNoRecipient() throws Exception {
        Dlp dlp = new Dlp(MATCH_ALL_FOR_ALL_DOMAINS);

        FakeMail mail = FakeMail.builder().sender(RECIPIENT1).build();

        assertThat(dlp.match(mail)).isEmpty();
    }

    @Test
    void matchShouldReturnEmptyWhenNoSender() throws Exception {
        Dlp dlp = new Dlp(MATCH_ALL_FOR_ALL_DOMAINS);

        FakeMail mail = FakeMail.builder().recipient(RECIPIENT1).build();

        assertThat(dlp.match(mail)).isEmpty();
    }

    @Test
    void matchShouldThrowOnNullMail() {
        Dlp dlp = new Dlp(MATCH_ALL_FOR_ALL_DOMAINS);

        assertThatThrownBy(() -> dlp.match(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void matchShouldReturnEmptyWhenNoRuleMatch() throws Exception {
        Dlp dlp = new Dlp(MATCH_NOTHING_FOR_ALL_DOMAINS);

        FakeMail mail = FakeMail.builder()
            .sender(ANY_AT_JAMES)
            .recipient(RECIPIENT1)
            .recipient(RECIPIENT2)
            .build();

        assertThat(dlp.match(mail)).isEmpty();
    }

    @Test
    void matchSenderShouldReturnRecipientsWhenEnvelopSenderMatches() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder().senderRule(Id.of("match sender"), Pattern.compile(ANY_AT_JAMES.asString())).build()));

        FakeMail mail = FakeMail.builder().sender(ANY_AT_JAMES).recipient(RECIPIENT1).build();

        assertThat(dlp.match(mail)).contains(RECIPIENT1);
    }

    @Test
    void nullSenderShouldBeIgnored() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder().recipientRule(Id.of("match all recipient"), Pattern.compile(".*")).build()));

        FakeMail mail = FakeMail.builder().sender(MailAddress.nullSender()).recipient(RECIPIENT1).build();

        assertThat(dlp.match(mail)).isEmpty();
    }

    @Test
    void matchSenderShouldReturnRecipientsWhenFromHeaderMatches() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder().senderRule(Id.of("match sender"), Pattern.compile(ANY_AT_JAMES.asString())).build()));

        FakeMail mail = FakeMail
            .builder()
            .sender(OTHER_AT_JAMES)
            .recipient(RECIPIENT1)
            .mimeMessage(MimeMessageBuilder
                .mimeMessageBuilder()
                .addFrom(ANY_AT_JAMES.toInternetAddress()))
            .build();

        assertThat(dlp.match(mail)).contains(RECIPIENT1);
    }

    @Test
    void matchShouldReturnRecipientsWhenEnvelopRecipientsMatches() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder().recipientRule(Id.of("match recipient"), Pattern.compile(RECIPIENT1.asString())).build()));

        FakeMail mail = FakeMail.builder()
            .sender(ANY_AT_JAMES)
            .recipient(RECIPIENT1)
            .recipient(RECIPIENT2)
            .build();

        assertThat(dlp.match(mail)).contains(RECIPIENT1, RECIPIENT2);
    }

    @Test
    void matchShouldReturnRecipientsWhenToHeaderMatches() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder().recipientRule(Id.of("match recipient"), Pattern.compile(RECIPIENT2.asString())).build()));

        FakeMail mail = FakeMail
            .builder()
            .sender(OTHER_AT_JAMES)
            .recipient(RECIPIENT1)
            .mimeMessage(MimeMessageBuilder
                .mimeMessageBuilder()
                .addToRecipient(RECIPIENT2.toInternetAddress()))
            .build();

        assertThat(dlp.match(mail)).contains(RECIPIENT1);
    }

    @Test
    void matchShouldReturnRecipientsWhenCcHeaderMatches() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder().recipientRule(Id.of("match recipient"), Pattern.compile(RECIPIENT2.asString())).build()));

        FakeMail mail = FakeMail
            .builder()
            .sender(OTHER_AT_JAMES)
            .recipient(RECIPIENT1)
            .mimeMessage(MimeMessageBuilder
                .mimeMessageBuilder()
                .addCcRecipient(RECIPIENT2.toInternetAddress()))
            .build();

        assertThat(dlp.match(mail)).contains(RECIPIENT1);
    }

    @Test
    void matchShouldReturnRecipientsWhenBccHeaderMatches() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder().recipientRule(Id.of("match recipient"), Pattern.compile(RECIPIENT2.asString())).build()));

        FakeMail mail = FakeMail
            .builder()
            .sender(OTHER_AT_JAMES)
            .recipient(RECIPIENT1)
            .mimeMessage(MimeMessageBuilder
                .mimeMessageBuilder()
                .addBccRecipient(RECIPIENT2.toInternetAddress()))
            .build();

        assertThat(dlp.match(mail)).contains(RECIPIENT1);
    }

    @Test
    void matchShouldReturnRecipientsWhenSubjectHeaderMatches() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder().contentRule(Id.of("match subject"), Pattern.compile("pony")).build()));

        FakeMail mail = FakeMail
            .builder()
            .sender(OTHER_AT_JAMES)
            .recipient(RECIPIENT1)
            .mimeMessage(MimeMessageBuilder
                .mimeMessageBuilder()
                .setSubject("I just bought a pony"))
            .build();

        assertThat(dlp.match(mail)).contains(RECIPIENT1);
    }

    @Test
    void matchShouldReturnRecipientsWhenMessageBodyMatches() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder().contentRule(Id.of("match content"), Pattern.compile("horse")).build()));

        FakeMail mail = FakeMail
            .builder()
            .sender(OTHER_AT_JAMES)
            .recipient(RECIPIENT1)
            .mimeMessage(MimeMessageBuilder
                .mimeMessageBuilder()
                .setSubject("I just bought a pony")
                .setText("It's actually a horse, not a pony"))
            .build();

        assertThat(dlp.match(mail)).contains(RECIPIENT1);
    }

    @Test
    void matchShouldReturnRecipientsWhenMessageBodyMatchesWithNoSubject() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder().contentRule(Id.of("match content"), Pattern.compile("horse")).build()));

        FakeMail mail = FakeMail
            .builder()
            .sender(OTHER_AT_JAMES)
            .recipient(RECIPIENT1)
            .mimeMessage(MimeMessageBuilder
                .mimeMessageBuilder()
                .setText("It's actually a horse, not a pony"))
            .build();

        assertThat(dlp.match(mail)).contains(RECIPIENT1);
    }

    @Test
    void matchShouldReturnRecipientsWhenMessageMultipartBodyMatches() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder().contentRule(Id.of("match content"), Pattern.compile("horse")).build()));

        FakeMail mail = FakeMail
            .builder()
            .sender(OTHER_AT_JAMES)
            .recipient(RECIPIENT1)
            .mimeMessage(MimeMessageBuilder
                .mimeMessageBuilder()
                .setSubject("I just bought a pony")
                .setMultipartWithBodyParts(
                    MimeMessageBuilder.bodyPartBuilder()
                        .data("It's actually a donkey, not a pony"),
                    MimeMessageBuilder.bodyPartBuilder()
                        .data("What??? No it's a horse!!!")))
            .build();

        assertThat(dlp.match(mail)).contains(RECIPIENT1);
    }

    @Test
    void matchShouldReturnRecipientsWhenEmbeddedMessageContentMatches() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder().contentRule(Id.of("match content"), Pattern.compile("horse")).build()));

        FakeMail mail = FakeMail
            .builder()
            .sender(OTHER_AT_JAMES)
            .recipient(RECIPIENT1)
            .mimeMessage(MimeMessageBuilder
                .mimeMessageBuilder()
                .setSubject("I just bought a pony")
                .setContent(
                    MimeMessageBuilder.multipartBuilder()
                        .addBody(
                    MimeMessageBuilder.bodyPartBuilder()
                        .data("It's actually a donkey, not a pony"))
                        .addBody(
                    MimeMessageBuilder.mimeMessageBuilder()
                        .setSender(RECIPIENT2.asString())
                        .setSubject("Embedded message with truth")
                        .setText("What??? No it's a horse!!!"))))
            .build();

        assertThat(dlp.match(mail)).contains(RECIPIENT1);
    }

    @Test
    void matchShouldReturnEmptyWhenEmbeddedSenderMatchesInSubMessage() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder()
                    .senderRule(Id.of("match content"), Pattern.compile(RECIPIENT2.asString()))
                    .build()));

        FakeMail mail = FakeMail
            .builder()
            .sender(OTHER_AT_JAMES)
            .recipient(RECIPIENT1)
            .mimeMessage(MimeMessageBuilder
                .mimeMessageBuilder()
                .setSubject("I just bought a pony")
                .setSender(RECIPIENT1.asString())
                .setContent(
                    MimeMessageBuilder.multipartBuilder()
                        .addBody(
                            MimeMessageBuilder.bodyPartBuilder()
                                .data("It's actually a donkey, not a pony"))
                        .addBody(
                            MimeMessageBuilder.mimeMessageBuilder()
                                .setSender(RECIPIENT2.asString())
                                .setSubject("Embedded message with truth")
                                .setText("What??? No it's a horse!!!"))))
            .build();

        assertThat(dlp.match(mail)).isEmpty();
    }

    @Test
    void matchShouldReturnEmptyWhenEmbeddedRecipientMatchesInSubMessage() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder()
                    .recipientRule(Id.of("match content"), Pattern.compile(RECIPIENT2.asString()))
                    .build()));

        FakeMail mail = FakeMail
            .builder()
            .sender(OTHER_AT_JAMES)
            .recipient(RECIPIENT1)
            .mimeMessage(MimeMessageBuilder
                .mimeMessageBuilder()
                .setSubject("I just bought a pony")
                .setSender(RECIPIENT1.asString())
                .setContent(
                    MimeMessageBuilder.multipartBuilder()
                        .addBody(
                            MimeMessageBuilder.bodyPartBuilder()
                                .data("It's actually a donkey, not a pony"))
                        .addBody(
                            MimeMessageBuilder.mimeMessageBuilder()
                                .addToRecipient(RECIPIENT1.asString())
                                .setSubject("Embedded message with truth")
                                .setText("What??? No it's a horse!!!"))))
            .build();

        assertThat(dlp.match(mail)).isEmpty();
    }

    @Test
    void matchShouldReturnRecipientsWhenEncodedSubjectMatchesContentRule() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder()
                    .contentRule(Id.of("match content"), Pattern.compile("poné"))
                    .build()));

        FakeMail mail = FakeMail
            .builder()
            .sender(OTHER_AT_JAMES)
            .recipient(RECIPIENT1)
            .mimeMessage(MimeMessageBuilder
                .mimeMessageBuilder()
                .setSubject("=?ISO-8859-1?Q?I_just_bought_a_pon=E9?=")
                .setSender(RECIPIENT1.asString())
                .setText("Meaningless text"))
            .build();

        assertThat(dlp.match(mail)).containsOnly(RECIPIENT1);
    }

    @Test
    void matchShouldReturnRecipientsWhenEncodedTextMatchesContentRule() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder()
                    .contentRule(Id.of("match content"), Pattern.compile("poné"))
                    .build()));

        FakeMail mail = FakeMail
            .builder()
            .sender(OTHER_AT_JAMES)
            .recipient(RECIPIENT1)
            .mimeMessage(MimeMessageBuilder
                .mimeMessageBuilder()
                .setSubject("you know what ?")
                .setSender(RECIPIENT1.asString())
                .setText("I bought a poné", "text/plain; charset=" + StandardCharsets.ISO_8859_1.name()))
            .build();

        assertThat(dlp.match(mail)).containsOnly(RECIPIENT1);
    }

    @Test
    void matchShouldReturnRecipientsWhenRulesMatchesAMailboxRecipient() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder()
                    .recipientRule(Id.of("id1"), Pattern.compile(RECIPIENT1.asString()))
                    .build()));

        MimeMessageBuilder meaninglessText = MimeMessageBuilder
            .mimeMessageBuilder()
            .addToRecipient("Name <" + RECIPIENT1.asString() + " >")
            .setSubject("=?ISO-8859-1?Q?I_just_bought_a_pon=E9?=")
            .setText("Meaningless text");

        FakeMail mail = FakeMail
            .builder()
            .sender(OTHER_AT_JAMES)
            .recipient(RECIPIENT2)
            .mimeMessage(meaninglessText)
            .build();

        assertThat(dlp.match(mail)).containsOnly(RECIPIENT2);
    }

    @Test
    void matchShouldReturnRecipientsWhenRulesMatchesAQuotedPrintableRecipient() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder()
                    .recipientRule(Id.of("id1"), Pattern.compile("Benoît"))
                    .build()));

        MimeMessageBuilder meaninglessText = MimeMessageBuilder
            .mimeMessageBuilder()
            .addToRecipient("=?ISO-8859-1?Q?Beno=EEt_TELLIER?=")
            .setSubject("=?ISO-8859-1?Q?I_just_bought_a_pon=E9?=")
            .setText("Meaningless text");

        FakeMail mail = FakeMail
            .builder()
            .sender(OTHER_AT_JAMES)
            .recipient(RECIPIENT2)
            .mimeMessage(meaninglessText)
            .build();

        assertThat(dlp.match(mail)).containsOnly(RECIPIENT2);
    }

    @Test
    void matchShouldReturnRecipientsWhenRulesMatchesAQuotedPrintableSender() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder()
                    .senderRule(Id.of("id1"), Pattern.compile("Benoît"))
                    .build()));

        MimeMessageBuilder meaninglessText = MimeMessageBuilder
            .mimeMessageBuilder()
            .addFrom("=?ISO-8859-1?Q?Beno=EEt_TELLIER?=")
            .setSubject("=?ISO-8859-1?Q?I_just_bought_a_pon=E9?=")
            .setText("Meaningless text");

        FakeMail mail = FakeMail
            .builder()
            .sender(OTHER_AT_JAMES)
            .recipient(RECIPIENT2)
            .mimeMessage(meaninglessText)
            .build();

        assertThat(dlp.match(mail)).containsOnly(RECIPIENT2);
    }

    @Test
    void matchShouldAttachMatchingRuleNameToMail() throws Exception {
        Dlp dlp = new Dlp(
            asRulesLoaderFor(
                JAMES_APACHE_ORG_DOMAIN,
                DlpDomainRules.builder()
                    .recipientRule(Id.of("should not match recipient"), Pattern.compile(RECIPIENT3.asString()))
                    .senderRule(Id.of("should match sender"), Pattern.compile(JAMES_APACHE_ORG))
                    .build()));

        FakeMail mail = FakeMail.builder()
            .sender(ANY_AT_JAMES)
            .recipient(RECIPIENT1)
            .recipient(RECIPIENT2)
            .build();

        dlp.match(mail);

        assertThat(mail.getAttribute("DlpMatchedRule")).isEqualTo("should match sender");
    }

}