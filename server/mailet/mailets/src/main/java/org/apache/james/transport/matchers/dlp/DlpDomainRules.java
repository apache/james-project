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

import static org.apache.james.javax.AddressHelper.asStringStream;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.dlp.api.DLPConfigurationItem;
import org.apache.james.dlp.api.DLPConfigurationItem.Targets;
import org.apache.james.javax.AddressHelper;
import org.apache.james.javax.MultipartUtil;
import org.apache.mailet.Mail;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.predicates.ThrowingPredicate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;

public class DlpDomainRules {

    @VisibleForTesting static DlpDomainRules matchNothing() {
        return DlpDomainRules.of(new Rule(DLPConfigurationItem.Id.of("always false"), mail -> false));
    }

    @VisibleForTesting static DlpDomainRules matchAll() {
        return DlpDomainRules.of(new Rule(DLPConfigurationItem.Id.of("always true"), mail -> true));
    }

    private static DlpDomainRules of(Rule rule) {
        return new DlpDomainRules(ImmutableList.of(rule));
    }

    public static DlpDomainRulesBuilder builder() {
        return new DlpDomainRulesBuilder();
    }

    static class Rule {

        interface MatcherFunction extends ThrowingPredicate<Mail> { }

        private static class ContentMatcher implements Rule.MatcherFunction {

            private final Pattern pattern;

            private ContentMatcher(Pattern pattern) {
                this.pattern = pattern;
            }

            @Override
            public boolean doTest(Mail mail) throws MessagingException, IOException {
                return Stream
                    .concat(getMessageSubjects(mail), getMessageBodies(mail.getMessage()))
                    .anyMatch(pattern.asPredicate());
            }

            private Stream<String> getMessageSubjects(Mail mail) throws MessagingException {
                MimeMessage message = mail.getMessage();
                if (message != null) {
                    String subject = message.getSubject();
                    if (subject != null) {
                        return Stream.of(subject);
                    }
                }
                return Stream.of();
            }

            private Stream<String> getMessageBodies(Message message) throws MessagingException, IOException {
                if (message != null) {
                    return getMessageBodiesFromContent(message.getContent());
                }
                return Stream.of();
            }

            private Stream<String> getMessageBodiesFromContent(Object content) throws IOException, MessagingException {
                if (content instanceof String) {
                    return Stream.of((String) content);
                }

                return extractContentsComplexType(content)
                    .flatMap(Throwing.function(this::getMessageBodiesFromContent).sneakyThrow());
            }

            private Stream<Object> extractContentsComplexType(Object content) throws IOException, MessagingException {
                if (content instanceof Message) {
                    Message message = (Message) content;
                    return Stream.of(message.getContent());
                }
                if (content instanceof Multipart) {
                    return MultipartUtil.retrieveBodyParts((Multipart) content)
                        .stream()
                        .map(Throwing.function(BodyPart::getContent).sneakyThrow());
                }

                return Stream.of();
            }
        }

        private static class RecipientsMatcher implements Rule.MatcherFunction {

            private final Pattern pattern;

            private RecipientsMatcher(Pattern pattern) {
                this.pattern = pattern;
            }

            @Override
            public boolean doTest(Mail mail) throws MessagingException, IOException {
                return listRecipientsAsString(mail).anyMatch(pattern.asPredicate());
            }

            private Stream<String> listRecipientsAsString(Mail mail) throws MessagingException {
                return Stream.concat(listEnvelopRecipients(mail), listHeaderRecipients(mail));
            }

            private Stream<String> listEnvelopRecipients(Mail mail) {
                return mail.getRecipients().stream().map(MailAddress::asString);
            }

            private Stream<String> listHeaderRecipients(Mail mail) throws MessagingException {
                return Optional.ofNullable(mail.getMessage())
                    .flatMap(Throwing.function(m -> Optional.ofNullable(m.getAllRecipients())))
                    .map(AddressHelper::asStringStream)
                    .orElse(Stream.of());
            }

        }

