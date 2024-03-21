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

package org.apache.james.mailbox.postgres.mail.dto;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.UuidBackedAttachmentId;
import org.apache.james.mailbox.postgres.mail.MessageRepresentation;
import org.jooq.BindingGetResultSetContext;
import org.jooq.BindingSetStatementContext;
import org.jooq.Converter;
import org.jooq.impl.AbstractConverter;
import org.jooq.postgres.extensions.bindings.AbstractPostgresBinding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.r2dbc.postgresql.codec.Json;

public class AttachmentsDTO extends ArrayList<MessageRepresentation.AttachmentRepresentation> implements Serializable {

    public static class AttachmentsDTOConverter extends AbstractConverter<Object, AttachmentsDTO> {
        private static final long serialVersionUID = 1L;
        private static final String ATTACHMENT_ID_PROPERTY = "attachment_id";
        private static final String NAME_PROPERTY = "name";
        private static final String CID_PROPERTY = "cid";
        private static final String IN_LINE_PROPERTY = "in_line";
        private final ObjectMapper objectMapper;

        public AttachmentsDTOConverter() {
            super(Object.class, AttachmentsDTO.class);
            this.objectMapper = new ObjectMapper();
            this.objectMapper.registerModule(new Jdk8Module());
        }

        @Override
        public AttachmentsDTO from(Object databaseObject) {
            if (databaseObject instanceof Json) {
                try {
                    JsonNode arrayNode = objectMapper.readTree(((Json) databaseObject).asArray());
                    List<MessageRepresentation.AttachmentRepresentation> collect = StreamSupport.stream(arrayNode.spliterator(), false)
                        .map(this::fromJsonNode)
                        .collect(Collectors.toList());
                    return new AttachmentsDTO(collect);
                } catch (Exception e) {
                    throw new RuntimeException("Error while deserializing attachment representation", e);
                }
            }
            throw new RuntimeException("Error while deserializing attachment representation. Unknown type: " + databaseObject.getClass().getName());
        }

        @Override
        public Object to(AttachmentsDTO userObject) {
            try {
                byte[] jsonAsByte = objectMapper.writeValueAsBytes(userObject
                    .stream().map(attachment -> Map.of(
                        ATTACHMENT_ID_PROPERTY, attachment.getAttachmentId().getId(),
                        NAME_PROPERTY, attachment.getName(),
                        CID_PROPERTY, attachment.getCid().map(Cid::getValue),
                        IN_LINE_PROPERTY, attachment.isInline())).collect(Collectors.toList()));
                return Json.of(jsonAsByte);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        private MessageRepresentation.AttachmentRepresentation fromJsonNode(JsonNode jsonNode) {
            AttachmentId attachmentId = UuidBackedAttachmentId.from(jsonNode.get(ATTACHMENT_ID_PROPERTY).asText());
            Optional<String> name = Optional.ofNullable(jsonNode.get(NAME_PROPERTY)).map(JsonNode::asText);
            Optional<Cid> cid = Optional.ofNullable(jsonNode.get(CID_PROPERTY)).map(JsonNode::asText).map(Cid::from);
            boolean isInline = jsonNode.get(IN_LINE_PROPERTY).asBoolean();

            return new MessageRepresentation.AttachmentRepresentation(attachmentId, name, cid, isInline);
        }
    }

    public static class AttachmentsDTOBinding extends AbstractPostgresBinding<Object, AttachmentsDTO> {
        private static final long serialVersionUID = 1L;
        private static final Converter<Object, AttachmentsDTO> CONVERTER = new AttachmentsDTOConverter();

        @Override
        public Converter<Object, AttachmentsDTO> converter() {
            return CONVERTER;
        }

        @Override
        public void set(final BindingSetStatementContext<AttachmentsDTO> ctx) throws SQLException {
            Object value = ctx.convert(converter()).value();

            ctx.statement().setObject(ctx.index(), value == null ? null : value);
        }


        @Override
        public void get(final BindingGetResultSetContext<AttachmentsDTO> ctx) throws SQLException {
            ctx.convert(converter()).value((Json) ctx.resultSet().getObject(ctx.index()));
        }
    }

    public static AttachmentsDTO from(List<MessageAttachmentMetadata> messageAttachmentMetadata) {
        return new AttachmentsDTO(MessageRepresentation.AttachmentRepresentation.from(messageAttachmentMetadata));
    }

    private static final long serialVersionUID = 1L;

    public AttachmentsDTO(Collection<? extends MessageRepresentation.AttachmentRepresentation> c) {
        super(c);
    }


}
