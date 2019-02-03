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

import javax.inject.Inject;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.fastfail.AbstractValidRcptHandler;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTable.ErrorMappingException;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler which reject invalid recipients
 */
public class ValidRcptHandler extends AbstractValidRcptHandler implements ProtocolHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidRcptHandler.class);

    private final UsersRepository users;
    private final RecipientRewriteTable recipientRewriteTable;
    private final DomainList domains;

    private boolean supportsRecipientRewriteTable = true;

    @Inject
    public ValidRcptHandler(UsersRepository users, RecipientRewriteTable recipientRewriteTable, DomainList domains) {
        this.users = users;
        this.recipientRewriteTable = recipientRewriteTable;
        this.domains = domains;
    }

    public void setSupportsRecipientRewriteTable(boolean supportsRecipientRewriteTable) {
        this.supportsRecipientRewriteTable = supportsRecipientRewriteTable;
    }

    @Override
    protected boolean isValidRecipient(SMTPSession session, MailAddress recipient) {
        try {
            String username = users.getUser(recipient);

            if (users.contains(username)) {
                return true;
            } else {
                return supportsRecipientRewriteTable && isRedirected(recipient, username);
            }
        } catch (UsersRepositoryException e) {
            LOGGER.info("Unable to access UsersRepository", e);
            return false;
        }
    }

    private boolean isRedirected(MailAddress recipient, String username) {
        LOGGER.debug("Unknown user {} check if it's an alias", username);

        try {
            Mappings targetString = recipientRewriteTable.getResolvedMappings(recipient.getLocalPart(), recipient.getDomain());

            if (!targetString.isEmpty()) {
                return true;
            }
        } catch (ErrorMappingException e) {
            return true;
        } catch (RecipientRewriteTableException e) {
            LOGGER.info("Unable to access RecipientRewriteTable", e);
            return false;
        }
        return false;
    }

    @Override
    protected boolean isLocalDomain(SMTPSession session, Domain domain) {
        try {
            return domains.containsDomain(domain);
        } catch (DomainListException e) {
            LOGGER.error("Unable to get domains", e);
            return false;
        }
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        setSupportsRecipientRewriteTable(config.getBoolean("enableRecipientRewriteTable", true));
    }

    @Override
    public void destroy() {
        // nothing to-do
    }
}