        private static class SenderMatcher implements Rule.MatcherFunction {

            private final Pattern pattern;

            private SenderMatcher(Pattern pattern) {
                this.pattern = pattern;
            }

            @Override
            public boolean doTest(Mail mail) throws MessagingException {
                return listSenders(mail).anyMatch(pattern.asPredicate());
            }

            private Stream<String> listSenders(Mail mail) throws MessagingException {
                return Stream.concat(listEnvelopSender(mail), listFromHeaders(mail));
            }

            private Stream<String> listEnvelopSender(Mail mail) {
                return mail.getMaybeSender().asStream()
                    .map(MailAddress::asString);
            }

            private Stream<String> listFromHeaders(Mail mail) throws MessagingException {
                MimeMessage message = mail.getMessage();
                if (message != null) {
                    return asStringStream(message.getFrom());
                }
                return Stream.of();
            }

        }

        private final DLPConfigurationItem.Id id;
        private final MatcherFunction matcher;

        public Rule(DLPConfigurationItem.Id id, MatcherFunction matcher) {
            this.id = id;
            this.matcher = matcher;
        }

        public DLPConfigurationItem.Id id() {
            return id;
        }

        public boolean match(Mail mail) {
            return matcher.test(mail);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Rule) {
                Rule other = (Rule) o;
                return Objects.equals(id, other.id) &&
                    Objects.equals(matcher, other.matcher);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, matcher);
        }

    }

    public static class DlpDomainRulesBuilder {

        private final ImmutableMultimap.Builder<Targets.Type, Rule> rules;

        private DlpDomainRulesBuilder() {
            rules = ImmutableMultimap.builder();
        }

        public DlpDomainRulesBuilder recipientRule(DLPConfigurationItem.Id id, Pattern pattern) {
            return rule(Targets.Type.Recipient, id, pattern);
        }

        public DlpDomainRulesBuilder senderRule(DLPConfigurationItem.Id id, Pattern pattern) {
            return rule(Targets.Type.Sender, id, pattern);
        }

        public DlpDomainRulesBuilder contentRule(DLPConfigurationItem.Id id, Pattern pattern) {
            return rule(Targets.Type.Content, id, pattern);
        }

        public DlpDomainRulesBuilder rule(Targets.Type type, DLPConfigurationItem.Id id, Pattern regexp) {
            rules.put(type, toRule(type, id, regexp));
            return this;
        }

        private Rule toRule(Targets.Type type, DLPConfigurationItem.Id id, Pattern pattern) {
            switch (type) {
                case Sender:
                    return new Rule(id, new Rule.SenderMatcher(pattern));
                case Content:
                    return new Rule(id, new Rule.ContentMatcher(pattern));
                case Recipient:
                    return new Rule(id, new Rule.RecipientsMatcher(pattern));
                default:
                    throw new IllegalArgumentException("unexpected value");
            }
        }

        public DlpDomainRules build() {
            ImmutableMultimap<Targets.Type, Rule> rules = this.rules.build();
            Preconditions.checkState(!containsDuplicateIds(rules), "Rules should not contain duplicated `id`");
            return new DlpDomainRules(rules.values());
        }

        private boolean containsDuplicateIds(ImmutableMultimap<Targets.Type, Rule> rules) {
            return
                Stream.of(Targets.Type.values())
                    .map(rules::get)
                    .anyMatch(this::containsDuplicateIds);
        }

        private boolean containsDuplicateIds(ImmutableCollection<Rule> rules) {
            long distinctIdCount = rules.stream()
                .map(Rule::id)
                .distinct()
                .count();
            return distinctIdCount != rules.size();
        }

    }

    private final ImmutableCollection<Rule> rules;

    private DlpDomainRules(ImmutableCollection<Rule> rules) {
        this.rules = rules;
    }

    public Optional<DLPConfigurationItem.Id> match(Mail mail) {
        return rules.stream()
            .filter(rule -> rule.match(mail))
            .map(Rule::id)
            .findFirst();
    }

}
