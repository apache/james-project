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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTable.ErrorMappingException;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.server.core.MailImpl;
import org.apache.james.util.AuditTrail;
import org.apache.james.util.MemoizedSupplier;
import org.apache.mailet.DsnParameters;
import org.apache.mailet.DsnParameters.RecipientDsnParameters;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.ProcessingState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class RecipientRewriteTableProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipientRewriteTableProcessor.class);
    private static final boolean REWRITE_SENDER_UPON_FORWARD = true;

    private static class Decision {
        private final MailAddress originalAddress;
        private final RrtExecutionResult executionResult;

        private Decision(MailAddress originalAddress, RrtExecutionResult executionResult) {
            this.originalAddress = originalAddress;
            this.executionResult = executionResult;
        }

        MailAddress originalAddress() {
            return originalAddress;
        }

        RrtExecutionResult executionResult() {
            return executionResult;
        }

        DsnParameters applyOnDsnParameters(DsnParameters dsnParameters) {
            ImmutableMap<MailAddress, RecipientDsnParameters> rcptParameters = dsnParameters.getRcptParameters();

            Optional<RecipientDsnParameters> originalRcptParameter = Optional.ofNullable(rcptParameters.get(originalAddress));

            return originalRcptParameter.map(parameters -> {
                Map<MailAddress, RecipientDsnParameters> newRcptParameters = executionResult.getNewRecipients().stream()
                    .map(newRcpt -> Pair.of(newRcpt, parameters))
                    .collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue));
                Map<MailAddress, RecipientDsnParameters> rcptParametersWithoutOriginal = rcptParameters.entrySet().stream()
                    .filter(rcpt -> !rcpt.getKey().equals(originalAddress))
                    .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

                return dsnParameters.withRcptParameters(ImmutableMap.<MailAddress, RecipientDsnParameters>builder()
                    .putAll(rcptParametersWithoutOriginal)
                    .putAll(newRcptParameters)
                    .build());
            }).orElse(dsnParameters);
        }
    }

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
    private final ProcessingState errorProcessor;
    private final boolean rewriteSenderUponForward;
    private final EnumSet<Mapping.Type> mappingTypes;

    public RecipientRewriteTableProcessor(RecipientRewriteTable virtualTableStore, DomainList domainList,
                                          MailetContext mailetContext, ProcessingState errorProcessor, boolean rewriteSenderUponForward) {
        this.virtualTableStore = virtualTableStore;
        this.mailetContext = mailetContext;
        this.defaultDomainSupplier = MemoizedSupplier.of(
            Throwing.supplier(() -> getDefaultDomain(domainList)).sneakyThrow());
        this.errorProcessor = errorProcessor;
        this.rewriteSenderUponForward = rewriteSenderUponForward;
        if (rewriteSenderUponForward) {
            EnumSet<Mapping.Type> types = EnumSet.allOf(Mapping.Type.class);
            types.remove(Mapping.Type.Forward);
            mappingTypes = types;
        } else {
            mappingTypes = EnumSet.allOf(Mapping.Type.class);
        }
    }

    public RecipientRewriteTableProcessor(RecipientRewriteTable virtualTableStore, DomainList domainList, MailetContext mailetContext) {
        this(virtualTableStore, domainList, mailetContext, new ProcessingState(Mail.ERROR), !REWRITE_SENDER_UPON_FORWARD);
    }

    private Domain getDefaultDomain(DomainList domainList) throws MessagingException {
        try {
            return domainList.getDefaultDomain();
        } catch (DomainListException e) {
            throw new MessagingException("Unable to access DomainList", e);
        }
    }

    public void processMail(Mail mail) throws MessagingException {
        Collection<MailAddress> recipientsBeforeRecipientsRewrite = ImmutableList.copyOf(mail.getRecipients());
        List<Decision> decisions = executeRrtFor(mail);

        applyDecisionsOnMailRecipients(mail, decisions);
        applyDecisionOnDSNParameters(mail, decisions);
        Collection<MailAddress> recipientsAfterRecipientsRewrite = mail.getRecipients();

        AuditTrail.entry()
            .protocol("mailetcontainer")
            .action("RecipientRewrite")
            .parameters(Throwing.supplier(() -> ImmutableMap.of("mailId", mail.getName(),
                "mimeMessageId", Optional.ofNullable(mail.getMessage())
                    .map(Throwing.function(MimeMessage::getMessageID))
                    .orElse(""),
                "sender", mail.getMaybeSender().asString(),
                "recipientsBeforeRewrite", StringUtils.join(recipientsBeforeRecipientsRewrite),
                "recipientsAfterRewrite", StringUtils.join(recipientsAfterRecipientsRewrite))))
            .log("Recipients rewritten.");

        processForwards(mail);
    }

    interface ForwardDecision {
        static ForwardDecision removeRecipient(MailAddress recipient) {
            return mail -> mail.setRecipients(mail.getRecipients()
                .stream()
                .filter(r -> !r.equals(recipient))
                .collect(ImmutableList.toImmutableList()));
        }

        static ForwardDecision sendACopy(MailetContext context, MailAddress originalRecipient, List<MailAddress> forwards) {
            return mail -> {
                MailImpl copy = MailImpl.duplicate(mail);
                try {
                    copy.setSender(originalRecipient);
                    copy.setRecipients(forwards);
                    context.sendMail(copy);

                    AuditTrail.entry()
                        .protocol("mailetcontainer")
                        .action("RecipientRewrite")
                        .parameters(Throwing.supplier(() -> ImmutableMap.of("mailId", mail.getName(),
                            "mimeMessageId", Optional.ofNullable(mail.getMessage())
                                .map(Throwing.function(MimeMessage::getMessageID))
                                .orElse(""),
                            "sender", mail.getMaybeSender().asString(),
                            "forwardedMailId", copy.getName(),
                            "forwardedMailSender", originalRecipient.asString(),
                            "forwardedMailRecipient", StringUtils.join(forwards))))
                        .log("Mail forwarded.");
                } finally {
                    LifecycleUtil.dispose(copy);
                }
            };
        }
        
        void apply(Mail mail) throws Exception;
    }

    public void processForwards(Mail mail) throws MessagingException {
        if (rewriteSenderUponForward) {
            mail.getRecipients()
                .stream()
                .flatMap(Throwing.function(this::processForward))
                .forEach(Throwing.consumer(decision -> decision.apply(mail)));
        }
    }

    private Stream<ForwardDecision> processForward(MailAddress recipient) throws RecipientRewriteTableException {
        ImmutableSet<Mapping> forwards = virtualTableStore.getStoredMappings(MappingSource.fromMailAddress(recipient))
            .select(Mapping.Type.Forward)
            .asStream()
            .collect(ImmutableSet.toImmutableSet());

        if (forwards.isEmpty()) {
            return Stream.of();
        }
        Mapping localCopyMapping = Mapping.forward(recipient.asString());
        boolean localCopy = forwards.contains(localCopyMapping);
        List<MailAddress> forwardedRecipients = forwards.stream()
            .filter(mapping -> !mapping.equals(localCopyMapping)) // remove the local copy
            .flatMap(mapping -> mapping.asMailAddress().stream())
            .collect(ImmutableList.toImmutableList());

        if (localCopy) {
            return Stream.of(ForwardDecision.sendACopy(mailetContext, recipient, forwardedRecipients));
        }
        return Stream.of(ForwardDecision.removeRecipient(recipient),
            ForwardDecision.sendACopy(mailetContext, recipient, forwardedRecipients));
    }

    private void applyDecisionOnDSNParameters(Mail mail, List<Decision> decisions) {
        mail.dsnParameters()
            .map(dsnParameters -> decisions.stream()
                .reduce(dsnParameters, (parameters, decision) -> decision.applyOnDsnParameters(parameters), (a, b) -> {
                    throw new NotImplementedException("No combiner needed as we are not in a multi-threaded environment");
                }))
            .ifPresent(mail::setDsnParameters);
    }

    private void applyDecisionsOnMailRecipients(Mail mail, List<Decision> decisions) throws MessagingException {
        RrtExecutionResult executionResults = decisions.stream()
            .map(Decision::executionResult)
            .reduce(RrtExecutionResult.empty(), RrtExecutionResult::merge);

        if (!executionResults.recipientWithError.isEmpty()) {
            MailImpl newMail = MailImpl.builder()
                .name(mail.getName())
                .sender(mail.getMaybeSender())
                .addRecipients(executionResults.recipientWithError)
                .mimeMessage(mail.getMessage())
                .state(errorProcessor.getValue())
                .build();
            mailetContext.sendMail(newMail);
            LifecycleUtil.dispose(newMail);
        }

        if (executionResults.newRecipients.isEmpty()) {
            mail.setState(Mail.GHOST);
        }

        mail.setRecipients(executionResults.newRecipients);
    }

    private List<Decision> executeRrtFor(Mail mail) {
        Function<MailAddress, Decision> convertToMappingData = recipient -> {
            Preconditions.checkNotNull(recipient);

            return executeRrtForRecipient(mail, recipient);
        };

        return mail.getRecipients()
            .stream()
            .map(convertToMappingData)
            .collect(ImmutableList.toImmutableList());
    }

    private Decision executeRrtForRecipient(Mail mail, MailAddress recipient) {
        try {
            Mappings mappings = virtualTableStore.getResolvedMappings(recipient.getLocalPart(), recipient.getDomain(), mappingTypes);

            if (mappings != null && !mappings.isEmpty()) {
                List<MailAddress> newMailAddresses = handleMappings(mappings, mail, recipient);
                return new Decision(recipient, RrtExecutionResult.success(newMailAddresses));
            }
            return new Decision(recipient, RrtExecutionResult.success(recipient));
        } catch (ErrorMappingException | RecipientRewriteTableException e) {
            LOGGER.warn("Could not rewrite recipient {}", recipient, e);
            return new Decision(recipient, RrtExecutionResult.error(recipient));
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
            .collect(ImmutableMap.toImmutableMap(
                Map.Entry::getKey,
                entry -> entry.getValue()
                    .stream()
                    .flatMap(domainEntry -> domainEntry.getValue().stream())
                    .collect(ImmutableList.toImmutableList())));
    }

    private Stream<Map.Entry<Domain, Collection<MailAddress>>> mailAddressesPerDomain(Mappings mappings) {
        return mappings.asStream()
            .map(mapping -> mapping.appendDomainIfNone(defaultDomainSupplier))
            .map(Mapping::asMailAddress)
            .flatMap(Optional::stream)
            .collect(ImmutableListMultimap.toImmutableListMultimap(
                MailAddress::getDomain, Function.identity()))
            .asMap()
            .entrySet()
            .stream();
    }

    private void forwardToRemoteAddress(Mail mail, MailAddress recipient, Collection<MailAddress> remoteRecipients) {
        if (!remoteRecipients.isEmpty()) {
            Mail duplicate = null;
            try {
                duplicate = mail.duplicate();
                duplicate.setRecipients(ImmutableList.copyOf(remoteRecipients));
                mailetContext.sendMail(duplicate);
                LOGGER.info("Mail for {} forwarded to {}", recipient, remoteRecipients);
            } catch (MessagingException ex) {
                LOGGER.warn("Error forwarding mail to {}", remoteRecipients);
            } finally {
                LifecycleUtil.dispose(duplicate);
            }
        }
    }

}
