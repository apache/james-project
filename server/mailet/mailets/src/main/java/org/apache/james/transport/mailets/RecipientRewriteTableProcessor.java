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
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

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
import org.apache.james.util.GuavaUtils;
import org.apache.james.util.OptionalUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class RecipientRewriteTableProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipientRewriteTableProcessor.class);

    private final org.apache.james.rrt.api.RecipientRewriteTable virtualTableStore;
    private final DomainList domainList;
    private final MailetContext mailetContext;

    private static final Function<RrtExecutionResult, Stream<MailAddress>> mailAddressesFromMappingData =
        mappingData ->
                OptionalUtils.or(
                    mappingData.getNewRecipients(),
                    mappingData.getRecipientWithError())
            .map(Collection::stream).orElse(Stream.of());

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
        this.domainList = domainList;
        this.mailetContext = mailetContext;
    }

    public void processMail(Mail mail) throws MessagingException {
        ImmutableList<RrtExecutionResult> mappingDatas = toMappingDatas(mail);

        ImmutableSet<MailAddress> newRecipients = getRecipientsByCondition(mappingDatas, mappingData -> !mappingData.isError());

        ImmutableSet<MailAddress> errorMailAddresses = getRecipientsByCondition(mappingDatas, RrtExecutionResult::isError);

        if (!errorMailAddresses.isEmpty()) {
            mailetContext.sendMail(mail.getSender(), errorMailAddresses, mail.getMessage(), Mail.ERROR);
        }

        if (newRecipients.isEmpty()) {
            mail.setState(Mail.GHOST);
        }

        mail.setRecipients(newRecipients);
    }


    private ImmutableSet<MailAddress> getRecipientsByCondition(ImmutableList<RrtExecutionResult> mappingDatas, Predicate<RrtExecutionResult> filterCondition) {
        return mappingDatas.stream()
            .filter(filterCondition)
            .flatMap(mailAddressesFromMappingData)
            .collect(Guavate.toImmutableSet());
    }

    private ImmutableList<RrtExecutionResult> toMappingDatas(Mail mail) {
        Function<MailAddress, RrtExecutionResult> convertToMappingData = recipient -> {
            Preconditions.checkNotNull(recipient);

            return getRrtExecutionResult(mail, recipient);
        };

        return mail.getRecipients()
            .stream()
            .map(convertToMappingData)
            .collect(Guavate.toImmutableList());
    }

    private RrtExecutionResult getRrtExecutionResult(Mail mail, MailAddress recipient) {
        try {
            Mappings mappings = virtualTableStore.getMappings(recipient.getLocalPart(), recipient.getDomain());

            if (mappings != null) {
                List<MailAddress> newMailAddresses = handleMappings(mappings, mail.getSender(), recipient, mail.getMessage());
                return new RrtExecutionResult(Optional.of(newMailAddresses), Optional.empty());
            }
            return origin(recipient);
        } catch (ErrorMappingException | RecipientRewriteTableException | MessagingException e) {
            LOGGER.info("Error while process mail.", e);
            return error(recipient);
        }
    }

    @VisibleForTesting
    List<MailAddress> handleMappings(Mappings mappings, MailAddress sender, MailAddress recipient, MimeMessage message) throws MessagingException {
        ImmutableList<MailAddress> mailAddresses = sanitizedMappings(mappings)
            .map(mailAddressFromMapping)
            .flatMap(OptionalUtils::toStream)
            .collect(Guavate.toImmutableList());

        forwardToRemoteAddress(sender, recipient, message, mailAddresses);

        return getLocalAddresses(mailAddresses);
    }

    private Stream<Mapping> sanitizedMappings(Mappings mappings) throws MessagingException {
        ImmutableList<Mapping> sanitizedMappings = sanitizeMappingsWithNoDomain(mappings, domainList);

        return Stream.concat(
            mappings.asStream().filter(Mapping::hasDomain),
            sanitizedMappings.stream());
    }

    private ImmutableList<MailAddress> getLocalAddresses(ImmutableList<MailAddress> mailAddresses) {
        return mailAddresses.stream()
            .filter(mailAddress -> mailetContext.isLocalServer(mailAddress.getDomain()))
            .collect(Guavate.toImmutableList());
    }

    private ImmutableList<Mapping> sanitizeMappingsWithNoDomain(Mappings mappings, DomainList domainList) throws MessagingException {
        Supplier<Domain> defaultDomainSupplier = Suppliers.memoize(
            GuavaUtils.toGuava(
            Throwing.supplier(() -> getDefaultDomain(domainList))
                .sneakyThrow()));

        return mappings.asStream()
            .filter(mapping -> !mapping.hasDomain())
            .map(mapping -> mapping.appendDomain(defaultDomainSupplier.get()))
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

    private Domain getDefaultDomain(DomainList domainList) throws MessagingException {
        try {
            return domainList.getDefaultDomain();
        } catch (DomainListException e) {
            throw new MessagingException("Unable to access DomainList", e);
        }
    }
    
    private RrtExecutionResult error(MailAddress mailAddress) {
        return new RrtExecutionResult(Optional.empty(), Optional.of(ImmutableList.of(mailAddress)));
    }

    private RrtExecutionResult origin(MailAddress mailAddress) {
        return new RrtExecutionResult(Optional.of(ImmutableList.of(mailAddress)), Optional.empty());
    }

    class RrtExecutionResult {
        private final Optional<List<MailAddress>> newRecipients;
        private final Optional<List<MailAddress>> recipientWithError;

        public RrtExecutionResult(Optional<List<MailAddress>> newRecipients, Optional<List<MailAddress>> recipientWithError) {
            this.newRecipients = newRecipients;
            this.recipientWithError = recipientWithError;
        }

        public Optional<List<MailAddress>> getNewRecipients() {
            return newRecipients;
        }

        public Optional<List<MailAddress>> getRecipientWithError() {
            return recipientWithError;
        }

        public boolean isError() {
            return recipientWithError.isPresent();
        }

    }
}
