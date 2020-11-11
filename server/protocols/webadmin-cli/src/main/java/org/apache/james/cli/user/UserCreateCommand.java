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

package org.apache.james.cli.user;

import java.util.concurrent.Callable;

import org.apache.james.cli.WebAdminCli;
import org.apache.james.httpclient.UserClient;
import org.apache.james.httpclient.model.UserPassword;

import feign.Feign;
import feign.Response;
import feign.jackson.JacksonEncoder;
import picocli.CommandLine;

@CommandLine.Command(
    name = "create",
    description = "Create a new User")
public class UserCreateCommand implements Callable<Integer> {

    public static final int CREATED_CODE = 204;
    public static final int BAD_REQUEST_CODE = 400;

    @CommandLine.ParentCommand UserCommand userCommand;

    @CommandLine.Parameters(description = "Username")
    String userName;

    @CommandLine.Option(names = {"-p", "--password"}, description = "Password", arity = "0..1", interactive = true, required = true)
    char[] password;

    @Override
    public Integer call() {
        try {
            UserClient userClient = Feign.builder()
                .encoder(new JacksonEncoder())
                .target(UserClient.class, userCommand.webAdminCli.jamesUrl + "/users");
            Response rs = userClient.createAUser(userName, new UserPassword(new String(password)));
            if (rs.status() == CREATED_CODE) {
                userCommand.out.println("The user was created successfully");
                return WebAdminCli.CLI_FINISHED_SUCCEED;
            } else if (rs.status() == BAD_REQUEST_CODE) {
                userCommand.out.println("The user name or the payload is invalid");
                return WebAdminCli.CLI_FINISHED_FAILED;
            }
            return WebAdminCli.CLI_FINISHED_FAILED;
        } catch (Exception e) {
            e.printStackTrace(userCommand.err);
            return WebAdminCli.CLI_FINISHED_FAILED;
        }
    }
}
