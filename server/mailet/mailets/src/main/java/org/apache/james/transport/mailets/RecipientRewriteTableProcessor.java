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

package org.apache.james.transport.mailets;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.mail.MessagingException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTable.ErrorMappingException;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.server.core.MailImpl;
import org.apache.james.util.MemoizedSupplier;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class RecipientRewriteTableProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipientRewriteTableProcessor.class);

    private static class RrtExecutionResult {
        private static RrtExecutionResult empty() {
            return new RrtExecutionResult(ImmutableSet.of(), ImmutableSet.of());
        }

        private static RrtExecutionResult error(MailAddress mailAddress) {
            return new RrtExecutionResult(ImmutableSet.of(), ImmutableSet.of(mailAddress));
        }

        private static RrtExecutionResult success(MailAddress mailAddress) {
            return new RrtExecutionResult(ImmutableSet.of(mailAddress), ImmutableSet.of());
        }

        private static RrtExecutionResult success(List<MailAddress> mailAddresses) {
            return new RrtExecutionResult(ImmutableSet.copyOf(mailAddresses), ImmutableSet.of());
        }

        private static RrtExecutionResult merge(RrtExecutionResult result1, RrtExecutionResult result2) {
            return new RrtExecutionResult(
                ImmutableSet.<MailAddress>builder()
                    .addAll(result1.getNewRecipients())
                    .addAll(result2.getNewRecipients())
                    .build(),
                ImmutableSet.<MailAddress>builder()
                    .addAll(result1.getRecipientWithError())
                    .addAll(result2.getRecipientWithError())
                    .build());
        }

        private final ImmutableSet<MailAddress> newRecipients;
        private final ImmutableSet<MailAddress> recipientWithError;

        public RrtExecutionResult(ImmutableSet<MailAddress> newRecipients, ImmutableSet<MailAddress> recipientWithError) {
            this.newRecipients = newRecipients;
            this.recipientWithError = recipientWithError;
        }

        public Set<MailAddress> getNewRecipients() {
            return newRecipients;
        }

        public Set<MailAddress> getRecipientWithError() {
            return recipientWithError;
        }

    }

    private final RecipientRewriteTable virtualTableStore;
    private final MailetContext mailetContext;
    private final Supplier<Domain> defaultDomainSupplier;
    private final String errorProcessor;

    public RecipientRewriteTableProcessor(RecipientRewriteTable virtualTableStore, DomainList domainList, MailetContext mailetContext, String errorProcessor) {
        this.virtualTableStore = virtualTableStore;
        this.mailetContext = mailetContext;
        this.defaultDomainSupplier = MemoizedSupplier.of(
            Throwing.supplier(() -> getDefaultDomain(domainList)).sneakyThrow());
        this.errorProcessor = errorProcessor;
    }

    public RecipientRewriteTableProcessor(RecipientRewriteTable virtualTableStore, DomainList domainList, MailetContext mailetContext) {
        this(virtualTableStore, domainList, mailetContext, Mail.ERROR);
    }

    private Domain getDefaultDomain(DomainList domainList) throws MessagingException {
        try {
            return domainList.getDefaultDomain();
        } catch (DomainListException e) {
            throw new MessagingException("Unable to access DomainList", e);
        }
    }

    public void processMail(Mail mail) throws MessagingException {
        RrtExecutionResult executionResults = executeRrtFor(mail);

        if (!executionResults.recipientWithError.isEmpty()) {
            MailImpl newMail = MailImpl.builder()
                .name(mail.getName())
                .sender(mail.getMaybeSender())
                .addRecipients(executionResults.recipientWithError)
                .mimeMessage(mail.getMessage())
                .state(errorProcessor)
                .build();
            mailetContext.sendMail(newMail);
        }

        if (executionResults.newRecipients.isEmpty()) {
            mail.setState(Mail.GHOST);
        }

        mail.setRecipients(executionResults.newRecipients);
    }

    private RrtExecutionResult executeRrtFor(Mail mail) {
        Function<MailAddress, RrtExecutionResult> convertToMappingData = recipient -> {
            Preconditions.checkNotNull(recipient);

            return executeRrtForRecipient(mail, recipient);
        };

        return mail.getRecipients()
            .stream()
            .map(convertToMappingData)
            .reduce(RrtExecutionResult.empty(), RrtExecutionResult::merge);
    }

    private RrtExecutionResult executeRrtForRecipient(Mail mail, MailAddress recipient) {
        try {
            Mappings mappings = virtualTableStore.getResolvedMappings(recipient.getLocalPart(), recipient.getDomain());

            if (mappings != null && !mappings.isEmpty()) {
                List<MailAddress> newMailAddresses = handleMappings(mappings, mail, recipient);
                return RrtExecutionResult.success(newMailAddresses);
            }
            return RrtExecutionResult.success(recipient);
        } catch (ErrorMappingException | RecipientRewriteTableException e) {
            LOGGER.warn("Could not rewrite recipient {}", recipient, e);
            return RrtExecutionResult.error(recipient);
        }
    }

    @VisibleForTesting
    List<MailAddress> handleMappings(Mappings mappings, Mail mail, MailAddress recipient) {
        boolean isLocal = true;
        Map<Boolean, List<MailAddress>> mailAddressSplit = splitRemoteMailAddresses(mappings);

        forwardToRemoteAddress(mail, recipient, mailAddressSplit.get(!isLocal));

        return mailAddressSplit.get(isLocal);
    }

    private ImmutableMap<Boolean, List<MailAddress>> splitRemoteMailAddresses(Mappings mappings) {
        return mailAddressesPerDomain(mappings)
            .collect(Collectors.partitioningBy(entry -> mailetContext.isLocalServer(entry.getKey())))
            .entrySet()
            .stream()
            .collect(Guavate.toImmutableMap(
                Map.Entry::getKey,
                entry -> entry.getValue()
                    .stream()
                    .flatMap(domainEntry -> domainEntry.getValue().stream())
                    .collect(Guavate.toImmutableList())));
    }

    private Stream<Map.Entry<Domain, Collection<MailAddress>>> mailAddressesPerDomain(Mappings mappings) {
        return mappings.asStream()
            .map(mapping -> mapping.appendDomainIfNone(defaultDomainSupplier))
            .map(Mapping::asMailAddress)
            .flatMap(Optional::stream)
            .collect(Guavate.toImmutableListMultimap(
                MailAddress::getDomain))
            .asMap()
            .entrySet()
            .stream();
    }

    private void forwardToRemoteAddress(Mail mail, MailAddress recipient, Collection<MailAddress> remoteRecipients) {
        if (!remoteRecipients.isEmpty()) {
            try {
                mailetContext.sendMail(
                    MailImpl.builder()
                        .name(mail.getName())
                        .sender(mail.getMaybeSender())
                        .addRecipients(ImmutableList.copyOf(remoteRecipients))
                        .mimeMessage(mail.getMessage())
                        .build());
                LOGGER.info("Mail for {} forwarded to {}", recipient, remoteRecipients);
            } catch (MessagingException ex) {
                LOGGER.warn("Error forwarding mail to {}", remoteRecipients);
            }
        }
    }

}
