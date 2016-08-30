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

package org.apache.james.jmap.json;

import java.io.IOException;
import java.util.Set;

import javax.inject.Inject;

import org.apache.james.mailbox.model.MailboxId;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableSet;

public class ObjectMapperFactory {

    private static final ImmutableSet.Builder<Module> JACKSON_BASE_MODULES = ImmutableSet.<Module>builder().add(new Jdk8Module(), new JavaTimeModule(), new GuavaModule());
    private final Set<Module> jacksonModules;
    
    @Inject
    public ObjectMapperFactory(MailboxId.Factory mailboxIdFactory) {
        SimpleModule mailboxIdModule = new SimpleModule();
        mailboxIdModule.addDeserializer(MailboxId.class, new MailboxIdDeserializer(mailboxIdFactory));
        mailboxIdModule.addSerializer(MailboxId.class, new MailboxIdSerializer());
        mailboxIdModule.addKeyDeserializer(MailboxId.class, new MailboxIdKeyDeserializer(mailboxIdFactory));
        mailboxIdModule.addKeySerializer(MailboxId.class, new MailboxIdKeySerializer());
        jacksonModules = JACKSON_BASE_MODULES.add(mailboxIdModule).build();
    }

    public ObjectMapper forParsing() {
        return new ObjectMapper()
                .registerModules(jacksonModules)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ObjectMapper forWriting() {
        return new ObjectMapper()
                .registerModules(jacksonModules)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    public static class MailboxIdDeserializer extends JsonDeserializer<MailboxId> {
        private MailboxId.Factory factory;

        public MailboxIdDeserializer(MailboxId.Factory factory) {
            this.factory = factory;
        }

        @Override
        public MailboxId deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            return factory.fromString(p.getValueAsString());
        }
    }

    public static class MailboxIdSerializer extends JsonSerializer<MailboxId> {

        @Override
        public void serialize(MailboxId value, JsonGenerator gen, SerializerProvider serializers) throws IOException, JsonProcessingException {
            gen.writeString(value.serialize());
        }
    }

    public static class MailboxIdKeyDeserializer extends KeyDeserializer {
        private MailboxId.Factory factory;

        public MailboxIdKeyDeserializer(MailboxId.Factory factory) {
            this.factory = factory;
        }

        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            return factory.fromString(key);
        }
    }

    public static class MailboxIdKeySerializer extends JsonSerializer<MailboxId> {

        @Override
        public void serialize(MailboxId value, JsonGenerator gen, SerializerProvider serializers) throws IOException, JsonProcessingException {
            gen.writeFieldName(value.serialize());
        }
    }

}
