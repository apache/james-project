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
import org.apache.james.webadmin.httpclient.UserClient;
import org.apache.james.webadmin.httpclient.feign.JamesFeignException;

import picocli.CommandLine;

@CommandLine.Command(
    name = "create",
    description = "Create a new User")
public class UserCreateCommand implements Callable<Integer> {

    @CommandLine.ParentCommand UserCommand userCommand;

    @CommandLine.Option(names = "--force", description = "Update a user's password")
    boolean force;

    @CommandLine.Parameters(description = "Username")
    String userName;

    @CommandLine.Option(names = {"-p", "--password"}, description = "Password", arity = "0..1", interactive = true, required = true)
    char[] password;

    @Override
    public Integer call() {
        UserClient userClient = userCommand.fullyQualifiedURL("/users");
        if (force) {
            return updateAUserPassword(userClient);
        } else {
            return createAUser(userClient);
        }
    }

    private Integer createAUser(UserClient userClient) {
        try {
            userClient.createAUser(userName, new String(password));
            userCommand.out.println("The user was created successfully");
            return WebAdminCli.CLI_FINISHED_SUCCEED;
        } catch (Exception e) {
            if (e instanceof JamesFeignException) {
                userCommand.err.println(e.getMessage());
            } else {
                e.printStackTrace(userCommand.err);
            }
            return WebAdminCli.CLI_FINISHED_FAILED;
        }
    }

    private Integer updateAUserPassword(UserClient userClient) {
        try {
            userClient.updateAUserPassword(userName, new String(password));
            userCommand.out.println("The user's password was successfully updated");
            return WebAdminCli.CLI_FINISHED_SUCCEED;
        } catch (Exception e) {
            if (e instanceof JamesFeignException) {
                userCommand.err.println(e.getMessage());
            } else {
                e.printStackTrace(userCommand.err);
            }
            return WebAdminCli.CLI_FINISHED_FAILED;
        }
    }

}
