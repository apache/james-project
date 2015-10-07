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

package org.apache.james.mailbox.model;

/**
 * Constants which are used within the mailbox api and implementations
 * 
 *
 */
public interface MailboxConstants {

    /**
     * The char which is used to prefix a namespace
     */
    public static final char NAMESPACE_PREFIX_CHAR = '#';

    /** The namespace used for store user inboxes */
    public static final String USER_NAMESPACE = NAMESPACE_PREFIX_CHAR + "private";

    /** The default delimiter used to seperated parent/child folders */
    public static final char DEFAULT_DELIMITER = '.';

    /** The name of the INBOX */
    public static final String INBOX = "INBOX";

}
