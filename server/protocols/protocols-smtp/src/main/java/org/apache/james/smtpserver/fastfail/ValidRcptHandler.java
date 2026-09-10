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
package org.apache.james.smtpserver.fastfail;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.fastfail.AbstractValidRcptHandler;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.api.RecipientValidator;
import org.apache.james.rrt.api.RecipientValidator.RecipientRewriteTableCheck;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;

/**
 * Handler which reject invalid recipients
 */
public class ValidRcptHandler extends AbstractValidRcptHandler implements ProtocolHandler {
    private final RecipientValidator recipientValidator;

    private RecipientValidator.Policy policy = RecipientValidator.Policy.DEFAULT;

    @Inject
    public ValidRcptHandler(UsersRepository users, RecipientRewriteTable recipientRewriteTable, DomainList domains) {
        this(new RecipientValidator(users, recipientRewriteTable, domains));
    }

    public ValidRcptHandler(RecipientValidator recipientValidator) {
        this.recipientValidator = recipientValidator;
    }

    public void setSupportsRecipientRewriteTable(boolean supportsRecipientRewriteTable) {
        this.policy = new RecipientValidator.Policy(supportsRecipientRewriteTable, policy.recipientRewriteTableCheck());
    }

    public void setRecipientRewriteTableCheck(RecipientRewriteTableCheck recipientRewriteTableCheck) {
        this.policy = new RecipientValidator.Policy(policy.supportsRecipientRewriteTable(), recipientRewriteTableCheck);
    }

    @Override
    protected boolean isValidRecipient(SMTPSession session, MailAddress recipient) throws UsersRepositoryException, RecipientRewriteTableException {
        return recipientValidator.isValidLocalRecipient(recipient, policy);
    }

    @Override
    protected boolean isLocalDomain(SMTPSession session, Domain domain) throws DomainListException {
        return recipientValidator.isLocalDomain(domain);
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        this.policy = RecipientValidator.Policy.from(config);
    }
}
