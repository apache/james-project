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

package org.apache.james.cli.mailbox;

import java.util.concurrent.Callable;

import org.apache.james.cli.WebAdminCli;
import org.apache.james.httpclient.MailboxClient;

import feign.Response;
import picocli.CommandLine;

@CommandLine.Command(
        name = "deleteAll",
        description = "Delete all mailboxes of a user")
public class MailboxDeleteAllCommand implements Callable<Integer> {

    public static final int DELETED_CODE = 204;
    public static final int NOT_FOUND_CODE = 404;

    @CommandLine.ParentCommand MailboxCommand mailboxCommand;

    @CommandLine.Parameters(description = "Username")
    String userName;

    @Override
    public Integer call() {
        try {
            MailboxClient mailboxClient = mailboxCommand.fullyQualifiedURL("/users");
            Response rs = mailboxClient.deleteAllMailboxes(userName);
            if (rs.status() == DELETED_CODE) {
                mailboxCommand.out.println("The user do not have mailboxes anymore.");
                return WebAdminCli.CLI_FINISHED_SUCCEED;
            } else if (rs.status() == NOT_FOUND_CODE) {
                mailboxCommand.err.println("The user name does not exist.");
                return WebAdminCli.CLI_FINISHED_FAILED;
            }
            return WebAdminCli.CLI_FINISHED_FAILED;
        } catch (Exception e) {
            e.printStackTrace(mailboxCommand.err);
            return WebAdminCli.CLI_FINISHED_FAILED;
        }
    }

}

