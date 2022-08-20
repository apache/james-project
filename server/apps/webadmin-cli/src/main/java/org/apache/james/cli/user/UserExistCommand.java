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
    name = "exist",
    description = "Check if a user exists")
public class UserExistCommand implements Callable<Integer> {

    @CommandLine.ParentCommand UserCommand userCommand;

    @CommandLine.Parameters String userName;

    @Override
    public Integer call() {
        try {
            UserClient userClient = userCommand.fullyQualifiedURL("/users");
            if (userClient.doesExist(userName)) {
                userCommand.out.println(userName + " exists");
            } else {
                userCommand.out.println(userName + " does not exist");
            }
            return WebAdminCli.CLI_FINISHED_SUCCEED;
        } catch (Exception e) {
            if (e instanceof JamesFeignException) {
                userCommand.out.println(e.getMessage());
            } else {
                e.printStackTrace(userCommand.err);
            }
            return WebAdminCli.CLI_FINISHED_FAILED;
        }
    }

}

