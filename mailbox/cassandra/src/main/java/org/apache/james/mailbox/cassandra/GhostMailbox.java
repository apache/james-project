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

package org.apache.james.mailbox.cassandra;

import org.apache.james.util.MDCStructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * See https://issues.apache.org/jira/browse/MAILBOX-322 for reading about the Ghost mailbox bug.
 *
 * This class intend to introduce a dedicated logger for the ghost mailbox bug.
 */
public class GhostMailbox {
    private static  final Logger LOGGER = LoggerFactory.getLogger(GhostMailbox.class);

    public static String MAILBOX_NAME = "mailboxName";
    public static String TYPE = "type";
    public static String MAILBOX_ID = "mailboxId";

    public static MDCStructuredLogger logger() {
        return MDCStructuredLogger.forLogger(LOGGER);
    }

}
