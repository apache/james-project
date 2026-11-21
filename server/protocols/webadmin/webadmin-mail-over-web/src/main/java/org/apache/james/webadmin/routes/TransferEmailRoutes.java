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

package org.apache.james.webadmin.routes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.server.core.MailImpl;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.eclipse.jetty.http.HttpStatus;

import spark.HaltException;
import spark.Request;
import spark.Service;

public class TransferEmailRoutes implements Routes {

    public static final String BASE_URL = "/mail-transfer-service";

    private MailQueue queue;

    @Override
    public String getBasePath() {
        return BASE_URL;
    }

    @Inject
    public TransferEmailRoutes(MailQueueFactory<?> queueFactory) {
        queue = queueFactory.createQueue(MailQueueFactory.SPOOL);
    }

    @PreDestroy
    void tearDown() {
        try {
            queue.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void define(Service service) {
        defineReceiveMailFromWebService(service);
    }

    public void defineReceiveMailFromWebService(Service service) {
        service.post(BASE_URL, (request, response) -> {
            MailImpl mail = parseMail(request);
            //Send to queue api for mail processing
            queue.enQueue(mail);
            response.body("");
            response.status(201);

            return response.body();
        });
    }

    private MailImpl parseMail(Request request) {
        try {
            //parse MimeMessage from request body
            MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(System.getProperties()), new ByteArrayInputStream(request.bodyAsBytes()));
            Address[] from = mimeMessage.getFrom();
            if (from == null || from.length != 1) {
                throw badRequest("The message must have exactly one 'From' address", null);
            }
            Address[] recipients = mimeMessage.getAllRecipients();
            if (recipients == null || recipients.length == 0) {
                throw badRequest("The message must have at least one recipient ('To', 'Cc' or 'Bcc')", null);
            }
            //create MailImpl object from MimeMessage
            return MailImpl.fromMimeMessage(UUID.randomUUID().toString(), mimeMessage);
        } catch (MessagingException | IllegalArgumentException e) {
            throw badRequest("Invalid message: " + e.getMessage(), e);
        }
    }

    private HaltException badRequest(String message, Exception cause) {
        ErrorResponder errorResponder = ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
            .message(message);
        if (cause != null) {
            errorResponder.cause(cause);
        }
        return errorResponder.haltError();
    }
}
