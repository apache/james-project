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
package org.apache.james.mailbox.store.mail.model;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.mailbox.model.MessageId;

public class DefaultMessageId implements MessageId {

    public static class Factory implements MessageId.Factory {
        
        @Override
        public MessageId fromString(String serialized) {
            throw new NotImplementedException("MessageId is not supported by this backend");
        }
        
        @Override
        public MessageId generate() {
            return new DefaultMessageId();
        }
    }
    
    public DefaultMessageId() {
    }

    @Override
    public boolean isSerializable() {
        return false;
    }

    @Override
    public String serialize() {
        throw new IllegalStateException("Capabilities should prevent calling this method");
    }
    
    @Override
    public final boolean equals(Object obj) {
        throw new IllegalStateException("Capabilities should prevent calling this method");
    }
    
    @Override
    public final int hashCode() {
        throw new IllegalStateException("Capabilities should prevent calling this method");
    }

    @Override
    public String toString() {
        return "DefaultMessageId{}";
    }
}
