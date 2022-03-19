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

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.server.core.MailImpl;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.transport.mailets.jsieve.ActionDispatcher;
import org.apache.james.transport.mailets.jsieve.ResourceLocator;
import org.apache.james.transport.mailets.jsieve.SieveMailAdapter;
import org.apache.jsieve.ConfigurationManager;
import org.apache.jsieve.SieveConfigurationException;
import org.apache.jsieve.SieveFactory;
import org.apache.jsieve.exception.SieveException;
import org.apache.jsieve.parser.generated.ParseException;
import org.apache.jsieve.parser.generated.TokenMgrError;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class SieveExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SieveExecutor.class);
    public static final AttributeName SIEVE_NOTIFICATION = AttributeName.of("SieveNotification");

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MailetContext mailetContext;
        private SievePoster sievePoster;
        private ResourceLocator resourceLocator;

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

        public SieveExecutor build() throws MessagingException {
            Preconditions.checkNotNull(mailetContext);
            Preconditions.checkNotNull(resourceLocator);
            Preconditions.checkNotNull(sievePoster);
            return new SieveExecutor(mailetContext, sievePoster, resourceLocator);
        }
    }

    private final MailetContext mailetContext;
    private final SievePoster sievePoster;
    private final ResourceLocator resourceLocator;
    private final SieveFactory factory;
    private final ActionDispatcher actionDispatcher;

    public SieveExecutor(MailetContext mailetContext, SievePoster sievePoster,
                         ResourceLocator resourceLocator) throws MessagingException {
        this.mailetContext = mailetContext;
        this.sievePoster = sievePoster;
        this.resourceLocator = resourceLocator;
        this.factory = createFactory();
        this.actionDispatcher = new ActionDispatcher();
    }

    private SieveFactory createFactory() throws MessagingException {
        try {
            return new ConfigurationManager().build();
        } catch (SieveConfigurationException e) {
            throw new MessagingException("Failed to load standard Sieve configuration.", e);
        }
    }

    public boolean execute(MailAddress recipient, Mail mail) throws MessagingException {
        Preconditions.checkNotNull(recipient, "Recipient for mail to be spooled cannot be null.");
        Preconditions.checkNotNull(mail.getMessage(), "Mail message to be spooled cannot be null.");
        boolean isSieveNotification = AttributeUtils.getValueAndCastFromMail(mail, SIEVE_NOTIFICATION, Boolean.class).orElse(false);
        return !isSieveNotification && sieveMessage(recipient, mail);
    }

    private boolean sieveMessage(MailAddress recipient, Mail aMail) {
        try {
            ResourceLocator.UserSieveInformation userSieveInformation = resourceLocator.get(recipient);
            sieveMessageEvaluate(recipient, aMail, userSieveInformation);
            return true;
        } catch (ScriptNotFoundException e) {
            LOGGER.info("Can not locate SIEVE script for user {}", recipient.asPrettyString());
            return false;
        } catch (Exception ex) {
            LOGGER.error("Cannot evaluate Sieve script for user {}", recipient.asPrettyString(), ex);
            return false;
        }
    }

    private void sieveMessageEvaluate(MailAddress recipient, Mail aMail, ResourceLocator.UserSieveInformation userSieveInformation) throws MessagingException {
        try {
            SieveMailAdapter aMailAdapter = new SieveMailAdapter(aMail,
                mailetContext, actionDispatcher, sievePoster, userSieveInformation.getScriptActivationDate(),
                userSieveInformation.getScriptInterpretationDate(), recipient);
            if (LOGGER.isDebugEnabled()) {
                // This logging operation is potentially costly
                LOGGER.debug("Evaluating {} against \"{}\"", aMailAdapter.toString(), recipient.asPrettyString());
            }
            factory.evaluate(aMailAdapter, factory.parse(userSieveInformation.getScriptContent()));
        } catch (SieveException | ParseException ex) {
            handleFailure(recipient, aMail, ex);
        } catch (TokenMgrError ex) {
            handleFailure(recipient, aMail, new SieveException(ex));
        }
    }

    @VisibleForTesting
    void handleFailure(MailAddress recipient, Mail aMail, Exception ex) throws MessagingException {
        MailImpl errorMail = MailImpl.builder()
            .name(MailImpl.getId())
            .addAttribute(new Attribute(SIEVE_NOTIFICATION, AttributeValue.of(true)))
            .sender(recipient)
            .addRecipient(recipient)
            .mimeMessage(SieveFailureMessageComposer.composeMessage(aMail, ex, recipient.toString()))
            .build();

        mailetContext.sendMail(errorMail);
        errorMail.dispose();
    }
}
