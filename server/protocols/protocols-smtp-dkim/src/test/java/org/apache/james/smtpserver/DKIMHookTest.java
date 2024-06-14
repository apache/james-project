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

package org.apache.james.smtpserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.core.Domain;
import org.apache.james.core.MaybeSender;
import org.apache.james.jdkim.tagvalue.SignatureRecordImpl;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

class DKIMHookTest {
    public static final SignatureRecordImpl SIGNATURE_RECORD_1 = new SignatureRecordImpl("a=rsa-sha256; " +
        "b=mPyQMaWy8a8m1H5AH/ntjNZ/bFh2l1090LieXgOqiawIAFxOoJ9PZwq/0BdBZvypfjXgg27+6TLmm/Ne59Y5X0FZq/wc8VVyWlK0JbCGu7okqbj+cQx84" +
        "y4so2CuIymmLprmnWFggoNw8MaUrkDLhSKEHqLPbvvB0axy471A1ifs4CmFtNo98hk7pGzp8y/4Vxkn3wi01Dw/0cmU/cwywT7p1ut29oXsqasgsG387+d7E" +
        "YxYqUqmUgohdK33gxw5RcuWz7zz5q5PMavLfOFOUh6+4v+xU3qe7ihYQC6iQyhCDHyNO6sVqcZFmK1VOMYfwV38Jf1qKiOblSVZQrRrKQ==; " +
        "s=smtpoutjames; d=linagora.com; v=1; bh=GfF1eYzDvrJ9X9ZwvtyAa0qkS+FH5KN1Jj/lI0gwGzQ=; " +
        "h=from : reply-to : subject : date : to : cc : resent-date : resent-from : resent-sender : resent-to : resent-cc : " +
        "in-reply-to : references : list-id : list-help : list-unsubscribe : list-subscribe : list-post : list-owner : list-archive;");
    public static final SignatureRecordImpl SIGNATURE_RECORD_2 = new SignatureRecordImpl("a=rsa-sha256; " +
        "b=mPyQMaWy8a8m1H5AH/ntjNZ/bFh2l1090LieXgOqiawIAFxOoJ9PZwq/0BdBZvypfjXgg27+6TLmm/Ne59Y5X0FZq/wc8VVyWlK0JbCGu7okqbj+cQx84" +
        "y4so2CuIymmLprmnWFggoNw8MaUrkDLhSKEHqLPbvvB0axy471A1ifs4CmFtNo98hk7pGzp8y/4Vxkn3wi01Dw/0cmU/cwywT7p1ut29oXsqasgsG387+d7E" +
        "YxYqUqmUgohdK33gxw5RcuWz7zz5q5PMavLfOFOUh6+4v+xU3qe7ihYQC6iQyhCDHyNO6sVqcZFmK1VOMYfwV38Jf1qKiOblSVZQrRrKQ==; " +
        "s=smtpoutjames; d=abc.com; v=1; bh=GfF1eYzDvrJ9X9ZwvtyAa0qkS+FH5KN1Jj/lI0gwGzQ=; " +
        "h=from : reply-to : subject : date : to : cc : resent-date : resent-from : resent-sender : resent-to : resent-cc : " +
        "in-reply-to : references : list-id : list-help : list-unsubscribe : list-subscribe : list-post : list-owner : list-archive;");

    @Nested
    class DKIMCheckNeededTest {
        @Test
        void onlyForSenderDomainShouldKeepWhenSenderDomainMatches() throws Exception {
            assertThat(DKIMHook.DKIMCheckNeeded.onlyForSenderDomain(Domain.LOCALHOST)
                .test(FakeMail.builder()
                    .name("mail")
                    .sender("bob@localhost")
                    .build()))
                .isTrue();
        }

        @Test
        void onlyForSenderDomainShouldRejectWhenSenderDomainDoesNotMatches() throws Exception {
            assertThat(DKIMHook.DKIMCheckNeeded.onlyForSenderDomain(Domain.LOCALHOST)
                .test(FakeMail.builder()
                    .name("mail")
                    .sender("bob@other.com")
                    .build()))
                .isFalse();
        }
        
