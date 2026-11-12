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

import static org.apache.james.protocols.smtp.SMTPRetCode.AUTH_REQUIRED;
import static org.apache.james.protocols.smtp.SMTPRetCode.LOCAL_ERROR;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.jdkim.api.PublicKeyRecordRetriever;
import org.apache.james.jdkim.api.SignatureRecord;
import org.apache.james.jdkim.exceptions.FailException;
import org.apache.james.jdkim.mailets.DKIMVerifier;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.mime4j.stream.ParserCursor;
import org.apache.james.mime4j.util.ByteSequence;
import org.apache.james.mime4j.util.ContentUtil;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * <p>Hook for verifying DKIM signatures of incoming mails.</p>
 *
 * <p>This hook can be restricted to specific sender domains and authenticate those emails against
 * their DKIM signature. Given a signed outgoing traffic this hook can use operators to accept legitimate
 * emails emmited by their infrastructure but redirected without envelope changes to there own domains by
 * some intermediate third parties. See <a href="https://issues.apache.org/jira/browse/JAMES-4032">JAMES-4032</a> for schemas.</p>
 *
 * <ul>Supported configuration elements:
 *   <li><b>forceCRLF</b>: Should CRLF be forced when computing body hashes.</li>
 *   <li><b>onlyForSenderDomain</b>: If specified, the DKIM checks are applied just for the emails whose MAIL FROM specifies this domain. If unspecified, all emails are checked (default).</li>
 *   <li><b>signatureRequired</b>: If DKIM signature is checked, the absence of signature will generate failure. Defaults to true.</li>
 *   <li><b>expectedDToken</b>: If DKIM signature is checked, the body should contain at least one DKIM signature with this d token. If unspecified, all d tokens are considered valid (default).</li>
 * </ul>
 *
 * <p>Example handlerchain configuration for smtpserver.xml:</p>
 * <pre><code>    &lt;handlerchain&gt;
 *       &lt;handler class=&quot;org.apache.james.smtpserver.DKIMHook&quot;&gt;
 *           &lt;forceCLRF&gt;true&lt;/forceCLRF&gt;
 *           &lt;onlyForSenderDomain&gt;apache.org&lt;/onlyForSenderDomain&gt;
 *           &lt;signatureRequired&gt;true&lt;/signatureRequired&gt;
 *           &lt;expectedDToken&gt;apache.org&lt;/expectedDToken&gt;
 *       &lt;/handler&gt;
 *       &lt;handler class=&quot;org.apache.james.smtpserver.CoreCmdHandlerLoader&quot;/&gt;
 *     &lt;/handlerchain&gt;
 * </code></pre>
 *
 * Would allow emails using <code>apache.org</code> as a MAIL FROM domain if, and only if they contain a
 * valid DKIM signature for the <code>apache.org</code> domain.
 *
 */
