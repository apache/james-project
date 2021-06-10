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
    name = "exist",
    description = "Check if a mailbox exists")
public class MailboxExistCommand implements Callable<Integer> {

    public static final int EXISTED_CODE = 204;
    public static final int INVALID_MAILBOX_NAME_CODE = 400;
    public static final int NOT_EXISTED_CODE = 404;

    @CommandLine.ParentCommand MailboxCommand mailboxCommand;

    @CommandLine.Parameters(description = "Username to be checked")
    String userName;

    @CommandLine.Parameters(description = "Mailbox's name to be tested existence")
    String mailboxName;

    @Override
    public Integer call() {
        try {
            MailboxClient mailboxClient = mailboxCommand.fullyQualifiedURL("/users");
            Response rs = mailboxClient.doesExist(userName, mailboxName);
            if (rs.status() == EXISTED_CODE) {
                mailboxCommand.out.println("The mailbox exists.");
                return WebAdminCli.CLI_FINISHED_SUCCEED;
            } else if (rs.status() == INVALID_MAILBOX_NAME_CODE) {
                mailboxCommand.err.println(rs.body());
                return WebAdminCli.CLI_FINISHED_FAILED;
            } else if (rs.status() == NOT_EXISTED_CODE) {
                mailboxCommand.out.println("Either the user name or the mailbox does not exist.");
                return WebAdminCli.CLI_FINISHED_SUCCEED;
            }
            return WebAdminCli.CLI_FINISHED_FAILED;
        } catch (Exception e) {
            e.printStackTrace(mailboxCommand.err);
            return WebAdminCli.CLI_FINISHED_FAILED;
        }
    }

}


