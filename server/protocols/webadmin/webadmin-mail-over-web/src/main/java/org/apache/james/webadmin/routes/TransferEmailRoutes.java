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
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.server.core.MailImpl;
import org.apache.james.webadmin.Routes;

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
            //parse MimeMessage from request body
            MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(System.getProperties()), new ByteArrayInputStream(request.bodyAsBytes()));
            //create MailImpl object from MimeMessage
            MailImpl mail = MailImpl.fromMimeMessage(UUID.randomUUID().toString(), mimeMessage);
            //Send to queue api for mail processing
            queue.enQueue(mail);
            response.body("");
            response.status(201);

            return response.body();
        });
    }
}
