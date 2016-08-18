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
package org.apache.james.jmap;

import static javax.servlet.http.HttpServletResponse.SC_CREATED;

import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;

import org.apache.james.jmap.json.ObjectMapperFactory;
import org.apache.james.jmap.model.UploadResponse;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Attachment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;

public class UploadHandler {
    private final AttachmentManager attachmentManager;
    private final ObjectMapper objectMapper;

    @Inject
    private UploadHandler(AttachmentManager attachmentManager, ObjectMapperFactory objectMapperFactory) {
        this.attachmentManager = attachmentManager;
        this.objectMapper = objectMapperFactory.forWriting();
    }

    public void handle(String contentType, InputStream content, MailboxSession mailboxSession, HttpServletResponse response) throws IOException, MailboxException {
        UploadResponse storedContent = uploadContent(contentType, content, mailboxSession);
        buildResponse(response, storedContent);
    }

    private UploadResponse uploadContent(String contentType, InputStream inputStream, MailboxSession session) throws IOException, MailboxException {
        Attachment attachment = Attachment.builder()
                .bytes(ByteStreams.toByteArray(inputStream))
                .type(contentType)
                .build();
        attachmentManager.storeAttachment(attachment, session);
        return UploadResponse.builder()
                .blobId(attachment.getAttachmentId().getId())
                .type(attachment.getType())
                .size(attachment.getSize())
                .build();
    }

    private void buildResponse(HttpServletResponse resp, UploadResponse storedContent) throws IOException {
        resp.setContentType(JMAPServlet.JSON_CONTENT_TYPE_UTF8);
        resp.setStatus(SC_CREATED);
        objectMapper.writeValue(resp.getOutputStream(), storedContent);
    }
}
