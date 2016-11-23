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

public class CommonProcessors {

    public static ProcessorConfiguration root() {
        return ProcessorConfiguration.builder()
                .state("root")
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("PostmasterAlias")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("RelayLimit=30")
                        .clazz("Null")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("RecipientIs=sievemanager@james.linagora.com")
                        .clazz("ToProcessor")
                        .addProperty("processor", "sieve-manager-check")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("HasMailAttribute=spamChecked")
                        .clazz("ToProcessor")
                        .addProperty("processor", "transport")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("SetMailAttribute")
                        .addProperty("spamChecked", "true")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("SMTPAuthSuccessful")
                        .clazz("ToProcessor")
                        .addProperty("processor", "transport")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("InSpammerBlacklist=query.bondedsender.org.")
                        .clazz("ToProcessor")
                        .addProperty("processor", "transport")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("InSpammerBlacklist=dnsbl.njabl.org.")
                        .clazz("ToProcessor")
                        .addProperty("processor", "spam")
                        .addProperty("notice", "550 Requested action not taken: rejected - see http://njabl.org/")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("ToProcessor")
                        .addProperty("processor", "transport")
                        .build())
                .build();
    }

    public static ProcessorConfiguration error() {
        return ProcessorConfiguration.builder()
                .state("error")
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("Bounce")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("ToRepository")
                        .addProperty("repositoryPath", "file://var/mail/error/")
                        .build())
                .build();
    }

    public static ProcessorConfiguration transport() {
        return ProcessorConfiguration.builder()
                .state("transport")
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .match("SMTPAuthSuccessful")
                        .clazz("SetMimeHeader")
                        .addProperty("name", "X-UserIsAuth")
                        .addProperty("value", "true")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("HasMailAttribute=org.apache.james.SMIMECheckSignature")
                        .clazz("SetMimeHeader")
                        .addProperty("name", "X-WasSigned")
                        .addProperty("value", "true")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("RemoveMimeHeader")
                        .addProperty("name", "bcc")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("RecipientRewriteTable")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("RecipientIsLocal")
                        .clazz("org.apache.james.jmap.mailet.VacationMailet")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("RecipientIsLocal")
                        .clazz("Sieve")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("RecipientIsLocal")
                        .clazz("LocalDelivery")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("SMTPAuthSuccessful")
                        .clazz("RemoteDelivery")
                        .addProperty("outgoingQueue", "outgoing")
                        .addProperty("delayTime", "5000, 100000, 500000")
                        .addProperty("maxRetries", "25")
                        .addProperty("maxDnsProblemRetries", "0")
                        .addProperty("deliveryThreads", "10")
                        .addProperty("sendpartial", "true")
                        .addProperty("bounceProcessor", "bounces")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("ToProcessor")
                        .addProperty("processor", "relay-denied")
                        .build())
                .build();
    }

    public static ProcessorConfiguration spam() {
        return ProcessorConfiguration.builder()
                .state("spam")
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("ToRepository")
                        .addProperty("repositoryPath", "file://var/mail/spam/")
                        .build())
                .build();
    }

    public static ProcessorConfiguration localAddressError() {
        return ProcessorConfiguration.builder()
                .state("local-address-error")
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("Bounce")
                        .addProperty("attachment", "none")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("ToRepository")
                        .addProperty("repositoryPath", "file://var/mail/address-error/")
                        .build())
                .build();
    }

    public static ProcessorConfiguration relayDenied() {
        return ProcessorConfiguration.builder()
                .state("replay-denied")
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("Bounce")
                        .addProperty("attachment", "none")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("ToRepository")
                        .addProperty("repositoryPath", "file://var/mail/relay-denied/")
                        .addProperty("notice", "Warning: You are sending an e-mail to a remote server. You must be authentified to perform such an operation")
                        .build())
                .build();
    }

    public static ProcessorConfiguration bounces() {
        return ProcessorConfiguration.builder()
                .state("bounces")
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("DSNBounce")
                        .addProperty("passThrough", "false")
                        .build())
                .build();
    }

    public static ProcessorConfiguration sieveManagerCheck() {
        return ProcessorConfiguration.builder()
                .state("sieve-manager-check")
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .match("RecipientIsLocal")
                        .clazz("ToProcessor")
                        .addProperty("processor", "sieve-manager")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("Bounce")
                        .addProperty("inline", "heads")
                        .addProperty("attachment", "none")
                        .addProperty("passThrough", "false")
                        .addProperty("prefix", "[REJECTED]")
                        .addProperty("notice", "You can't send messages to configure SIEVE on this serveur unless you are the official SIEVE manager.")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("Null")
                        .build())
                .build();
    }

    public static ProcessorConfiguration sieveManager() {
        return ProcessorConfiguration.builder()
                .state("sieve-manager")
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("SetMailAttribute")
                        .addProperty("org.apache.james.SMTPAuthUser", "true")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("org.apache.james.transport.mailets.managesieve.ManageSieveMailet")
                        .addProperty("helpURL", "file:/root/james-server-app-3.0.0-beta5-SNAPSHOT/conf/managesieve.help.txt")
                        .build())
                .addMailet(MailetConfiguration.builder()
                        .match("All")
                        .clazz("Null")
                        .build())
                .build();
    }
}
