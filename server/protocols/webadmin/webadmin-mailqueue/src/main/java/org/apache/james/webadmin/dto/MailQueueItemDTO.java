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

package org.apache.james.webadmin.dto;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.queue.api.ManageableMailQueue;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class MailQueueItemDTO {

    public static Builder builder() {
        return new Builder();
    }

    public static MailQueueItemDTO from(ManageableMailQueue.MailQueueItemView mailQueueItemView) {
        return builder()
                .name(mailQueueItemView.getMail().getName())
                .sender(mailQueueItemView.getMail().getMaybeSender().asOptional())
                .recipients(mailQueueItemView.getMail().getRecipients())
                .nextDelivery(mailQueueItemView.getNextDelivery())
                .build();
    }

    public static class Builder {

        private String name;
        private String sender;
        private List<String> recipients;
        private Optional<ZonedDateTime> nextDelivery;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder sender(MailAddress sender) {
            this.sender = sender.asString();
            return this;
        }

        public Builder sender(Optional<MailAddress> sender) {
            sender.ifPresent(this::sender);
            return this;
        }

        public Builder recipients(Collection<MailAddress> recipients) {
            this.recipients = recipients.stream()
                    .map(MailAddress::asString)
                    .collect(Guavate.toImmutableList());
            return this;
        }

        public Builder nextDelivery(Optional<ZonedDateTime> nextDelivery) {
            this.nextDelivery = nextDelivery;
            return this;
        }

        public MailQueueItemDTO build() {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "name is mandatory");
            return new MailQueueItemDTO(name, sender, recipients, nextDelivery);
        }
    }

    private final String name;
    private final String sender;
    private final List<String> recipients;
    private final Optional<ZonedDateTime> nextDelivery;

    public MailQueueItemDTO(String name, String sender, List<String> recipients, Optional<ZonedDateTime> nextDelivery) {
        this.name = name;
        this.sender = sender;
        this.recipients = recipients;
        this.nextDelivery = nextDelivery;
    }

    public String getName() {
        return name;
    }

    public String getSender() {
        return sender;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public Optional<ZonedDateTime> getNextDelivery() {
        return nextDelivery;
    }
}
