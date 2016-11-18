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


package org.apache.james.transport.mailets.jsieve.delivery;

import java.io.IOException;

import javax.mail.MessagingException;

import org.apache.commons.logging.Log;
import org.apache.james.transport.mailets.delivery.DeliveryUtils;
import org.apache.james.transport.mailets.jsieve.ActionDispatcher;
import org.apache.james.transport.mailets.jsieve.ResourceLocator;
import org.apache.james.transport.mailets.jsieve.SieveMailAdapter;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.jsieve.ConfigurationManager;
import org.apache.jsieve.SieveConfigurationException;
import org.apache.jsieve.SieveFactory;
import org.apache.jsieve.exception.SieveException;
import org.apache.jsieve.parser.generated.ParseException;
import org.apache.jsieve.parser.generated.TokenMgrError;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class SieveExecutor {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MailetContext mailetContext;
        private UsersRepository usersRepos;
        private SievePoster sievePoster;
        private ResourceLocator resourceLocator;
        private Log log;

        public Builder usersRepository(UsersRepository usersRepository) {
            this.usersRepos = usersRepository;
            return this;
        }

        public Builder sievePoster(SievePoster sievePoster) {
            this.sievePoster = sievePoster;
            return this;
        }

        public Builder mailetContext(MailetContext mailetContext) {
            this.mailetContext = mailetContext;
            return this;
        }

        public Builder resourceLocator(ResourceLocator resourceLocator) {
            this.resourceLocator = resourceLocator;
            return this;
        }

        public Builder log(Log log) {
            this.log = log;
            return this;
        }

        public SieveExecutor build() throws MessagingException {
            Preconditions.checkNotNull(mailetContext);
            Preconditions.checkNotNull(usersRepos);
            Preconditions.checkNotNull(resourceLocator);
            Preconditions.checkNotNull(log);
            Preconditions.checkNotNull(sievePoster);
            return new SieveExecutor(mailetContext, usersRepos, sievePoster, resourceLocator, log);
        }
    }

    private final MailetContext mailetContext;
    private final UsersRepository usersRepos;
    private final SievePoster sievePoster;
    private final ResourceLocator resourceLocator;
    private final SieveFactory factory;
    private final ActionDispatcher actionDispatcher;
    private final Log log;

    public SieveExecutor(MailetContext mailetContext, UsersRepository usersRepos, SievePoster sievePoster,
                         ResourceLocator resourceLocator, Log log) throws MessagingException {
        this.mailetContext = mailetContext;
        this.usersRepos = usersRepos;
        this.sievePoster = sievePoster;
        this.resourceLocator = resourceLocator;
        factory = createFactory(log);
        this.actionDispatcher = new ActionDispatcher();
        this.log = log;
    }

    private SieveFactory createFactory(Log log) throws MessagingException {
        try {
            final ConfigurationManager configurationManager = new ConfigurationManager();
            configurationManager.setLog(log);
            return configurationManager.build();
        } catch (SieveConfigurationException e) {
            throw new MessagingException("Failed to load standard Sieve configuration.", e);
        }
    }

    public void execute(MailAddress recipient, Mail mail) throws MessagingException {
        Preconditions.checkNotNull(recipient, "Recipient for mail to be spooled cannot be null.");
        Preconditions.checkNotNull(mail.getMessage(), "Mail message to be spooled cannot be null.");

        sieveMessage(recipient, mail, log);
        // If no exception was thrown the message was successfully stored in the mailbox
        log.info("Local delivered mail " + mail.getName() + " sucessfully from " + DeliveryUtils.prettyPrint(mail.getSender()) + " to " + DeliveryUtils.prettyPrint(recipient)
            + " in folder " + this.folder);
    }

    protected void sieveMessage(MailAddress recipient, Mail aMail, Log log) throws MessagingException {
        try {
            ResourceLocator.UserSieveInformation userSieveInformation = resourceLocator.get(getScriptUri(recipient));
            sieveMessageEvaluate(recipient, aMail, userSieveInformation, log);
        } catch (Exception ex) {
            // SIEVE is a mail filtering protocol.
            // Rejecting the mail because it cannot be filtered
            // seems very unfriendly.
            // So just log and store in INBOX
            log.error("Cannot evaluate Sieve script. Storing mail in user INBOX.", ex);
            storeMessageInbox(recipient, aMail);
        }
    }

    private void sieveMessageEvaluate(MailAddress recipient, Mail aMail, ResourceLocator.UserSieveInformation userSieveInformation, Log log) throws MessagingException, IOException {
        try {
            SieveMailAdapter aMailAdapter = new SieveMailAdapter(aMail,
                mailetContext, actionDispatcher, sievePoster, userSieveInformation.getScriptActivationDate(),
                userSieveInformation.getScriptInterpretationDate(), recipient);
            aMailAdapter.setLog(log);
            // This logging operation is potentially costly
            log.debug("Evaluating " + aMailAdapter.toString() + "against \"" + getScriptUri(recipient) + "\"");
            factory.evaluate(aMailAdapter, factory.parse(userSieveInformation.getScriptContent()));
        } catch (SieveException ex) {
            handleFailure(recipient, aMail, ex);
        }
        catch (ParseException ex) {
            handleFailure(recipient, aMail, ex);
        }
        catch (TokenMgrError ex) {
            handleFailure(recipient, aMail, new SieveException(ex));
        }
    }

    protected String getScriptUri(MailAddress m) {
        return "//" + retrieveUserNameUsedForScriptStorage(m) + "/sieve";
    }

    protected void handleFailure(MailAddress recipient, Mail aMail, Exception ex) throws MessagingException, IOException {
        mailetContext.sendMail(recipient, ImmutableList.of(recipient), SieveFailureMessageComposer.composeMessage(aMail, ex, recipient.toString()));
    }

    protected void storeMessageInbox(MailAddress mailAddress, Mail mail) throws MessagingException {
        sievePoster.post("mailbox://" + retrieveUserNameUsedForScriptStorage(mailAddress) + "/", mail);
    }

    public String retrieveUserNameUsedForScriptStorage(MailAddress mailAddress) {
        try {
            if (usersRepos.supportVirtualHosting()) {
                return mailAddress.asString();
            } else {
                return mailAddress.getLocalPart() + "@localhost";
            }
        } catch (UsersRepositoryException e) {
            log.warn("Can not determine if virtual hosting is used for " + mailAddress.asPrettyString(), e);
            return mailAddress.getLocalPart() + "@localhost";
        }
    }
}
