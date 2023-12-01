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
package org.apache.james.mailbox.tools.jpa.migrator;

import java.util.Locale;

import org.apache.james.mailbox.tools.jpa.migrator.command.JpaMigrateCommand;
import org.apache.james.mailbox.tools.jpa.migrator.exception.JpaMigrateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

/**
 * The class that will manage the migration commands for the James JPA database.
 * All SQL commands should be moved from JAVA code to a separate file.
 */
public class JpaMigrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JpaMigrator.class);
    
    /**
     * The package name where all commands reside.
     */
    private static final String JPA_MIGRATION_COMMAND_PACKAGE = JpaMigrateCommand.class.getPackage().getName();

    /**<p>Executes the database migration for the provided JIRAs numbers.
     * For example, for the https://issues.apache.org/jira/browse/IMAP-165 JIRA, simply invoke
     * with IMAP165 as parameter.
     * You can also invoke with many JIRA at once. They will be all serially executed.</p>
     * 
     * TODO Extract the SQL in JAVA classes to XML file.
     * 
     * @param jiras the JIRAs numbers
     * @throws JpaMigrateException 
     */
    public static void main(String[] jiras) throws JpaMigrateException {

        try {
            EntityManagerFactory factory = Persistence.createEntityManagerFactory("JamesMigrator");
            EntityManager em = factory.createEntityManager();

            for (String jira: jiras) {
                JpaMigrateCommand jiraJpaMigratable = (JpaMigrateCommand) Class.forName(JPA_MIGRATION_COMMAND_PACKAGE + "." + jira.toUpperCase(Locale.US) + JpaMigrateCommand.class.getSimpleName()).getDeclaredConstructor().newInstance();
                LOGGER.info("Now executing {} migration", jira);
                em.getTransaction().begin();
                jiraJpaMigratable.migrate(em);
                em.getTransaction().commit();
                LOGGER.info("{} migration is successfully achieved", jira);
            }
        } catch (Throwable t) {
            throw new JpaMigrateException(t);
        }
        
    }

}
