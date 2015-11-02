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

import java.util.ArrayList;
import java.util.Collection;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTable.ErrorMappingException;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.MailAddress;

/**
 * Receives a Mail from JamesSpoolManager and takes care of delivery of the
 * message to local inboxes.
 * 
 * Available configurations are:
 * 
 * <code>&lt;enableAliases&gt;true&lt;/enableAliases&gt;</code>: specify wether
 * the user aliases should be looked up or not. Default is false.
 * 
 * <code>&lt;enableForwarding&gt;true&lt;/enableForwarding&gt;</code>: enable
 * the forwarding. Default to false.
 * 
 * 
 * @deprecated use org.apache.james.transport.mailets.RecipientRewriteTable
 */
@Deprecated
public class UsersRepositoryAliasingForwarding extends AbstractRecipientRewriteTableMailet {

    /**
     * The user repository for this mail server. Contains all the users with
     * inboxes on this server.
     */
    private UsersRepository usersRepository;

    /**
     * Return a string describing this mailet.
     * 
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "Local User Aliasing and Forwarding Mailet";
    }

    @Inject
    public void setUsersRepository(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    /**
     * Return null when the mail should be GHOSTed, the username string when it
     * should be changed due to the ignoreUser configuration.
     * 
     * @param sender
     * @param recipient
     * @param message
     * @throws MessagingException
     */
    public Collection<MailAddress> processMail(MailAddress sender, MailAddress recipient, MimeMessage message) throws MessagingException {
        if (recipient == null) {
            throw new IllegalArgumentException("Recipient for mail to be spooled cannot be null.");
        }
        if (message == null) {
            throw new IllegalArgumentException("Mail message to be spooled cannot be null.");
        }

        if (usersRepository instanceof RecipientRewriteTable) {
            Mappings mappings;
            try {
                mappings = ((RecipientRewriteTable) usersRepository).getMappings(recipient.getLocalPart(), recipient.getDomain());
            } catch (ErrorMappingException e) {
                String errorBuffer = "A problem as occoured trying to alias and forward user " + recipient + ": " + e.getMessage();
                throw new MessagingException(errorBuffer);
            } catch (RecipientRewriteTableException e) {
                String errorBuffer = "A problem as occoured trying to alias and forward user " + recipient + ": " + e.getMessage();
                throw new MessagingException(errorBuffer);
            }

            if (mappings != null) {
                return handleMappings(mappings, sender, recipient, message);
            }
        }
        ArrayList<MailAddress> ret = new ArrayList<MailAddress>();
        ret.add(recipient);
        return ret;

    }

}
