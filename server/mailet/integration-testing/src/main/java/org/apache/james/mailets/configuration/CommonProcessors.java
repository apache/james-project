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

import org.apache.james.jmap.mailet.filter.JMAPFiltering;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.transport.mailets.AddDeliveredToHeader;
import org.apache.james.transport.mailets.Bounce;
import org.apache.james.transport.mailets.DSNBounce;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.mailets.Null;
import org.apache.james.transport.mailets.PostmasterAlias;
import org.apache.james.transport.mailets.RecipientRewriteTable;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.james.transport.mailets.SetMimeHeader;
import org.apache.james.transport.mailets.Sieve;
import org.apache.james.transport.mailets.ToProcessor;
import org.apache.james.transport.mailets.ToRepository;
import org.apache.james.transport.mailets.VacationMailet;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.HasMailAttribute;
import org.apache.james.transport.matchers.IsSenderInRRTLoop;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.james.transport.matchers.RelayLimit;
import org.apache.james.transport.matchers.SMTPAuthSuccessful;

public class CommonProcessors {

    public static final MailRepositoryUrl ERROR_REPOSITORY = MailRepositoryUrl.from("memory://var/mail/error/");
    public static final MailRepositoryUrl RRT_ERROR_REPOSITORY = MailRepositoryUrl.from("memory://var/mail/rrt-error/");
    private static final String RRT_ERROR = "rrt-error";

    public static ProcessorConfiguration root() {
        return ProcessorConfiguration.root()
                .enableJmx(false)
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(PostmasterAlias.class))
                .addMailet(MailetConfiguration.builder()
                        .matcher(RelayLimit.class)
                        .matcherCondition("30")
                        .mailet(Null.class))
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
                .enableJmx(false)
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(Bounce.class))
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", ERROR_REPOSITORY.asString()))
                .build();
    }

    public static ProcessorConfiguration transport() {
        return ProcessorConfiguration.transport()
                .enableJmx(false)
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
                        .mailet(JMAPFiltering.class))
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
                        .addProperty("maxRetries", "3")
                        .addProperty("maxDnsProblemRetries", "0")
                        .addProperty("deliveryThreads", "10")
                        .addProperty("sendpartial", "true")
                        .addProperty("bounceProcessor", "bounces"))
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToProcessor.class)
                        .addProperty("processor", "error"))
                .build();
    }

    public static ProcessorConfiguration deliverOnlyTransport() {
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.LOCAL_DELIVERY)
            .build();
    }

    public static ProcessorConfiguration bounces() {
        return ProcessorConfiguration.bounces()
                .enableJmx(false)
                .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(DSNBounce.class)
                        .addProperty("passThrough", "false"))
                .build();
    }

    public static ProcessorConfiguration.Builder rrtErrorEnabledTransport() {
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(RecipientRewriteTable.class)
                .addProperty("errorProcessor", RRT_ERROR))
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIsLocal.class)
                .mailet(VacationMailet.class))
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIsLocal.class)
                .mailet(JMAPFiltering.class))
            .addMailetsFrom(CommonProcessors.deliverOnlyTransport());
    }

    public static ProcessorConfiguration.Builder rrtError() {
        return ProcessorConfiguration.builder()
            .state(RRT_ERROR)
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(ToRepository.class)
                .addProperty("passThrough", "true")
                .addProperty("repositoryPath", RRT_ERROR_REPOSITORY.asString()))
            .addMailet(MailetConfiguration.builder()
                .matcher(IsSenderInRRTLoop.class)
                .mailet(Null.class))
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(Bounce.class));
    }
}
