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

import org.apache.james.mailbox.tools.jpa.migrator.exception.JpaMigrateException;

import jakarta.persistence.EntityManager;

/**
 * JIRA IMAP-165 is "Add index annotation on frequently used columns".
 * 
 * Add 3 indexes for the DELETED, SEEN and RECENT columns of MEMBERSHIP table.
 * 
 * @link https://issues.apache.org/jira/browse/IMAP-165
 * 
 */
public class IMAP165JpaMigrateCommand implements JpaMigrateCommand {

    @Override
    public void migrate(EntityManager em) throws JpaMigrateException {
        JpaMigrateQuery.executeUpdate(em, "CREATE INDEX I_MMBRSHP_SEEN ON MEMBERSHIP (SEEN)");
        JpaMigrateQuery.executeUpdate(em, "CREATE INDEX I_MMBRSHP_RECENT ON MEMBERSHIP (RECENT)");
        JpaMigrateQuery.executeUpdate(em, "CREATE INDEX I_MMBRSHP_DELETED ON MEMBERSHIP (DELETED)");
    }

}
