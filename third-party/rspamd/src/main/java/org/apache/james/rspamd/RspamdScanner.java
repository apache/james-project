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

package org.apache.james.rspamd;


import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.MailAddress;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.rspamd.client.RspamdClientConfiguration;
import org.apache.james.rspamd.client.RspamdHttpClient;
import org.apache.james.rspamd.model.AnalysisResult;
import org.apache.james.util.AuditTrail;
import org.apache.james.util.ReactorUtils;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.ProcessingState;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RspamdScanner extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(RspamdScanner.class);

    public static final AttributeName FLAG_MAIL = AttributeName.of("org.apache.james.rspamd.flag");
    public static final AttributeName STATUS_MAIL = AttributeName.of("org.apache.james.rspamd.status");

    private final RspamdHttpClient rspamdHttpClient;
    private final RspamdClientConfiguration configuration;
    private boolean rewriteSubject;
    private Optional<String> virusProcessor;
    private Optional<String> rejectSpamProcessor;

    @Inject
    public RspamdScanner(RspamdHttpClient rspamdHttpClient, RspamdClientConfiguration configuration) {
        this.rspamdHttpClient = rspamdHttpClient;
        this.configuration = configuration;
    }

    @Override
    public void init() {
        rewriteSubject = getBooleanParameter(getInitParameter("rewriteSubject"), false);
        virusProcessor = getInitParameterAsOptional("virusProcessor");
        rejectSpamProcessor = getInitParameterAsOptional("rejectSpamProcessor");
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        if (configuration.usePerUserBayes()) {
            scanPerUser(mail);
        } else {
            scanAll(mail);
        }
    }

    private void scanPerUser(Mail mail) {
        Flux.fromIterable(mail.getRecipients())
            .flatMap(Throwing.function(rcpt -> rspamdHttpClient.checkV2(mail, RspamdHttpClient.Options.forMailAddress(rcpt))
                .map(result -> Pair.of(rcpt, result))), ReactorUtils.DEFAULT_CONCURRENCY)
            .concatMap(rcptAndResult -> Mono.fromRunnable(() -> handleScanResult(mail, rcptAndResult))
                .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER))
            .blockLast();
    }

    private void handleScanResult(Mail mail, Pair<MailAddress, AnalysisResult> rcptAndResult) {
        AuditTrail.entry()
            .protocol("mailetcontainer")
            .action("RspamdScanner")
            .parameters(Throwing.supplier(() -> ImmutableMap.of("mailId", mail.getName(),
                "mimeMessageId", Optional.ofNullable(mail.getMessage())
                    .map(Throwing.function(MimeMessage::getMessageID))
                    .orElse(""),
                "sender", mail.getMaybeSender().asString(),
                "recipient", rcptAndResult.getKey().asString(),
                "rspamDAction", rcptAndResult.getValue().getAction().name(),
                "rspamDRequiredScore", Float.toString(rcptAndResult.getValue().getRequiredScore()),
                "rspamRewrittenSubject", rcptAndResult.getValue().getDesiredRewriteSubject().orElse(""),
                "rspamDScore", Float.toString(rcptAndResult.getValue().getScore()))))
            .log("Mail scanned with RSpamD.");

        if (AnalysisResult.Action.REJECT == rcptAndResult.getValue().getAction()) {
            rejectSpamProcessor.ifPresent(processor -> processorPerUser(mail, rcptAndResult.getKey(), processor));
        }

        appendRspamdResultHeader(mail, rcptAndResult.getKey(), rcptAndResult.getRight());

        if (rcptAndResult.getRight().hasVirus()) {
            virusProcessor.ifPresent(processor -> processorPerUser(mail, rcptAndResult.getKey(), processor));
        }
    }

    private void processorPerUser(Mail mail, MailAddress rcpt, String processor) {
        Mail copy = null;
        try {
            copy = mail.duplicate();
            copy.setRecipients(ImmutableList.of(rcpt));
            getMailetContext().sendMail(copy, processor);
        } catch (MessagingException e) {
            throw new RuntimeException("Error when processor per user", e);
        } finally {
            if (copy != null) {
                mail.setRecipients(Sets.difference(
                    ImmutableSet.copyOf(mail.getRecipients()),
                    ImmutableSet.of(rcpt)));
                LifecycleUtil.dispose(copy);
            }
        }
        if (virusProcessor.equals(Optional.of(processor))) {
            LOGGER.info("Detected a mail containing virus. Sending mail {} to {}", mail, virusProcessor);
            mail.setState(processor);
        }
    }

    private void scanAll(Mail mail) throws MessagingException {
        AnalysisResult rspamdResult = rspamdHttpClient.checkV2(mail).block();
        Preconditions.checkNotNull(rspamdResult);

        if (rspamdResult.getAction() == AnalysisResult.Action.REJECT) {
            rejectSpamProcessor.ifPresent(mail::setState);
        }

        mail.getRecipients()
            .forEach(recipient -> appendRspamdResultHeader(mail, recipient, rspamdResult));

        if (rewriteSubject) {
            rspamdResult.getDesiredRewriteSubject()
                .ifPresent(Throwing.consumer(desiredRewriteSubject -> mail.getMessage().setSubject(desiredRewriteSubject)));
        }

        if (rspamdResult.hasVirus()) {
            virusProcessor.ifPresent(state -> {
                LOGGER.info("Detected a mail containing virus. Sending mail {} to {}", mail, virusProcessor);
                mail.setState(state);
            });
        }
    }

    private void appendRspamdResultHeader(Mail mail, MailAddress recipient, AnalysisResult rspamdResult) {
        for (Attribute attribute : getHeadersAsAttributes(rspamdResult)) {
            mail.addSpecificHeaderForRecipient(PerRecipientHeaders.Header.builder()
                .name(attribute.getName().asString())
                .value((String) attribute.getValue().value())
                .build(), recipient);
        }
    }

    private List<Attribute> getHeadersAsAttributes(AnalysisResult rspamdResult) {
        String defaultFlagMailAttributeValue = "NO";
        String defaultStatusMailAttributeValue = "No";
        if (rspamdResult.getAction().equals(AnalysisResult.Action.REJECT)
                || rspamdResult.getAction().equals(AnalysisResult.Action.ADD_HEADER)
                || rspamdResult.getAction().equals(AnalysisResult.Action.REWRITE_SUBJECT)) {
            defaultFlagMailAttributeValue = "YES";
            defaultStatusMailAttributeValue = "Yes";
        }

        return ImmutableList.of(new Attribute(FLAG_MAIL, AttributeValue.of(defaultFlagMailAttributeValue)),
            new Attribute(STATUS_MAIL, AttributeValue.of(defaultStatusMailAttributeValue + ","
                + " actions=" + rspamdResult.getAction().getDescription()
                + " score=" + rspamdResult.getScore()
                + " requiredScore=" + rspamdResult.getRequiredScore())));
    }

    @Override
    public Collection<ProcessingState> requiredProcessingState() {
        return Stream.of(virusProcessor, rejectSpamProcessor)
            .flatMap(Optional::stream)
            .map(ProcessingState::new)
            .collect(ImmutableList.toImmutableList());
    }
}
