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

package org.apache.james.webadmin.httpclient;

import java.util.List;

import org.apache.james.webadmin.httpclient.feign.JamesFeignException;
import org.apache.james.webadmin.httpclient.feign.MailboxFeignClient;
import org.apache.james.webadmin.httpclient.model.MailboxName;

import feign.Response;

public class MailboxClient {

    private final MailboxFeignClient feignClient;

    public MailboxClient(MailboxFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    public void createAMailbox(String username, String mailboxName) {
        try (Response response = feignClient.createAMailbox(username, mailboxName)) {
            FeignHelper.checkResponse(response.status() == 204, "Create a mailbox failed. " + FeignHelper.extractBody(response));
        }
    }

    public boolean doesExist(String username, String mailboxName) {
        try (Response response = feignClient.doesExist(username, mailboxName)) {
            switch (response.status()) {
                case 204:
                    return true;
                case 404:
                    return false;
                default:
                    throw new JamesFeignException("Check a mailbox exist failed. " + FeignHelper.extractBody(response));
            }
        }
    }

    public List<MailboxName> getMailboxList(String username) {
        return feignClient.getMailboxList(username);
    }

    public void deleteAMailbox(String username, String mailboxName) {
        try (Response response = feignClient.deleteAMailbox(username, mailboxName)) {
            FeignHelper.checkResponse(response.status() == 204, "Delete a mailbox failed. " + FeignHelper.extractBody(response));
        }
    }

    public void deleteAllMailboxes(String username) {
        try (Response response = feignClient.deleteAllMailboxes(username)) {
            switch (response.status()) {
                case 204:
                    return;
                case 404:
                    throw new JamesFeignException("The user name does not exist.");
                default:
                    throw new JamesFeignException("Delete all mailboxes failed. " + FeignHelper.extractBody(response));
            }
        }
    }
}