public class DKIMHook implements JamesMessageHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(DKIMHook.class);
    private static final Pattern LDH_LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

    @FunctionalInterface
    interface DKIMCheckNeeded extends Predicate<Mail> {
        static DKIMCheckNeeded or(List<DKIMCheckNeeded> checkNeededs) {
            return mail -> checkNeededs.stream()
                .anyMatch(predicate -> predicate.test(mail));
        }

        static DKIMCheckNeeded onlyForSenderDomain(Domain domain) {
            return mail -> mail.getMaybeSender()
                .asOptional()
                .map(MailAddress::getDomain)
                .map(domain::equals)
                .orElse(false);
        }

        /**
         * True when any mailbox of any From: field is in the domain, for Jakarta Mail or for the lenient mime4j
         * parser. A From: that one of them cannot parse is also checked, whatever its domain: an attacker should not
         * skip the check by malforming the field.
         */
        static DKIMCheckNeeded onlyForHeaderFromDomain(Domain domain) {
            return mail -> {
                try {
                    Address[] from = mail.getMessage().getFrom();
                    return (from != null && Arrays.stream(from).anyMatch(address -> isInDomainOrMalformed(address, domain)))
                        || rawFrom(mail).stream().anyMatch(value -> isInDomainOrMalformedForMime4j(value, domain));
                } catch (MessagingException me) {
                    LOGGER.info("Unable to parse the \"FROM\" header; checking DKIM.", me);
                    return true;
                }
            };
        }

        private static boolean isInDomainOrMalformedForMime4j(String rawFrom, Domain domain) {
            // parseAddressList(CharSequence) bounds its cursor by chars, not bytes, and cuts a non-ASCII field short
            ByteSequence rawBytes = ContentUtil.encode(MimeUtil.unfold(rawFrom));
            return LenientAddressParser.DEFAULT.parseAddressList(rawBytes, new ParserCursor(0, rawBytes.length()))
                .flatten()
                .stream()
                .anyMatch(mailbox -> isInDomainOrMalformed(mailbox.getDomain(), domain));
        }

        private static boolean isInDomainOrMalformed(String mailboxDomain, Domain domain) {
            if (mailboxDomain == null) {
                return true;
            }
            try {
                return Domain.of(mailboxDomain).equals(domain);
            } catch (IllegalArgumentException e) {
                return !isUndecodableALabelDomain(mailboxDomain);
            }
        }

        /**
         * True for a well-formed domain holding an A-label that Domain cannot decode: Domain follows IDNA2003, which
         * refuses IDNA2008 labels such as xn--fa-hia (faß). Such a domain is never the protected one.
         */
        private static boolean isUndecodableALabelDomain(String mailboxDomain) {
            List<String> labels = Splitter.on('.').splitToList(mailboxDomain.toLowerCase(Locale.US));
            return labels.stream().allMatch(label -> LDH_LABEL.matcher(label).matches())
                && labels.stream().anyMatch(label -> label.startsWith("xn--"));
        }

        private static boolean hasUndecodableALabelDomain(String address) {
            int at = address == null ? -1 : address.lastIndexOf('@');
            if (at <= 0) {
                return false;
            }
            try {
                // Validates the local part alone
                new MailAddress(address.substring(0, at), Domain.LOCALHOST.asString());
                return isUndecodableALabelDomain(address.substring(at + 1));
            } catch (AddressException e) {
                return false;
            }
        }

        private static boolean isInDomainOrMalformed(Address from, Domain domain) {
            if (!(from instanceof InternetAddress internetAddress)) {
                return true;
            }
            try {
                if (internetAddress.isGroup()) {
                    InternetAddress[] members = internetAddress.getGroup(false);
                    return members != null && Arrays.stream(members)
                        .anyMatch(member -> isInDomainOrMalformed(member, domain));
                }
                return new MailAddress(internetAddress.getAddress()).getDomain().equals(domain);
            } catch (AddressException e) {
                // Can happen when InternetAddress.getAddress() returns a malformed value (e.g. "addr>")
                return !hasUndecodableALabelDomain(internetAddress.getAddress());
            }
        }

        private static List<String> rawFrom(Mail mail) throws MessagingException {
            return Optional.ofNullable(mail.getMessage().getHeader("From"))
                .map(Arrays::asList)
                .orElse(List.of());
        }

        DKIMCheckNeeded ALL = any -> true;
        DKIMCheckNeeded NONE = any -> false;
    }

    @FunctionalInterface
    interface SignatureRecordValidation {
        static SignatureRecordValidation and(SignatureRecordValidation a, SignatureRecordValidation b) {
            return (sender, messageId, records) -> {
                HookResult hookResult = a.validate(sender, messageId, records);
                if (hookResult.equals(HookResult.DECLINED)) {
                    return b.validate(sender, messageId, records);
                }
                return hookResult;
            };
        }

        static SignatureRecordValidation signatureRequired(boolean required) {
            return (sender, messageId, records) -> {
                if (required && (records == null || records.isEmpty())) {
                    LOGGER.warn("DKIM check failed for {}. Expecting DKIM signatures. Got none.", messageId);
                    return HookResult.builder()
                        .hookReturnCode(HookReturnCode.deny())
                        .smtpReturnCode(AUTH_REQUIRED)
                        .smtpDescription("DKIM check failed. Expecting DKIM signatures. Got none.")
                        .build();
                }
                return HookResult.DECLINED;
            };
        }

        SignatureRecordValidation ALLOW_ALL = (sender, messageId, records) -> HookResult.DECLINED;

        HookResult validate(MaybeSender maybeSender, String messageId, List<SignatureRecord> records);
    }

    static class DTokenSignatureRecordValidation implements SignatureRecordValidation {
        private final List<String> expectedDTokens;

        DTokenSignatureRecordValidation(List<String> expectedDTokens) {
            this.expectedDTokens = expectedDTokens;
        }

        DTokenSignatureRecordValidation(String expectedDToken) {
            this(ImmutableList.of(expectedDToken));
        }

        @Override
        public HookResult validate(MaybeSender maybeSender, String messageId, List<SignatureRecord> records) {
            if (records.stream().anyMatch(r -> expectedDTokens.stream().anyMatch(d -> r.getDToken().equals(d)))) {
                return HookResult.DECLINED;
            }
            ImmutableSet<CharSequence> actualDToken = records.stream().map(SignatureRecord::getDToken).collect(ImmutableSet.toImmutableSet());
            LOGGER.warn("DKIM check failed for {}. Wrong d token. Expecting {}. Got {}.", messageId, expectedDTokens, actualDToken);
            return HookResult.builder()
                .hookReturnCode(HookReturnCode.deny())
                .smtpReturnCode(AUTH_REQUIRED)
                .smtpDescription("DKIM check failed. Wrong d token. Expecting " + expectedDTokens + ". Got " + actualDToken)
                .build();
        }
    }

    public static class Config {
        public static final ImmutableList<ValidatedEntity> DEFAULT_VALIDATED_ENTITIES = ImmutableList.of(ValidatedEntity.envelope, ValidatedEntity.headers);

        public static Config parse(Configuration config) {
            return new Config(
                config.getBoolean("forceCRLF", true),
                config.getBoolean("signatureRequired", true),
                Optional.ofNullable(config.getString("onlyForSenderDomain", null))
                    .map(s -> Splitter.on(',').splitToStream(s)
                        .map(Domain::of)
                        .toList()),
                Optional.ofNullable(config.getString("validatedEntities", null))
                    .map(entities -> Stream.of(entities.split(","))
                        .map(ValidatedEntity::from)
                        .collect(ImmutableList.toImmutableList()))
                    .orElse(DEFAULT_VALIDATED_ENTITIES),
                Optional.ofNullable(config.getString("expectedDToken", null))
                    .map(s -> Splitter.on(',').splitToList(s)));
        }

        public enum ValidatedEntity {
            envelope,
            headers;

            static ValidatedEntity from(String rawValue) {
                Preconditions.checkNotNull(rawValue);

                return Stream.of(values())
                    .filter(entity -> entity.name().equalsIgnoreCase(rawValue))
                    .findAny()
                    .orElseThrow(() -> new IllegalArgumentException(String.format("invalid validated entity '%s'", rawValue)));
            }
        }

        private final boolean forceCRLF;
        private final boolean signatureRequired;
        private final Optional<List<Domain>> onlyForSenderDomain;
        private final ImmutableList<ValidatedEntity> validatedEntities;
        private final Optional<List<String>> expectedDToken;

        public Config(boolean forceCRLF, boolean signatureRequired, Optional<List<Domain>> onlyForSenderDomain,
                      ImmutableList<ValidatedEntity> validatedEntities, Optional<List<String>> expectedDToken) {
            this.forceCRLF = forceCRLF;
            this.signatureRequired = signatureRequired;
            this.onlyForSenderDomain = onlyForSenderDomain;
            this.validatedEntities = validatedEntities;
            this.expectedDToken = expectedDToken;
        }

        DKIMCheckNeeded dkimCheckNeeded() {
            return onlyForSenderDomain
                .map(domains -> DKIMCheckNeeded.or(domains.stream()
                    .map(this::computeDKIMChecksNeeded)
                    .flatMap(Collection::stream)
                    .toList()))
                .orElse(DKIMCheckNeeded.ALL);
        }

        SignatureRecordValidation signatureRecordValidation() {
            return SignatureRecordValidation.and(
                SignatureRecordValidation.signatureRequired(signatureRequired),
                expectedDToken.<SignatureRecordValidation>map(DTokenSignatureRecordValidation::new).orElse(SignatureRecordValidation.ALLOW_ALL));
        }

        private ImmutableList<DKIMCheckNeeded> computeDKIMChecksNeeded(Domain domain) {
            return validatedEntities.stream()
                .map(entity -> toDKIMCheck(entity, domain))
                .collect(ImmutableList.toImmutableList());
        }

        private DKIMCheckNeeded toDKIMCheck(ValidatedEntity entity, Domain domain) {
            return switch (entity) {
                case envelope ->  DKIMCheckNeeded.onlyForSenderDomain(domain);
                case headers -> DKIMCheckNeeded.onlyForHeaderFromDomain(domain);
            };
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Config config) {
                return forceCRLF == config.forceCRLF
                    && signatureRequired == config.signatureRequired
                    && Objects.equals(onlyForSenderDomain, config.onlyForSenderDomain)
                    && Objects.equals(validatedEntities, config.validatedEntities)
                    && Objects.equals(expectedDToken, config.expectedDToken);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(forceCRLF, signatureRequired, onlyForSenderDomain, validatedEntities, expectedDToken);
        }
    }

    @VisibleForTesting
    private final DKIMVerifier verifier;

    private Config config;
    private SignatureRecordValidation signatureRecordValidation;
    private DKIMCheckNeeded dkimCheckNeeded;

    @Inject
    public DKIMHook(PublicKeyRecordRetriever publicKeyRecordRetriever) {
        verifier = new DKIMVerifier(publicKeyRecordRetriever);
    }

    @Override
    public void init(Configuration configuration) {
        config = Config.parse(configuration);

        dkimCheckNeeded = config.dkimCheckNeeded();
        signatureRecordValidation = config.signatureRecordValidation();
    }

    @Override
    public HookResult onMessage(SMTPSession session, Mail mail) {
        if (session.isRelayingAllowed()) {
            return HookResult.DECLINED;
        }
        if (!dkimCheckNeeded.test(mail)) {
            return HookResult.DECLINED;
        }
        try {
            return signatureRecordValidation.validate(mail.getMaybeSender(), mail.getMessage().getMessageID(),
                verifier.verify(mail.getMessage(), config.forceCRLF));
        } catch (MessagingException e) {
            LOGGER.warn("Error while verifying DKIM signatures", e);
            return HookResult.builder()
                .hookReturnCode(HookReturnCode.denySoft())
                .smtpReturnCode(LOCAL_ERROR)
                .smtpDescription("Failure computing DKIM signature.")
                .build();
        } catch (FailException e) {
            LOGGER.warn("DKIM check failed. Invalid signature.", e);
            return HookResult.builder()
                .hookReturnCode(HookReturnCode.deny())
                .smtpReturnCode(AUTH_REQUIRED)
                .smtpDescription("DKIM check failed. Invalid signature.")
                .build();
        }
    }
}
