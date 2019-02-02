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
package org.apache.james.mailbox.tools.jpa.migrator.command;

import javax.persistence.EntityManager;

import org.apache.james.mailbox.tools.jpa.migrator.exception.JpaMigrateException;

/**
 * JIRA IMAP-184 is "Remove size of MailboxMembership".
 * 
 * Simply drop the MAILBOXMEMBERSHIP.SIZE column.
 * 
 * @link https://issues.apache.org/jira/browse/IMAP-184
 * 
 */
public class IMAP184JpaMigrateCommand implements JpaMigrateCommand {

    @Override
    public void migrate(EntityManager em) throws JpaMigrateException {
        JpaMigrateQuery.executeUpdate(em, "ALTER TABLE MAILBOXMEMBERSHIP DROP COLUMN SIZE");
    }

}
