/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.mailbox.model;

import java.util.Objects;

import jakarta.inject.Inject;

import com.google.common.base.MoreObjects;


public class ThreadId {
    public static class Factory {
        private final MessageId.Factory messageIdFactory;

        @Inject
        public Factory(MessageId.Factory messageIdFactory) {
            this.messageIdFactory = messageIdFactory;
        }

        public ThreadId fromString(String serialized) {
            MessageId messageId = messageIdFactory.fromString(serialized);
            return fromBaseMessageId(messageId);
        }
    }

    public static ThreadId fromBaseMessageId(MessageId baseMessageId) {
        return new ThreadId(baseMessageId);
    }

    private final MessageId baseMessageId;

    public ThreadId(MessageId baseMessageId) {
        this.baseMessageId = baseMessageId;
    }

    public MessageId getBaseMessageId() {
        return baseMessageId;
    }

    public String serialize() {
        return baseMessageId.serialize();
    }

    public boolean isSerializable() {
        return baseMessageId.isSerializable();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ThreadId) {
            ThreadId that = (ThreadId) o;
            return Objects.equals(this.baseMessageId, that.baseMessageId);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(baseMessageId);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("baseMessageId", baseMessageId)
            .toString();
    }
}