        @Test
        void allShouldAcceptArbitrarySenderDomains() throws Exception {
            assertThat(DKIMHook.DKIMCheckNeeded.ALL
                .test(FakeMail.builder()
                    .name("mail")
                    .sender("bob@other.com")
                    .build()))
                .isTrue();
        }
    }

    @Nested
    class SignatureRecordValidationTest {
        @Test
        void shouldDenyWhenNoDkimRecordsWhenTrue() {
            assertThat(DKIMHook.SignatureRecordValidation.signatureRequired(true)
                .validate(MaybeSender.getMailSender("bob@localhost"), ImmutableList.of()))
                .satisfies(hookResult -> assertThat(hookResult.getResult()).isEqualTo(HookReturnCode.deny()))
                .satisfies(hookResult -> assertThat(hookResult.getSmtpDescription()).isEqualTo("DKIM check failed. Expecting DKIM signatures. Got none."));
        }

        @Test
        void shouldDeclineWhenNoDkimRecordWhenTrue() {
            assertThat(DKIMHook.SignatureRecordValidation.signatureRequired(false)
                .validate(MaybeSender.getMailSender("bob@localhost"), ImmutableList.of()))
                .satisfies(hookResult -> assertThat(hookResult.getResult()).isEqualTo(HookReturnCode.declined()));
        }

        @Test
        void shouldDeclineWhenDkimRecordWhenTrue() {
            assertThat(DKIMHook.SignatureRecordValidation.signatureRequired(true)
                .validate(MaybeSender.getMailSender("bob@localhost"), ImmutableList.of(SIGNATURE_RECORD_1)))
                .satisfies(hookResult -> assertThat(hookResult.getResult()).isEqualTo(HookReturnCode.declined()));
        }

        @Test
        void allShouldDeclineWhenNoDkimRecord() {
            assertThat(DKIMHook.SignatureRecordValidation.ALLOW_ALL
                .validate(MaybeSender.getMailSender("bob@localhost"), ImmutableList.of()))
                .satisfies(hookResult -> assertThat(hookResult.getResult()).isEqualTo(HookReturnCode.declined()));
        }

        @Test
        void allShouldDeclineWhenDkimRecord() {
            assertThat(DKIMHook.SignatureRecordValidation.ALLOW_ALL
                .validate(MaybeSender.getMailSender("bob@localhost"), ImmutableList.of(SIGNATURE_RECORD_1)))
                .satisfies(hookResult -> assertThat(hookResult.getResult()).isEqualTo(HookReturnCode.declined()));
        }

        @Test
        void andShouldDeclineWhenBothDecline() {
            assertThat(DKIMHook.SignatureRecordValidation.and(
                    DKIMHook.SignatureRecordValidation.ALLOW_ALL,
                    DKIMHook.SignatureRecordValidation.ALLOW_ALL)
                .validate(MaybeSender.getMailSender("bob@localhost"), ImmutableList.of()))
                .satisfies(hookResult -> assertThat(hookResult.getResult()).isEqualTo(HookReturnCode.declined()));
        }

        @Test
        void andShouldDenyWhenFirstDeny() {
            assertThat(DKIMHook.SignatureRecordValidation.and(
                    DKIMHook.SignatureRecordValidation.signatureRequired(true),
                    DKIMHook.SignatureRecordValidation.ALLOW_ALL)
                .validate(MaybeSender.getMailSender("bob@localhost"), ImmutableList.of()))
                .satisfies(hookResult -> assertThat(hookResult.getResult()).isEqualTo(HookReturnCode.deny()))
                .satisfies(hookResult -> assertThat(hookResult.getSmtpDescription()).isEqualTo("DKIM check failed. Expecting DKIM signatures. Got none."));
        }

