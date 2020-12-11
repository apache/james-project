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

package org.apache.james.httpclient;

import feign.Param;
import feign.RequestLine;
import feign.Response;

public interface MailboxClient {

    @RequestLine("PUT /{userNameToBeUsed}/mailboxes/{mailboxNameToBeCreated}")
    Response createAMailbox(@Param("userNameToBeUsed") String userName, @Param("mailboxNameToBeCreated") String mailboxName);

    @RequestLine("GET /{usernameToBeUsed}/mailboxes/{mailboxNameToBeTested}")
    Response doesExist(@Param("usernameToBeUsed") String userName, @Param("mailboxNameToBeTested") String mailboxName);

    @RequestLine("GET /{usernameToBeUsed}/mailboxes")
    Response getMailboxList(@Param("usernameToBeUsed") String userName);

    @RequestLine("DELETE /{usernameToBeUsed}/mailboxes/{mailboxNameToBeDeleted}")
    Response deleteAMailbox(@Param("usernameToBeUsed") String userName, @Param("mailboxNameToBeDeleted") String mailboxName);

    @RequestLine("DELETE /{usernameToBeUsed}/mailboxes")
    Response deleteAllMailboxes(@Param("usernameToBeUsed") String userName);

}
