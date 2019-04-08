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
package org.apache.james.mailbox.backup;

import org.apache.james.mailbox.model.MessageId;

import com.google.common.base.Objects;

public class SerializedMessageId {
    private final String value;

    public SerializedMessageId(String value) {
        this.value = value;
    }

    public SerializedMessageId(MessageId messageId) {
        this.value = messageId.serialize();
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SerializedMessageId) {
            SerializedMessageId that = (SerializedMessageId) o;
            return Objects.equal(value, that.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

}
