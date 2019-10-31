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

package org.apache.james.mpt.imapmailbox;

import org.apache.james.core.Username;

/**
 * Some constants to use when running Imap tests.
 */
public interface ImapTestConstants {
    int PORT = 143;

    String HOST = "localhost";

    Username USER = Username.of("imapuser");

    String PASSWORD = "password";

    String FROM_ADDRESS = "sender@localhost";

    String TO_ADDRESS = USER + "@" + HOST;

    int TIMEOUT = 10000;
}
