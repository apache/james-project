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

import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.protocols.lib.lifecycle.InitializingLifecycleAwareProtocolHandler;
import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.fastfail.AbstractValidRcptHandler;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTable.ErrorMappingException;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;

/**
 * Handler which reject invalid recipients
 */
public class ValidRcptHandler extends AbstractValidRcptHandler implements InitializingLifecycleAwareProtocolHandler {

    private UsersRepository users;

    private RecipientRewriteTable vut;

    private boolean useVut = true;

    private DomainList domains;

    /**
     * Gets the users repository.
     * 
     * @return the users
     */
    public final UsersRepository getUsers() {
        return users;
    }

    /**
     * Sets the users repository.
     * 
     * @param users
     *            the users to set
     */
    @Inject
    public final void setUsersRepository(@Named("usersrepository") UsersRepository users) {
        this.users = users;
    }

    /**
     * Sets the virtual user table store.
     * 
     * @param vut
     *            the tableStore to set
     */
    @Inject
    public final void setRecipientRewriteTable(RecipientRewriteTable vut) {
        this.vut = vut;
    }

    @Inject
    public void setDomainList(@Named("domainlist") DomainList domains) {
        this.domains = domains;
    }
    
    public void setRecipientRewriteTableSupport(boolean useVut) {
        this.useVut = useVut;
    }

    @Override
    protected boolean isValidRecipient(SMTPSession session, MailAddress recipient) {

        String username = recipient.toString();

        // check if the server use virtualhosting, if not use only the localpart
        // as username
        try {
            if (!users.supportVirtualHosting()) {
                username = recipient.getLocalPart();
            }

            if (users.contains(username)) {
                return true;
            } else {

                if (useVut) {
                    session.getLogger().debug("Unknown user " + username + " check if its an alias");

                    try {
                        Collection<String> targetString = vut.getMappings(recipient.getLocalPart(), recipient.getDomain());

                        if (targetString != null && !targetString.isEmpty()) {
                            return true;
                        }
                    } catch (ErrorMappingException e) {
                        return false;
                    } catch (RecipientRewriteTableException e) {
                        session.getLogger().info("Unable to access RecipientRewriteTable", e);
                        return false;
                    }
                }

                return false;
            }
        } catch (UsersRepositoryException e) {
            session.getLogger().info("Unable to access UsersRepository", e);
            return false;

        }
    }

    /**
     * @see
     * org.apache.james.protocols.smtp.core.fastfail.AbstractValidRcptHandler
     * #isLocalDomain(org.apache.james.protocols.smtp.SMTPSession,
     * java.lang.String)
     */
    protected boolean isLocalDomain(SMTPSession session, String domain) {
        try {
            return domains.containsDomain(domain);
        } catch (DomainListException e) {
            session.getLogger().error("Unable to get domains", e);
            return false;
        }
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        setRecipientRewriteTableSupport(config.getBoolean("enableRecipientRewriteTable", true));
        
    }

    @Override
    public void destroy() {
        // nothing to-do
    }
}
