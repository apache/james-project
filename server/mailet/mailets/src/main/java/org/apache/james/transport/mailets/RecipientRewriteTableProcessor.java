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

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTable.ErrorMappingException;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.util.MemoizedSupplier;
import org.apache.james.util.OptionalUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class RecipientRewriteTableProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipientRewriteTableProcessor.class);

    private static class RrtExecutionResult {
        private static RrtExecutionResult empty() {
            return new RrtExecutionResult(ImmutableList.of(), ImmutableList.of());
        }

        private static RrtExecutionResult error(MailAddress mailAddress) {
            return new RrtExecutionResult(ImmutableList.of(), ImmutableList.of(mailAddress));
        }

        private static RrtExecutionResult success(MailAddress mailAddress) {
            return new RrtExecutionResult(ImmutableList.of(mailAddress), ImmutableList.of());
        }

        private static RrtExecutionResult success(List<MailAddress> mailAddresses) {
            return new RrtExecutionResult(ImmutableList.copyOf(mailAddresses), ImmutableList.of());
        }

        private static RrtExecutionResult merge(RrtExecutionResult result1, RrtExecutionResult result2) {
            return new RrtExecutionResult(
                ImmutableList.<MailAddress>builder()
                    .addAll(result1.getNewRecipients())
                    .addAll(result2.getNewRecipients())
                    .build(),
                ImmutableList.<MailAddress>builder()
                    .addAll(result1.getRecipientWithError())
                    .addAll(result2.getRecipientWithError())
                    .build());
        }

        private final ImmutableList<MailAddress> newRecipients;
        private final ImmutableList<MailAddress> recipientWithError;

        public RrtExecutionResult(ImmutableList<MailAddress> newRecipients, ImmutableList<MailAddress> recipientWithError) {
            this.newRecipients = newRecipients;
            this.recipientWithError = recipientWithError;
        }

        public List<MailAddress> getNewRecipients() {
            return newRecipients;
        }

        public List<MailAddress> getRecipientWithError() {
            return recipientWithError;
        }

    }

    private final RecipientRewriteTable virtualTableStore;
    private final MailetContext mailetContext;
    private final Supplier<Domain> defaultDomainSupplier;

    private static final Function<Mapping, Optional<MailAddress>> mailAddressFromMapping =
        addressMapping -> {
            try {
                return Optional.of(new MailAddress(addressMapping.asString()));
            } catch (AddressException e) {
                return Optional.empty();
            }
        };

    public RecipientRewriteTableProcessor(RecipientRewriteTable virtualTableStore, DomainList domainList, MailetContext mailetContext) {
        this.virtualTableStore = virtualTableStore;
        this.mailetContext = mailetContext;
        this.defaultDomainSupplier = MemoizedSupplier.of(
            Throwing.supplier(() -> getDefaultDomain(domainList)).sneakyThrow());
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
            mailetContext.sendMail(mail.getSender(), executionResults.recipientWithError, mail.getMessage(), Mail.ERROR);
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
            Mappings mappings = virtualTableStore.getMappings(recipient.getLocalPart(), recipient.getDomain());

            if (mappings != null) {
                List<MailAddress> newMailAddresses = handleMappings(mappings, mail.getSender(), recipient, mail.getMessage());
                return RrtExecutionResult.success(newMailAddresses);
            }
            return RrtExecutionResult.success(recipient);
        } catch (ErrorMappingException | RecipientRewriteTableException | MessagingException e) {
            LOGGER.info("Error while process mail.", e);
            return RrtExecutionResult.error(recipient);
        }
    }

    @VisibleForTesting
    List<MailAddress> handleMappings(Mappings mappings, MailAddress sender, MailAddress recipient, MimeMessage message) throws MessagingException {
        ImmutableList<MailAddress> mailAddresses = mappings.asStream()
            .map(mapping -> mapping.appendDomainIfNone(defaultDomainSupplier))
            .map(mailAddressFromMapping)
            .flatMap(OptionalUtils::toStream)
            .collect(Guavate.toImmutableList());

        forwardToRemoteAddress(sender, recipient, message, mailAddresses);

        return getLocalAddresses(mailAddresses);
    }

    private ImmutableList<MailAddress> getLocalAddresses(ImmutableList<MailAddress> mailAddresses) {
        return mailAddresses.stream()
            .filter(mailAddress -> mailetContext.isLocalServer(mailAddress.getDomain()))
            .collect(Guavate.toImmutableList());
    }

    private void forwardToRemoteAddress(MailAddress sender, MailAddress recipient, MimeMessage message, ImmutableList<MailAddress> mailAddresses) {
        ImmutableList<MailAddress> remoteAddresses = mailAddresses.stream()
            .filter(mailAddress -> !mailetContext.isLocalServer(mailAddress.getDomain()))
            .collect(Guavate.toImmutableList());

        if (!remoteAddresses.isEmpty()) {
            try {
                mailetContext.sendMail(sender, remoteAddresses, message);
                LOGGER.info("Mail for {} forwarded to {}", recipient, remoteAddresses);
            } catch (MessagingException ex) {
                LOGGER.warn("Error forwarding mail to {}", remoteAddresses);
            }
        }
    }

}
