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

import java.util.EnumSet;

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
import org.apache.james.rrt.api.RecipientRewriteTable.ErrorMappingException;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping;
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
    private boolean ignoreMappingsWithoutLocalMailbox = false;

    @Inject
    public ValidRcptHandler(UsersRepository users, RecipientRewriteTable recipientRewriteTable, DomainList domains) {
        this.users = users;
        this.recipientRewriteTable = recipientRewriteTable;
        this.domains = domains;
    }

    public void setSupportsRecipientRewriteTable(boolean supportsRecipientRewriteTable) {
        this.supportsRecipientRewriteTable = supportsRecipientRewriteTable;
    }

    public void setIgnoreMappingsWithoutLocalMailbox(boolean ignoreMappingsWithoutLocalMailbox) {
        this.ignoreMappingsWithoutLocalMailbox = ignoreMappingsWithoutLocalMailbox;
    }

    @Override
    protected boolean isValidRecipient(SMTPSession session, MailAddress recipient) throws UsersRepositoryException, RecipientRewriteTableException {
        // Check existence of mailbox first to use RRT less often.
        if (mailboxExists(recipient)) {
            return true;
        } else {
            // Check whether there is a valid RRT entry for the recipient.
            return supportsRecipientRewriteTable && hasValidRRTEntry(recipient);
        }
    }

    protected boolean mailboxExists(MailAddress recipient) throws UsersRepositoryException {
        return users.contains(users.getUsername(recipient));
    }

    protected boolean hasValidRRTEntry(MailAddress recipient) throws RecipientRewriteTableException {
        LOGGER.debug("Unknown recipient {}, resolving it via RRT", recipient);
        try {
            // Error mappings are only used to forbid sending from the source address and can be ignored here.
            Mappings mappings = recipientRewriteTable.getResolvedMappings(
                recipient.getLocalPart(),
                recipient.getDomain(),
                EnumSet.complementOf(EnumSet.of(Mapping.Type.Error))
            );
            if (ignoreMappingsWithoutLocalMailbox) {
                return !mappings.isEmpty();
            } else {
                return allResolvedMailboxesExist(mappings);
            }
        } catch (ErrorMappingException e) {
            // As we filter the mappings above, this case should never happen.
            LOGGER.error("Unexpexted mapping of type Error: ", e);
            return false;
        }
    }

    private boolean allResolvedMailboxesExist(Mappings mappings) {
        return mappings
            .asStream()
            .flatMap(mapping -> mapping.asMailAddress().stream())
            .allMatch(address -> {
                try {
                    return mailboxExists(address);
                } catch (UsersRepositoryException e) {
                    return false;
                }
            });
    }

    @Override
    protected boolean isLocalDomain(SMTPSession session, Domain domain) throws DomainListException {
        return domains.containsDomain(domain);
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        setSupportsRecipientRewriteTable(config.getBoolean("enableRecipientRewriteTable", true));
        setIgnoreMappingsWithoutLocalMailbox(config.getBoolean("ignoreMappingsWithoutLocalMailbox", true));
    }
}
