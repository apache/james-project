/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.mailbox.store.mail.utils;

import java.util.List;
import java.util.Optional;

import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.model.Subject;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.field.UnstructuredField;
import org.apache.james.mime4j.field.UnstructuredFieldImpl;
import org.apache.james.mime4j.message.HeaderImpl;
import org.apache.james.mime4j.stream.Field;

import com.github.steveash.guavate.Guavate;

public class MimeMessageHeadersUtil {
    public static Optional<MimeMessageId> parseMimeMessageId(HeaderImpl headers) {
        return Optional.ofNullable(headers.getField("Message-ID")).map(field -> new MimeMessageId(field.getBody()));
    }

    public static Optional<MimeMessageId> parseInReplyTo(HeaderImpl headers) {
        return Optional.ofNullable(headers.getField("In-Reply-To")).map(field -> new MimeMessageId(field.getBody()));
    }

    public static Optional<List<MimeMessageId>> parseReferences(HeaderImpl headers) {
        List<Field> mimeMessageIdFields = headers.getFields("References");
        if (!mimeMessageIdFields.isEmpty()) {
            List<MimeMessageId> mimeMessageIdList = mimeMessageIdFields.stream()
                .map(mimeMessageIdField -> new MimeMessageId(mimeMessageIdField.getBody()))
                .collect(Guavate.toImmutableList());
            return Optional.of(mimeMessageIdList);
        }
        return Optional.empty();
    }

    public static Optional<Subject> parseSubject(HeaderImpl headers) {
        return Optional.ofNullable(headers.getField("Subject"))
            .map(field -> {
                if (!(field instanceof UnstructuredField)) {
                    field = UnstructuredFieldImpl.PARSER.parse(field, DecodeMonitor.SILENT);
                }
                return (UnstructuredField) field;
            })
            .map(unstructuredField -> new Subject(unstructuredField.getValue()));
    }
}
