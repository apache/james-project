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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.TimeZone;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.ser.SerializerFactory;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;

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

    private static final ImmutableSet.Builder<Module> JACKSON_BASE_MODULES = ImmutableSet.<Module>builder().add(
            new Jdk8Module(),
            new JavaTimeModule(),
            new GuavaModule());

    private final Set<Module> jacksonModules;
    private static final TimeZone UTC_TIMEZONE = TimeZone.getTimeZone("UTC");

    @Inject
    public ObjectMapperFactory(MailboxId.Factory mailboxIdFactory, MessageId.Factory messageIdFactory) {
        SimpleModule mailboxIdModule = new SimpleModule();
        mailboxIdModule.addDeserializer(MailboxId.class, new MailboxIdDeserializer(mailboxIdFactory));
        mailboxIdModule.addSerializer(MailboxId.class, new MailboxIdSerializer());
        mailboxIdModule.addKeyDeserializer(MailboxId.class, new MailboxIdKeyDeserializer(mailboxIdFactory));
        mailboxIdModule.addKeySerializer(MailboxId.class, new MailboxIdKeySerializer());

        mailboxIdModule.addDeserializer(MessageId.class, new MessageIdDeserializer(messageIdFactory));
        mailboxIdModule.addSerializer(MessageId.class, new MessageIdSerializer());
        mailboxIdModule.addKeyDeserializer(MessageId.class, new MessageIdKeyDeserializer(messageIdFactory));
        mailboxIdModule.addKeySerializer(MessageId.class, new MessageIdKeySerializer());
        jacksonModules = JACKSON_BASE_MODULES.add(mailboxIdModule).build();
    }

    public ObjectMapper forParsing() {
        return new ObjectMapper()
                .registerModules(jacksonModules)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ObjectMapper forWriting() {
        return new ObjectMapper()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .registerModules(jacksonModules);
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

    public static class MessageIdDeserializer extends JsonDeserializer<MessageId> {
        private MessageId.Factory factory;

        public MessageIdDeserializer(MessageId.Factory factory) {
            this.factory = factory;
        }

        @Override
        public MessageId deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            return factory.fromString(p.getValueAsString());
        }
    }

    public static class MessageIdKeyDeserializer extends KeyDeserializer {
        private MessageId.Factory factory;

        public MessageIdKeyDeserializer(MessageId.Factory factory) {
            this.factory = factory;
        }

        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            return factory.fromString(key);
        }
    }

    public static class MessageIdKeySerializer extends JsonSerializer<MessageId> {

        @Override
        public void serialize(MessageId value, JsonGenerator gen, SerializerProvider serializers) throws IOException, JsonProcessingException {
            gen.writeFieldName(value.serialize());
        }
    }

    
    public static class MessageIdSerializer extends JsonSerializer<MessageId> {

        @Override
        public void serialize(MessageId value, JsonGenerator gen, SerializerProvider serializers) throws IOException, JsonProcessingException {
            gen.writeString(value.serialize());
        }
    }

}
