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

package org.apache.james.mailbox.cassandra.json;

import java.io.IOException;

import org.apache.james.mailbox.model.MailboxACL;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class MailboxACLJsonConverter {

    interface Rfc4314RightsMixIn {
        @JsonValue
        String serialize();
    }

    static class ACLKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(String key, DeserializationContext deserializationContext) throws IOException {
            return MailboxACL.EntryKey.deserialize(key);
        }
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule()
                .addKeyDeserializer(MailboxACL.EntryKey.class, new ACLKeyDeserializer());
        objectMapper
            .addMixIn(MailboxACL.Rfc4314Rights.class, Rfc4314RightsMixIn.class)
            .registerModule(module);
    }

    public static String toJson(MailboxACL acl) throws JsonProcessingException {
        return objectMapper.writeValueAsString(acl);
    }
    
    public static MailboxACL toACL(String jsonACLString) throws IOException {
        return objectMapper.readValue(jsonACLString, MailboxACL.class);
    }
}
