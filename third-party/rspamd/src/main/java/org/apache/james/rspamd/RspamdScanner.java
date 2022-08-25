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

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.MailAddress;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.rspamd.client.RspamdHttpClient;
import org.apache.james.rspamd.model.AnalysisResult;
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RspamdScanner extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(RspamdScanner.class);

    public static final AttributeName FLAG_MAIL = AttributeName.of("org.apache.james.rspamd.flag");
    public static final AttributeName STATUS_MAIL = AttributeName.of("org.apache.james.rspamd.status");

    private final RspamdHttpClient rspamdHttpClient;
    private boolean rewriteSubject;
    private boolean perUserScans;
    private Optional<String> virusProcessor;
    private Optional<String> rejectSpamProcessor;

    @Inject
    public RspamdScanner(RspamdHttpClient rspamdHttpClient) {
        this.rspamdHttpClient = rspamdHttpClient;
    }

    @Override
    public void init() {
        perUserScans = getBooleanParameter(getInitParameter("perUserScans"), false);
        rewriteSubject = getBooleanParameter(getInitParameter("rewriteSubject"), false);
        virusProcessor = getInitParameterAsOptional("virusProcessor");
        rejectSpamProcessor = getInitParameterAsOptional("rejectSpamProcessor");
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        if (perUserScans) {
            scanPerUser(mail);
        } else {
            scanAll(mail);
        }
    }

    private void scanPerUser(Mail mail) {
        Flux.fromIterable(mail.getRecipients())
            .flatMap(Throwing.function(rcpt -> rspamdHttpClient.checkV2(mail, RspamdHttpClient.Options.forMailAddress(rcpt))
                .map(result -> Pair.of(rcpt, result))), ReactorUtils.DEFAULT_CONCURRENCY)
            .concatMap(rcptAndResult -> Mono.fromRunnable(Throwing.runnable(() -> {
                if (rcptAndResult.getValue().getAction() == AnalysisResult.Action.REJECT) {
                    rejectForUser(mail, rcptAndResult);
                }
                appendRspamdResultHeader(mail, rcptAndResult.getKey(), rcptAndResult.getRight());

                if (rcptAndResult.getRight().hasVirus()) {
                    virusForUser(mail, rcptAndResult);
                }
            })).subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER))
            .blockLast();
    }

    private void virusForUser(Mail mail, Pair<MailAddress, AnalysisResult> rcptAndResult) {
        virusProcessor.ifPresent(Throwing.consumer(state -> {
            LOGGER.info("Detected a mail containing virus. Sending mail {} to {}", mail, virusProcessor);
            Mail copy = mail.duplicate();
            try {
                copy.setRecipients(ImmutableList.of(rcptAndResult.getKey()));
                getMailetContext().sendMail(copy, state);
            } finally {
                mail.setRecipients(Sets.difference(
                    ImmutableSet.copyOf(mail.getRecipients()),
                    ImmutableSet.of(rcptAndResult.getKey())));
                LifecycleUtil.dispose(copy);
            }
            mail.setState(state);
        }));
    }

    private void rejectForUser(Mail mail, Pair<MailAddress, AnalysisResult> rcptAndResult) {
        rejectSpamProcessor.ifPresent(Throwing.consumer(spamProcessor -> {
            Mail copy = mail.duplicate();
            try {
                copy.setRecipients(ImmutableList.of(rcptAndResult.getKey()));
                getMailetContext().sendMail(copy, spamProcessor);
            } finally {
                mail.setRecipients(Sets.difference(
                    ImmutableSet.copyOf(mail.getRecipients()),
                    ImmutableSet.of(rcptAndResult.getKey())));
                LifecycleUtil.dispose(copy);
            }
        }));
    }

    private void scanAll(Mail mail) throws MessagingException {
        AnalysisResult rspamdResult = rspamdHttpClient.checkV2(mail, RspamdHttpClient.Options.NONE).block();

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
