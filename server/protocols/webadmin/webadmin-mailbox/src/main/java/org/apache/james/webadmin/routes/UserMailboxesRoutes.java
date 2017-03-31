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

import javax.inject.Inject;

import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.service.UserMailboxesService;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.MailboxHaveChildrenException;
import org.apache.james.webadmin.validation.MailboxName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Service;

public class UserMailboxesRoutes implements Routes {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserMailboxesRoutes.class);

    public static final String MAILBOX_NAME = ":mailboxName";
    public static final String MAILBOXES = "mailboxes";
    private static final String USER_NAME = ":userName";
    public static final String USERS_BASE = "/users";
    public static final String USER_MAILBOXES_BASE = USERS_BASE + Constants.SEPARATOR + USER_NAME + Constants.SEPARATOR + MAILBOXES;
    public static final String SPECIFIC_MAILBOX = USER_MAILBOXES_BASE + Constants.SEPARATOR + MAILBOX_NAME;

    private final UserMailboxesService userMailboxesService;
    private final JsonTransformer jsonTransformer;

    @Inject
    public UserMailboxesRoutes(UserMailboxesService userMailboxesService, JsonTransformer jsonTransformer) {
        this.userMailboxesService = userMailboxesService;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public void define(Service service) {

        service.put(SPECIFIC_MAILBOX, (request, response) -> {
            try {
                userMailboxesService.createMailbox(request.params(USER_NAME), new MailboxName(request.params(MAILBOX_NAME)));
                response.status(204);
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid put on user mailbox", e);
                response.status(404);
            } catch (IllegalArgumentException e) {
                LOGGER.info("Attempt to create an invalid mailbox");
                response.status(400);
            }
            return Constants.EMPTY_BODY;
        });

        service.delete(SPECIFIC_MAILBOX, (request, response) -> {
            try {
                userMailboxesService.deleteMailbox(request.params(USER_NAME), new MailboxName(request.params(MAILBOX_NAME)));
                response.status(204);
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid delete on user mailbox", e);
                response.status(404);
            } catch (MailboxHaveChildrenException e) {
                LOGGER.info("Attempt to delete a mailbox with children");
                response.status(409);
            } catch (IllegalArgumentException e) {
                LOGGER.info("Attempt to create an invalid mailbox");
                response.status(400);
            }
            return Constants.EMPTY_BODY;
        });

        service.delete(USER_MAILBOXES_BASE, (request, response) -> {
            try {
                userMailboxesService.deleteMailboxes(request.params(USER_NAME));
                response.status(204);
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid delete on user mailboxes", e);
                response.status(404);
            }
            return Constants.EMPTY_BODY;
        });

        service.get(SPECIFIC_MAILBOX, (request, response) -> {
            try {
                if (userMailboxesService.testMailboxExists(request.params(USER_NAME), new MailboxName(request.params(MAILBOX_NAME)))) {
                    response.status(204);
                } else {
                    response.status(404);
                }
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid get on user mailbox", e);
                response.status(404);
            } catch (IllegalArgumentException e) {
                LOGGER.info("Attempt to create an invalid mailbox");
                response.status(400);
            }
            return Constants.EMPTY_BODY;
        });

        service.get(USER_MAILBOXES_BASE, (request, response) -> {
            response.status(200);
            try {
                return userMailboxesService.listMailboxes(request.params(USER_NAME));
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid get on user mailboxes", e);
                response.status(404);
                return Constants.EMPTY_BODY;
            }
        }, jsonTransformer);

    }
}
