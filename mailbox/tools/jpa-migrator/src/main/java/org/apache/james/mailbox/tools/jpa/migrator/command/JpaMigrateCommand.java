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
 * A command that apply to James database the needed updates.
 */
public interface JpaMigrateCommand {
    
    /**
     * Executes the needed SQL commands on the database via the provided JPA entity manager.
     * A transaction on the provided entity manager must be begun by the caller.
     * It is also the reponsibility of the caller to commit the opened transaction after
     * calling the migrate method.
     * 
     * @param em the provided Entity Manager
     * @throws JpaMigrateException
     */
    void migrate(EntityManager em) throws JpaMigrateException;

}
