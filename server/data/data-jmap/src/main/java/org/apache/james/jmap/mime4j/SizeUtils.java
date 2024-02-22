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

package org.apache.james.jmap.mime4j;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.SingleBody;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.dom.field.FieldName;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.message.MessageImpl;
import org.apache.james.mime4j.message.MultipartImpl;
import org.apache.james.mime4j.util.ByteSequence;
import org.apache.james.mime4j.util.ContentUtil;

import com.google.common.io.CountingOutputStream;

public class SizeUtils {
    public static long sizeOf(Entity entity) throws IOException {
        if (entity instanceof BodyPart) {
            BodyPart bodyPart = (BodyPart) entity;

            return sizeOf(bodyPart.getBody());
        }
        if (entity instanceof MessageImpl) {
            MessageImpl bodyPart = (MessageImpl) entity;

            return sizeOf(bodyPart.getBody());
        }
        CountingOutputStream countingOutputStream = new CountingOutputStream(OutputStream.nullOutputStream());
        DefaultMessageWriter defaultMessageWriter = new DefaultMessageWriter();
        defaultMessageWriter.writeEntity(entity, countingOutputStream);
        return countingOutputStream.getCount();
    }

    public static long sizeOf(Header header) throws IOException {
        CountingOutputStream countingOutputStream = new CountingOutputStream(OutputStream.nullOutputStream());
        DefaultMessageWriter defaultMessageWriter = new DefaultMessageWriter();
        defaultMessageWriter.writeHeader(header, countingOutputStream);
        return countingOutputStream.getCount();
    }

    public static long sizeOf(Body body) throws IOException {
        if (body instanceof FakeBinaryBody) {
            return ((FakeBinaryBody) body).getSize();
        }
        if (body instanceof SingleBody) {
            return ((SingleBody) body).size();
        }
        if (body instanceof Multipart) {
            return sizeOfMultipart((Multipart) body);
        }
        if (body instanceof Message) {
            Message message = (Message) body;
            return sizeOf(message.getHeader()) + sizeOf(message.getBody());
        }
        CountingOutputStream countingOutputStream = new CountingOutputStream(OutputStream.nullOutputStream());
        DefaultMessageWriter defaultMessageWriter = new DefaultMessageWriter();
        defaultMessageWriter.writeBody(body, countingOutputStream);
        return countingOutputStream.getCount();
    }

    // Inspired from DefaultMessageWriter
    public static long sizeOfMultipart(Multipart multipart)
        throws IOException {
        long result = 0;
        ContentTypeField contentType = getContentType(multipart);

        ByteSequence boundary = getBoundary(contentType);

        ByteSequence preamble;
        ByteSequence epilogue;
        if (multipart instanceof MultipartImpl) {
            preamble = ((MultipartImpl) multipart).getPreambleRaw();
            epilogue = ((MultipartImpl) multipart).getEpilogueRaw();
        } else {
            preamble = multipart.getPreamble() != null ? ContentUtil.encode(multipart.getPreamble()) : null;
            epilogue = multipart.getEpilogue() != null ? ContentUtil.encode(multipart.getEpilogue()) : null;
        }
        if (preamble != null) {
            result += preamble.length() + 2;
        }

        for (Entity bodyPart : multipart.getBodyParts()) {
            result += 2 + boundary.length() + 2; // -- boudary CRLF
            result += sizeOf(bodyPart.getHeader());
            result += sizeOf(bodyPart);
            result += 2; // CRLF
        }

        result += 2 + boundary.length() + 2 + 2; // -- boudary -- CRLF
        if (epilogue != null) {
            result += epilogue.length();
        }
        return result;
    }


    // Taken from DefaultMessageWriter
    private static ContentTypeField getContentType(Multipart multipart) {
        Entity parent = multipart.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Missing parent entity in multipart");
        }

        Header header = parent.getHeader();
        if (header == null) {
            throw new IllegalArgumentException("Missing header in parent entity");
        }

        ContentTypeField contentType = (ContentTypeField) header
            .getField(FieldName.CONTENT_TYPE_LOWERCASE);
        if (contentType == null) {
            throw new IllegalArgumentException("Content-Type field not specified");
        }

        return contentType;
    }

    // Taken from DefaultMessageWriter
    private static ByteSequence getBoundary(ContentTypeField contentType) {
        String boundary = contentType.getBoundary();
        if (boundary == null) {
            throw new IllegalArgumentException("Multipart boundary not specified. Mime-Type: " + contentType.getMimeType() + ", Raw: " + contentType.toString());
        }
        return ContentUtil.encode(boundary);
    }
}
