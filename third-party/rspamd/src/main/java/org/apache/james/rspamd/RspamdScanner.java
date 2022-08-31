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

import org.apache.james.core.MailAddress;
import org.apache.james.rspamd.client.RspamdHttpClient;
import org.apache.james.rspamd.model.AnalysisResult;
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

public class RspamdScanner extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(RspamdScanner.class);

    public static final AttributeName FLAG_MAIL = AttributeName.of("org.apache.james.rspamd.flag");
    public static final AttributeName STATUS_MAIL = AttributeName.of("org.apache.james.rspamd.status");

    private final RspamdHttpClient rspamdHttpClient;
    private boolean rewriteSubject;
    private Optional<String> virusProcessor;
    private Optional<String> rejectSpamProcessor;

    @Inject
    public RspamdScanner(RspamdHttpClient rspamdHttpClient) {
        this.rspamdHttpClient = rspamdHttpClient;
    }

    @Override
    public void init() {
        rewriteSubject = getBooleanParameter(getInitParameter("rewriteSubject"), false);
        virusProcessor = getInitParameterAsOptional("virusProcessor");
        rejectSpamProcessor = getInitParameterAsOptional("rejectSpamProcessor");
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        AnalysisResult rspamdResult = rspamdHttpClient.checkV2(mail).block();

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
