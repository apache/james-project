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

package org.apache.james.mailbox.cassandra.mail.utils;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.SimpleMailboxACL;

import java.io.IOException;

public class SimpleMailboxACLJsonConverter {

    interface Rfc4314RightsMixIn {
        @JsonValue
        int getValue();
    }

    static class ACLKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(String key, DeserializationContext deserializationContext ) throws IOException {
            return new SimpleMailboxACL.SimpleMailboxACLEntryKey(key);
        }
    }

    private static ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.addMixInAnnotations(SimpleMailboxACL.Rfc4314Rights.class, Rfc4314RightsMixIn.class);
        SimpleModule module = new SimpleModule();
        module.addAbstractTypeMapping(MailboxACL.MailboxACLEntryKey.class, SimpleMailboxACL.SimpleMailboxACLEntryKey.class);
        module.addAbstractTypeMapping(MailboxACL.MailboxACLRights.class, SimpleMailboxACL.Rfc4314Rights.class);
        module.addKeyDeserializer(MailboxACL.MailboxACLEntryKey.class, new ACLKeyDeserializer());
        objectMapper.registerModule(module);
    }

    public static String toJson(MailboxACL acl) throws JsonProcessingException {
        return objectMapper.writeValueAsString(acl);
    }
    
    public static MailboxACL toACL(String jsonACLString) throws IOException {
        return objectMapper.readValue(jsonACLString, SimpleMailboxACL.class);
    }
}
