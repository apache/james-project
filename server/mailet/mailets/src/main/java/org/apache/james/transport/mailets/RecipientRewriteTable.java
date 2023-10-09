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

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.domainlist.api.DomainList;
import org.apache.mailet.Mail;
import org.apache.mailet.ProcessingState;
import org.apache.mailet.base.GenericMailet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Mailet which should get used when using RecipientRewriteTable-Store to
 * implementations for mappings of forwards and aliases.
 *
 * By specifying an 'errorProcessor' you can specify your logic upon RecipientRewriteTable failures.
 *
 * Exemple:
 *
 * <pre>
 * <code>
 *  &lt;mailet match=&quot;All&quot; class=&quot;RecipientRewriteTable&quot;&gt;
 *    &lt;errorProcessor&gt;x@rrt-errors&lt;/errorProcessor&gt;
 *  &lt;/mailet&gt;
 * </code>
 * </pre>
 *
 * The <b>rewriteSenderUponForward</b> option (fault to true) can be used to prevent senders to be rewritten upon forwards in the transport enveloppe
 * (JAMES 3.8.0 default behaviour). <b>WARNING</b>: Please note that not rewriting the sender will cause issues forwarding emails
 * from external senders to external addresses as the DKIM and SPF records will not be matching the ones of the sending
 * domain.
 */
public class RecipientRewriteTable extends GenericMailet {
    public static final String ERROR_PROCESSOR = "errorProcessor";

    private final org.apache.james.rrt.api.RecipientRewriteTable virtualTableStore;
    private final DomainList domainList;
    private RecipientRewriteTableProcessor processor;
    private ProcessingState errorProcessor;
    private boolean rewriteSenderUponForward = true;

    @Inject
    public RecipientRewriteTable(org.apache.james.rrt.api.RecipientRewriteTable virtualTableStore, DomainList domainList) {
        this.virtualTableStore = virtualTableStore;
        this.domainList = domainList;
    }

    @Override
    public void init() throws MessagingException {
        errorProcessor = new ProcessingState(getInitParameter(ERROR_PROCESSOR, Mail.ERROR));
        rewriteSenderUponForward = getBooleanParameter("rewriteSenderUponForward", true);
        processor = new RecipientRewriteTableProcessor(virtualTableStore, domainList, getMailetContext(), errorProcessor,
            rewriteSenderUponForward);
    }


    /**
     * The service rewrite the recipient list of mail. The method should:
     * - Set Return-Path and remove all other Return-Path headers from the mail's message. This only works because there is a placeholder inserted by MimeMessageWrapper
     * - If there were errors, we redirect the email to the ERROR processor. In order for this server to meet the requirements of the SMTP
     * specification, mails on the ERROR processor must be returned to the sender. Note that this email doesn't include any details
     * regarding the details of the failure(s). In the future we may wish to address this.
     * - Set the mail's state to <code>Mail.GHOST</code> if the recipients be empty after rewriting.
     */
    @Override
    public void service(Mail mail) throws MessagingException {
        Preconditions.checkNotNull(mail);
        MimeMessage message = mail.getMessage();

        if (message != null) {
            processor.processMail(mail);
        }

    }

    @Override
    public String getMailetInfo() {
        return "RecipientRewriteTable Mailet";
    }

    @Override
    public Collection<ProcessingState> requiredProcessingState() {
        return ImmutableList.of(errorProcessor);
    }
}