        @Test
        void andShouldDenyWhenSecondDeny() {
            assertThat(DKIMHook.SignatureRecordValidation.and(
                    DKIMHook.SignatureRecordValidation.ALLOW_ALL,
                    DKIMHook.SignatureRecordValidation.signatureRequired(true))
                .validate(MaybeSender.getMailSender("bob@localhost"), ImmutableList.of()))
                .satisfies(hookResult -> assertThat(hookResult.getResult()).isEqualTo(HookReturnCode.deny()))
                .satisfies(hookResult -> assertThat(hookResult.getSmtpDescription()).isEqualTo("DKIM check failed. Expecting DKIM signatures. Got none."));
        }

        @Test
        void andShouldKeepOnlyTheFirstSMTPDescription() {
            assertThat(DKIMHook.SignatureRecordValidation.and(
                    DKIMHook.SignatureRecordValidation.signatureRequired(true),
                    DKIMHook.SignatureRecordValidation.expectedDToken("wrong.com"))
                .validate(MaybeSender.getMailSender("bob@localhost"), ImmutableList.of()))
                .satisfies(hookResult -> assertThat(hookResult.getResult()).isEqualTo(HookReturnCode.deny()))
                .satisfies(hookResult -> assertThat(hookResult.getSmtpDescription()).isEqualTo("DKIM check failed. Expecting DKIM signatures. Got none."));
        }

        @Test
        void expectedDTokenShouldDenyWhenWrongDToken() {
            assertThat(DKIMHook.SignatureRecordValidation.expectedDToken("wrong.com")
                .validate(MaybeSender.getMailSender("bob@localhost"), ImmutableList.of(SIGNATURE_RECORD_1)))
                .satisfies(hookResult -> assertThat(hookResult.getResult()).isEqualTo(HookReturnCode.deny()))
                .satisfies(hookResult -> assertThat(hookResult.getSmtpDescription()).isEqualTo("DKIM check failed. Wrong d token. Expecting wrong.com"));
        }

        @Test
        void expectedDTokenShouldDeclineWhenGoodDToken() {
            assertThat(DKIMHook.SignatureRecordValidation.expectedDToken("linagora.com")
                .validate(MaybeSender.getMailSender("bob@localhost"), ImmutableList.of(SIGNATURE_RECORD_1)))
                .satisfies(hookResult -> assertThat(hookResult.getResult()).isEqualTo(HookReturnCode.declined()));
        }

        @Test
        void expectedDTokenShouldDeclineWhenGoodFirstDToken() {
            assertThat(DKIMHook.SignatureRecordValidation.expectedDToken("linagora.com")
                .validate(MaybeSender.getMailSender("bob@localhost"), ImmutableList.of(SIGNATURE_RECORD_1, SIGNATURE_RECORD_2)))
                .satisfies(hookResult -> assertThat(hookResult.getResult()).isEqualTo(HookReturnCode.declined()));
        }

        @Test
        void expectedDTokenShouldDeclineWhenGoodSecondDToken() {
            assertThat(DKIMHook.SignatureRecordValidation.expectedDToken("linagora.com")
                .validate(MaybeSender.getMailSender("bob@localhost"), ImmutableList.of(SIGNATURE_RECORD_2, SIGNATURE_RECORD_1)))
                .satisfies(hookResult -> assertThat(hookResult.getResult()).isEqualTo(HookReturnCode.declined()));
        }
    }

    @Nested
    class ConfigTest {
        @Test
        void shouldMatchEqualsContract() {
            EqualsVerifier.forClass(DKIMHook.Config.class)
                .verify();
        }

        @Test
        void parseShouldSpecifyDefaultValues() {
            BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();

            assertThat(DKIMHook.Config.parse(configuration))
                .isEqualTo(new DKIMHook.Config(true, true, Optional.empty(), Optional.empty()));
        }

        @Test
        void parseShouldPreserveSpecifiedValues() {
            BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
            configuration.addProperty("forceCRLF", false);
            configuration.addProperty("signatureRequired", false);
            configuration.addProperty("onlyForSenderDomain", "linagora.com");
            configuration.addProperty("expectedDToken", "apache.org");

            assertThat(DKIMHook.Config.parse(configuration))
                .isEqualTo(new DKIMHook.Config(false, false,
                    Optional.of(Domain.of("linagora.com")), Optional.of("apache.org")));
        }
    }
}