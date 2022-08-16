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
import org.apache.james.webadmin.httpclient.feign.UserFeignClient;
import org.apache.james.webadmin.httpclient.model.UserName;
import org.apache.james.webadmin.httpclient.model.UserPassword;

import feign.Response;

public class UserClient {

    private final UserFeignClient feignClient;

    public UserClient(UserFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    public List<UserName> getUserNameList() {
        return feignClient.getUserNameList();
    }

    public void createAUser(String username, String password) {
        try (Response response = feignClient.createAUser(username, new UserPassword(password))) {
            switch (response.status()) {
                case 204:
                    return;
                case 400:
                    throw new JamesFeignException("The user name or the payload is invalid");
                case 409:
                    throw new JamesFeignException("The user already exists");
                default:
                    throw new JamesFeignException("Create user failed. " + FeignHelper.extractBody(response));
            }
        }
    }

    public void updateAUserPassword(String username, String password) {
        try (Response response = feignClient.updateAUserPassword(username, new UserPassword(password))) {
            switch (response.status()) {
                case 204:
                    return;
                case 400:
                    throw new JamesFeignException("The user name or the payload is invalid");
                default:
                    throw new JamesFeignException("Update user failed. " + FeignHelper.extractBody(response));
            }
        }
    }

    public void deleteAUser(String username) {
        try (Response response = feignClient.deleteAUser(username)) {
            FeignHelper.checkResponse(response.status() == 204, "Delete a user failed. " + FeignHelper.extractBody(response));
        }
    }

    public boolean doesExist(String username) {
        try (Response response = feignClient.doesExist(username)) {
            switch (response.status()) {
                case 200:
                    return true;
                case 404:
                    return false;
                case 400:
                    throw new JamesFeignException("The user name is invalid.\n" +
                        "A user has two attributes: username and password. A valid user should satisfy these criteria:\n" +
                        "-  username and password cannot be null or empty\n" +
                        "-  username should not be longer than 255 characters\n" +
                        "-  username can not contain '/'\n" +
                        "-  username can not contain multiple domain delimiter('@')\n" +
                        "-  A username can have only a local part when virtualHosting is disabled. E.g.'myUser'\n" +
                        "-  When virtualHosting is enabled, a username should have a domain part, and the domain part " +
                        "should be concatenated after a domain delimiter('@'). E.g. 'myuser@james.org'");
                default:
                    throw new JamesFeignException("Check exist user failed. " + FeignHelper.extractBody(response));
            }
        }
    }

}