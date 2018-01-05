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


package org.apache.james.mailets.configuration;

import org.apache.james.jmap.mailet.VacationMailet;
import org.apache.james.transport.mailets.AddDeliveredToHeader;
import org.apache.james.transport.mailets.Bounce;
import org.apache.james.transport.mailets.DSNBounce;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.mailets.Null;
import org.apache.james.transport.mailets.PostmasterAlias;
import org.apache.james.transport.mailets.RecipientRewriteTable;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.james.transport.mailets.SetMailAttribute;
import org.apache.james.transport.mailets.SetMimeHeader;
import org.apache.james.transport.mailets.Sieve;
import org.apache.james.transport.mailets.ToProcessor;
import org.apache.james.transport.mailets.ToRepository;
import org.apache.james.transport.mailets.managesieve.ManageSieveMailet;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.HasMailAttribute;
import org.apache.james.transport.matchers.InSpammerBlacklist;
import org.apache.james.transport.matchers.RecipientIs;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.james.transport.matchers.RelayLimit;
import org.apache.james.transport.matchers.SMTPAuthSuccessful;
import org.apache.mailet.Mail;

public class CommonProcessors {

	public static final String ERROR_REPOSITORY = "file://var/mail/error/";

    public static ProcessorConfiguration root() {
        return ProcessorConfiguration.root()
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(PostmasterAlias.class))
                .addMailet(MailetConfiguration.builder()
                        .matcher(RelayLimit.class)
                        .matcherCondition("30")
                        .mailet(Null.class))
                .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIs.class)
                        .matcherCondition("sievemanager@james.linagora.com")
                        .mailet(ToProcessor.class)
                        .addProperty("processor", "sieve-manager-check"))
                .addMailet(MailetConfiguration.builder()
                        .matcher(HasMailAttribute.class)
                        .matcherCondition("spamChecked")
                        .mailet(ToProcessor.class)
                        .addProperty("processor", ProcessorConfiguration.STATE_TRANSPORT))
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(SetMailAttribute.class)
                        .addProperty("spamChecked", "true"))
                .addMailet(MailetConfiguration.builder()
                        .matcher(SMTPAuthSuccessful.class)
                        .mailet(ToProcessor.class)
                        .addProperty("processor", ProcessorConfiguration.STATE_TRANSPORT))
                .addMailet(MailetConfiguration.builder()
                        .matcher(InSpammerBlacklist.class)
                        .matcherCondition("query.bondedsender.org.")
                        .mailet(ToProcessor.class)
                        .addProperty("processor", ProcessorConfiguration.STATE_TRANSPORT))
                .addMailet(MailetConfiguration.builder()
                        .matcher(InSpammerBlacklist.class)
                        .matcherCondition("dnsbl.njabl.org.")
                        .mailet(ToProcessor.class)
                        .addProperty("processor", ProcessorConfiguration.STATE_SPAM)
                        .addProperty("notice", "550 Requested action not taken: rejected - see http://njabl.org/"))
                .addMailet(MailetConfiguration.TO_TRANSPORT)
                .build();
    }

    public static ProcessorConfiguration simpleRoot() {
        return ProcessorConfiguration.root()
            .addMailet(MailetConfiguration.TO_TRANSPORT)
            .build();
    }

    public static ProcessorConfiguration error() {
        return ProcessorConfiguration.error()
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(Bounce.class))
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", ERROR_REPOSITORY))
                .build();
    }

    public static ProcessorConfiguration transport() {
        return ProcessorConfiguration.transport()
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .matcher(SMTPAuthSuccessful.class)
                        .mailet(SetMimeHeader.class)
                        .addProperty("name", "X-UserIsAuth")
                        .addProperty("value", "true"))
                .addMailet(MailetConfiguration.builder()
                        .matcher(HasMailAttribute.class)
                        .matcherCondition("org.apache.james.SMIMECheckSignature")
                        .mailet(SetMimeHeader.class)
                        .addProperty("name", "X-WasSigned")
                        .addProperty("value", "true"))
                .addMailet(MailetConfiguration.BCC_STRIPPER)
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(RecipientRewriteTable.class))
                .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIsLocal.class)
                        .mailet(VacationMailet.class))
                .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIsLocal.class)
                        .mailet(Sieve.class))
                .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIsLocal.class)
                        .mailet(AddDeliveredToHeader.class))
                .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIsLocal.class)
                        .mailet(LocalDelivery.class))
                .addMailet(MailetConfiguration.builder()
                        .matcher(SMTPAuthSuccessful.class)
                        .mailet(RemoteDelivery.class)
                        .addProperty("outgoingQueue", "outgoing")
                        .addProperty("delayTime", "5000, 100000, 500000")
                        .addProperty("maxRetries", "25")
                        .addProperty("maxDnsProblemRetries", "0")
                        .addProperty("deliveryThreads", "10")
                        .addProperty("sendpartial", "true")
                        .addProperty("bounceProcessor", "bounces"))
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToProcessor.class)
                        .addProperty("processor", "relay-denied"))
                .build();
    }

    public static ProcessorConfiguration deliverOnlyTransport() {
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.LOCAL_DELIVERY)
            .build();
    }

    public static ProcessorConfiguration spam() {
        return ProcessorConfiguration.spam()
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", "file://var/mail/spam/"))
                .build();
    }

    public static ProcessorConfiguration localAddressError() {
        return ProcessorConfiguration.builder()
                .state("local-address-error")
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(Bounce.class)
                        .addProperty("attachment", "none"))
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", "file://var/mail/address-error/"))
                .build();
    }

    public static ProcessorConfiguration relayDenied() {
        return ProcessorConfiguration.builder()
                .state("replay-denied")
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(Bounce.class)
                        .addProperty("attachment", "none"))
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", "file://var/mail/relay-denied/")
                        .addProperty("notice", "Warning: You are sending an e-mail to a remote server. You must be authentified to perform such an operation"))
                .build();
    }

    public static ProcessorConfiguration bounces() {
        return ProcessorConfiguration.bounces()
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(DSNBounce.class)
                        .addProperty("passThrough", "false"))
                .build();
    }

    public static ProcessorConfiguration sieveManagerCheck() {
        return ProcessorConfiguration.builder()
                .state("sieve-manager-check")
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIsLocal.class)
                        .mailet(ToProcessor.class)
                        .addProperty("processor", "sieve-manager"))
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(Bounce.class)
                        .addProperty("inline", "heads")
                        .addProperty("attachment", "none")
                        .addProperty("passThrough", "false")
                        .addProperty("prefix", "[REJECTED]")
                        .addProperty("notice", "You can't send messages to configure SIEVE on this serveur unless you are the official SIEVE manager."))
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(Null.class))
                .build();
    }

    public static ProcessorConfiguration sieveManager() {
        return ProcessorConfiguration.builder()
                .state("sieve-manager")
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(SetMailAttribute.class)
                        .addProperty(Mail.SMTP_AUTH_USER_ATTRIBUTE_NAME, "true"))
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ManageSieveMailet.class)
                        .addProperty("helpURL", "file:/root/james-server-app-3.0.0-beta5-SNAPSHOT/conf/managesieve.help.txt"))
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(Null.class))
                .build();
    }
}
