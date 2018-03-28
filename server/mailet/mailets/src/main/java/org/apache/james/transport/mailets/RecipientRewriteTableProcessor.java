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
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class RecipientRewriteTableProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipientRewriteTableProcessor.class);

    private final org.apache.james.rrt.api.RecipientRewriteTable virtualTableStore;
    private final DomainList domainList;
    private final MailetContext mailetContext;

    private static final Function<RrtExecutionResult, Stream<MailAddress>> mailAddressesFromMappingData =
        mappingData -> mappingData.getNewRecipients()
            .orElse(mappingData.getRecipientWithError()
                .orElse(ImmutableList.of())).stream();

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

    private ImmutableList<RrtExecutionResult> toMappingDatas(final Mail mail) {
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
    List<MailAddress> handleMappings(Mappings mappings, MailAddress sender, MailAddress recipient, MimeMessage message) 
            throws MessagingException {
        ImmutableList<Mapping> addressMappingWithoutDomains = getAddressWithNoDomain(mappings, domainList);

        ImmutableList<Mapping> newAddressMappings = convertToNewMappings(mappings, addressMappingWithoutDomains);

        ImmutableList<MailAddress> mailAddresses = buildMailAddressFromMappingAddress(newAddressMappings);

        forwardToRemoteAddress(sender, recipient, message, mailAddresses);

        return getLocalAddresses(mailAddresses);
    }

    private ImmutableList<Mapping> convertToNewMappings(Mappings mappings, ImmutableList<Mapping> addressWithoutDomains) {
        return Stream.concat(
                mappings.asStream().filter(Mapping::hasDomain),
                addressWithoutDomains.stream())
            .collect(Guavate.toImmutableList());
    }

    private ImmutableList<MailAddress> getLocalAddresses(ImmutableList<MailAddress> mailAddresses) {
        return mailAddresses.stream()
            .filter(mailAddress -> mailetContext.isLocalServer(mailAddress.getDomain()))
            .collect(Guavate.toImmutableList());
    }

    private ImmutableList<MailAddress> buildMailAddressFromMappingAddress(ImmutableList<Mapping> newMappings) {
        return newMappings.stream()
            .map(mailAddressFromMapping)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Guavate.toImmutableList());
    }

    private ImmutableList<Mapping> getAddressWithNoDomain(Mappings mappings, DomainList domainList) throws MessagingException {
        ImmutableList<Mapping> addressWithoutDomains = mappings.asStream()
            .filter(address -> !address.hasDomain())
            .collect(Guavate.toImmutableList());
        
        if (!addressWithoutDomains.isEmpty()) {
            final Domain defaultDomain = getDefaultDomain(domainList);

            return addressWithoutDomains.stream()
                .map(address -> address.appendDomain(defaultDomain))
                .collect(Guavate.toImmutableList());
        }
        return ImmutableList.of();
    }

    private void forwardToRemoteAddress(MailAddress sender, MailAddress recipient, MimeMessage message, ImmutableList<MailAddress> mailAddresses) throws MessagingException {
        ImmutableList<MailAddress> remoteAddress = mailAddresses.stream()
            .filter(mailAddress -> !mailetContext.isLocalServer(mailAddress.getDomain()))
            .collect(Guavate.toImmutableList());

        if (!remoteAddress.isEmpty()) {
            try {
                mailetContext.sendMail(sender, remoteAddress, message);
                LOGGER.info("Mail for {} forwarded to {}", recipient, remoteAddress);
            } catch (MessagingException ex) {
                LOGGER.warn("Error forwarding mail to {}", remoteAddress);
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
        return new RrtExecutionResult(Optional.empty(), Optional.<List<MailAddress>>of(ImmutableList.of(mailAddress)));
    }

    private RrtExecutionResult origin(MailAddress mailAddress) {
        return new RrtExecutionResult(Optional.<List<MailAddress>>of(ImmutableList.of(mailAddress)), Optional.empty());
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
