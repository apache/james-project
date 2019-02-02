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

package org.apache.james.jmap.methods;

import org.apache.james.jmap.model.CreationMessage;
import org.apache.james.jmap.model.CreationMessageId;
import org.apache.james.jmap.model.JmapMDN;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.SetError;

import com.google.common.base.MoreObjects;

public class ValueWithId<T> {

    private CreationMessageId creationId;
    private T value;

    private ValueWithId(CreationMessageId creationId, T value) {
        this.creationId = creationId;
        this.value = value;
    }

    public CreationMessageId getCreationId() {
        return creationId;
    }

    public T getValue() {
        return value;
    }

    public static class CreationMessageEntry extends ValueWithId<CreationMessage> {
        public CreationMessageEntry(CreationMessageId creationId, CreationMessage message) {
            super(creationId, message);
        }
    }

    public static class MDNCreationEntry extends ValueWithId<JmapMDN> {
        public MDNCreationEntry(CreationMessageId creationId, JmapMDN mdn) {
            super(creationId, mdn);
        }
    }

    public static class ErrorWithId extends ValueWithId<SetError> {
        public ErrorWithId(CreationMessageId creationId, SetError error) {
            super(creationId, error);
        }
    }

    public static class MessageWithId extends ValueWithId<Message> {
        public MessageWithId(CreationMessageId creationId, Message message) {
            super(creationId, message);
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("creationId", creationId)
            .add("value", value)
            .toString();
    }
}
