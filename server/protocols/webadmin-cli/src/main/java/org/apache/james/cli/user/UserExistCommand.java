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

import feign.Response;
import picocli.CommandLine;

@CommandLine.Command(
    name = "exist",
    description = "Check if a user exists")
public class UserExistCommand implements Callable<Integer> {

    public static final int EXISTED_CODE = 200;
    public static final int USER_NAME_INVALID_CODE = 400;
    public static final int NOT_EXISTED_CODE = 404;

    @CommandLine.ParentCommand UserCommand userCommand;

    @CommandLine.Parameters String userName;

    @Override
    public Integer call() {
        try {
            UserClient userClient = userCommand.fullyQualifiedURL("/users");
            Response rs = userClient.doesExist(userName);
            if (rs.status() == EXISTED_CODE) {
                userCommand.out.println(userName + " exists");
                return WebAdminCli.CLI_FINISHED_SUCCEED;
            } else if (rs.status() == USER_NAME_INVALID_CODE) {
                userCommand.out.println("The user name is invalid.\n" +
                    "A user has two attributes: username and password. A valid user should satisfy these criteria:\n" +
                    "-  username and password cannot be null or empty\n" +
                    "-  username should not be longer than 255 characters\n" +
                    "-  username can not contain '/'\n" +
                    "-  username can not contain multiple domain delimiter('@')\n" +
                    "-  A username can have only a local part when virtualHosting is disabled. E.g.'myUser'\n" +
                    "-  When virtualHosting is enabled, a username should have a domain part, and the domain part " +
                    "should be concatenated after a domain delimiter('@'). E.g. 'myuser@james.org'");
                return WebAdminCli.CLI_FINISHED_FAILED;
            } else if (rs.status() == NOT_EXISTED_CODE) {
                userCommand.out.println(userName + " does not exist");
                return WebAdminCli.CLI_FINISHED_SUCCEED;
            }
            return WebAdminCli.CLI_FINISHED_FAILED;
        } catch (Exception e) {
            e.printStackTrace(userCommand.err);
            return WebAdminCli.CLI_FINISHED_FAILED;
        }
    }

}

