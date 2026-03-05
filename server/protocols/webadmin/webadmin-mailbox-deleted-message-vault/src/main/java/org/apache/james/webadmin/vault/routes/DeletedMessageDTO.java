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

package org.apache.james.webadmin.vault.routes;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.james.vault.DeletedMessage;

import com.google.common.collect.ImmutableList;

public class DeletedMessageDTO {

    public static DeletedMessageDTO from(DeletedMessage message) {
        return new DeletedMessageDTO(
            message.getMessageId().serialize(),
            message.getOriginMailboxes().stream()
                .map(id -> id.serialize())
                .collect(ImmutableList.toImmutableList()),
            message.getOwner().asString(),
            message.getDeliveryDate(),
            message.getDeletionDate(),
            message.getSender().asOptional().map(Object::toString),
            message.getRecipients().stream()
                .map(Object::toString)
                .collect(ImmutableList.toImmutableList()),
            message.getSubject(),
            message.hasAttachment(),
            message.getSize());
    }

    private final String messageId;
    private final List<String> originMailboxes;
    private final String owner;
    private final ZonedDateTime deliveryDate;
    private final ZonedDateTime deletionDate;
    private final Optional<String> sender;
    private final List<String> recipients;
    private final Optional<String> subject;
    private final boolean hasAttachment;
    private final long size;

    private DeletedMessageDTO(String messageId, List<String> originMailboxes, String owner,
                               ZonedDateTime deliveryDate, ZonedDateTime deletionDate,
                               Optional<String> sender, List<String> recipients,
                               Optional<String> subject, boolean hasAttachment, long size) {
        this.messageId = messageId;
        this.originMailboxes = originMailboxes;
        this.owner = owner;
        this.deliveryDate = deliveryDate;
        this.deletionDate = deletionDate;
        this.sender = sender;
        this.recipients = recipients;
        this.subject = subject;
        this.hasAttachment = hasAttachment;
        this.size = size;
    }

    public String getMessageId() {
        return messageId;
    }

    public List<String> getOriginMailboxes() {
        return originMailboxes;
    }

    public String getOwner() {
        return owner;
    }

    public ZonedDateTime getDeliveryDate() {
        return deliveryDate;
    }

    public ZonedDateTime getDeletionDate() {
        return deletionDate;
    }

    public Optional<String> getSender() {
        return sender;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public Optional<String> getSubject() {
        return subject;
    }

    public boolean isHasAttachment() {
        return hasAttachment;
    }

    public long getSize() {
        return size;
    }
}
