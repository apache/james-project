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

package org.apache.james.mailbox.store.json;

import java.io.IOException;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageId.Factory;
import org.apache.james.mailbox.store.event.EventSerializer;
import org.apache.james.mailbox.store.json.event.EventConverter;
import org.apache.james.mailbox.store.json.event.dto.EventDataTransferObject;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class JacksonEventSerializer implements EventSerializer {

    private final EventConverter eventConverter;
    private final ObjectMapper objectMapper;

    public JacksonEventSerializer(EventConverter eventConverter, ObjectMapper objectMapper) {
        this.eventConverter = eventConverter;
        this.objectMapper = objectMapper;
    }

    public byte[] serializeEvent(MailboxListener.MailboxEvent event) throws Exception {
        return objectMapper.writeValueAsBytes(eventConverter.convertToDataTransferObject(event));
    }

    public MailboxListener.MailboxEvent deSerializeEvent(byte[] serializedEvent) throws Exception {
        EventDataTransferObject eventDataTransferObject = objectMapper.readValue(serializedEvent, EventDataTransferObject.class);
        return eventConverter.retrieveEvent(eventDataTransferObject);
    }

    public static ObjectMapper configureObjectMapper(ObjectMapper objectMapper, MessageId.Factory messageIdFactory) {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(MessageUid.class, new MessageUidDeserializer());
        module.addKeyDeserializer(MessageUid.class, new MessageUidKeyDeserializer());
        module.addSerializer(MessageUid.class, new MessageUidSerializer());
        module.addKeySerializer(MessageUid.class, new MessageUidKeySerializer());
        module.addSerializer(MessageId.class, new MessageIdSerializer());
        module.addDeserializer(MessageId.class, new MessageIdDeserializer(messageIdFactory));
        objectMapper.registerModule(module);
        return objectMapper;
    }

    public static class MessageUidDeserializer extends JsonDeserializer<MessageUid> {

        @Override
        public MessageUid deserialize(JsonParser parser, DeserializationContext context) throws IOException, JsonProcessingException {
            return MessageUid.of(Long.parseLong(parser.getValueAsString()));
        }
        
    }

    public static class MessageUidSerializer extends JsonSerializer<MessageUid> {

        @Override
        public void serialize(MessageUid value, JsonGenerator generator, SerializerProvider serializers) throws IOException, JsonProcessingException {
            generator.writeString(String.valueOf(value.asLong()));
        }
        
    }

    public static class MessageUidKeyDeserializer extends KeyDeserializer {

        @Override
        public Object deserializeKey(String key, DeserializationContext context) throws IOException, JsonProcessingException {
            return MessageUid.of(Long.parseLong(key));
        }
        
    }

    public static class MessageUidKeySerializer extends JsonSerializer<MessageUid> {

        @Override
        public void serialize(MessageUid value, JsonGenerator generator, SerializerProvider serializers) throws IOException, JsonProcessingException {
            generator.writeFieldName(String.valueOf(value.asLong()));
        }
        
    }

    public static class MessageIdSerializer extends JsonSerializer<MessageId> {

        @Override
        public void serialize(MessageId value, JsonGenerator generator, SerializerProvider serializers) throws IOException, JsonProcessingException {
            generator.writeString(String.valueOf(value.serialize()));
        }
        
    }

    public static class MessageIdDeserializer extends JsonDeserializer<MessageId> {
        private final Factory factory;

        public MessageIdDeserializer(MessageId.Factory factory) {
            this.factory = factory;
        }

        @Override
        public MessageId deserialize(JsonParser p, DeserializationContext context) throws IOException, JsonProcessingException {
            return factory.fromString(p.getValueAsString());
        }
        
    }

}
