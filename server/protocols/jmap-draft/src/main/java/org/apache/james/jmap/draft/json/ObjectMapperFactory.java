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

package org.apache.james.jmap.draft.json;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.model.mailbox.Rights;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;

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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class ObjectMapperFactory {

    private static final ImmutableSet.Builder<Module> JACKSON_BASE_MODULES = ImmutableSet.<Module>builder().add(
            new Jdk8Module(),
            new JavaTimeModule(),
            new GuavaModule());

    private final Set<Module> jacksonModules;

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
        mailboxIdModule.addSerializer(Username.class, new UsernameSerializer());
        mailboxIdModule.addDeserializer(Username.class, new UsernameDeserializer());
        mailboxIdModule.addKeyDeserializer(Username.class, new UsernameKeyDeserializer());
        mailboxIdModule.addKeySerializer(Username.class, new UsernameKeySerializer());
        mailboxIdModule.addDeserializer(Rights.Right.class, new RightDeserializer());

        SimpleModule mdnModule = new SimpleModule();
        mailboxIdModule.addDeserializer(DispositionActionMode.class, new MDNActionModeDeserializer());
        mailboxIdModule.addDeserializer(DispositionSendingMode.class, new MDNSendingModeDeserializer());
        mailboxIdModule.addDeserializer(DispositionType.class, new MDNTypeDeserializer());

        SimpleModule contentTypeModule = new SimpleModule();
        contentTypeModule.addDeserializer(ContentType.class, new ContentTypeDeserializer());
        contentTypeModule.addSerializer(ContentType.class, new ContentTypeSerializer());

        mailboxIdModule.setMixInAnnotation(Role.class, RoleMixIn.class);

        jacksonModules = JACKSON_BASE_MODULES.add(mailboxIdModule)
            .add(mdnModule)
            .add(contentTypeModule)
            .build();
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

    public static class MDNActionModeDeserializer extends JsonDeserializer<DispositionActionMode> {
        private static final ImmutableList<String> ALLOWED_VALUES = Arrays.stream(DispositionActionMode.values())
            .map(DispositionActionMode::getValue)
            .collect(ImmutableList.toImmutableList());

        @Override
        public DispositionActionMode deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            String value = jsonParser.getValueAsString();
            return DispositionActionMode.fromString(value)
                .orElseThrow(() -> new IllegalStateException(
                    String.format("Unrecognized MDN Disposition action mode %s. Should be one of %s", value, ALLOWED_VALUES)));
        }
    }

    public static class MDNSendingModeDeserializer extends JsonDeserializer<DispositionSendingMode> {
        private static final ImmutableList<String> ALLOWED_VALUES = Arrays.stream(DispositionSendingMode.values())
            .map(DispositionSendingMode::getValue)
            .collect(ImmutableList.toImmutableList());

        @Override
        public DispositionSendingMode deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            String value = jsonParser.getValueAsString();
            return DispositionSendingMode.fromString(value)
                .orElseThrow(() -> new IllegalStateException(
                    String.format("Unrecognized MDN Disposition sending mode %s. Should be one of %s", value, ALLOWED_VALUES)));
        }
    }

    public static class ContentTypeDeserializer extends JsonDeserializer<ContentType> {
        @Override
        public ContentType deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            String value = jsonParser.getValueAsString();
            return ContentType.of(value);
        }
    }

    public static class ContentTypeSerializer extends JsonSerializer<ContentType> {
        @Override
        public void serialize(ContentType value, JsonGenerator gen, SerializerProvider serializers) throws IOException, JsonProcessingException {
            gen.writeString(value.asString());
        }
    }

    public static class MDNTypeDeserializer extends JsonDeserializer<DispositionType> {
        private static final ImmutableList<String> ALLOWED_VALUES = Arrays.stream(DispositionType.values())
            .map(DispositionType::getValue)
            .collect(ImmutableList.toImmutableList());

        @Override
        public DispositionType deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            String value = jsonParser.getValueAsString();
            return DispositionType.fromString(value)
                .orElseThrow(() -> new IllegalStateException(
                    String.format("Unrecognized MDN Disposition type %s. Should be one of %s", value, ALLOWED_VALUES)));
        }
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

    public static class UsernameSerializer extends JsonSerializer<Username> {
        @Override
        public void serialize(Username value, JsonGenerator gen, SerializerProvider serializers) throws IOException, JsonProcessingException {
            gen.writeString(value.asString());
        }
    }

    public static class UsernameKeySerializer extends JsonSerializer<Username> {
        @Override
        public void serialize(Username value, JsonGenerator gen, SerializerProvider serializers) throws IOException, JsonProcessingException {
            gen.writeFieldName(value.asString());
        }
    }

    public static class UsernameDeserializer extends JsonDeserializer<Username> {
        @Override
        public Username deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            return Username.of(p.getValueAsString());
        }
    }

    public static class UsernameKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt) {
            return Username.of(key);
        }
    }

    public static class RightDeserializer extends JsonDeserializer<Rights.Right> {

        @Override
        public Rights.Right deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            String nodeValue = p.getValueAsString();
            Preconditions.checkArgument(nodeValue.length() == 1, "Rights should be represented as single value characters");

            return Rights.Right.forChar(nodeValue.charAt(0));
        }
    }

    public static class MailboxIdKeyDeserializer extends KeyDeserializer {
        private MailboxId.Factory factory;

        public MailboxIdKeyDeserializer(MailboxId.Factory factory) {
            this.factory = factory;
        }

        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt) {
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
        public Object deserializeKey(String key, DeserializationContext ctxt) {
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
