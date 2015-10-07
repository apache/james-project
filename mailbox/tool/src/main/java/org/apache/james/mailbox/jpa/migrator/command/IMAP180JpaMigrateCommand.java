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
package org.apache.james.mailbox.jpa.migrator.command;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.james.mailbox.jpa.migrator.exception.JpaMigrateException;
import org.apache.openjpa.kernel.DelegatingResultList;
import org.apache.openjpa.lib.rop.ResultList;

/**
 * JIRA IMAP-180 is "Add @ElementJoinColumn for Property and Header tables".
 * 
 * 1. Add the needed columns on HEADER and PROPERTY
 * ALTER TABLE HEADER ADD COLUMN MESSAGE_ID BIGINT
 * ALTER TABLE PROPERTY ADD COLUMN MESSAGE_ID BIGINT
 * 
 * 2. Link the HEADER/PROPERTY tables with the MESSAGE table
 * SELECT * FROM MESSAGE_HEADER / MESSAGE_HEADER
 * 
 * 3. Add the needed FK and indexes on HEADER and PROPERTY
 * CREATE INDEX SQL100727182411700 ON HEADER(MESSAGE_ID)
 * ALTER TABLE HEADER ADD CONSTRAINT SQL100727182411700 FOREIGN KEY (MESSAGE_ID) REFERENCES MESSAGE(ID)
 * CREATE INDEX SQL100727182411780 ON PROPERTY(MESSAGE_ID)
 * ALTER TABLE PROPERTY ADD CONSTRAINT SQL100727182411780 FOREIGN KEY (MESSAGE_ID) REFERENCES MESSAGE(ID)
 * 
 * 4. Drop the MESSAGE_HEADER and MESSAGE_PROPERY tables
 * DROP TABLE MESSAGE_HEADER
 * DROP TABLE MESSAGE_PROPERTY
 * 
 * @link https://issues.apache.org/jira/browse/IMAP-180
 * 
 */
public class IMAP180JpaMigrateCommand implements JpaMigrateCommand {

    /**
     * @see org.apache.james.mailbox.jpa.migrator.command#migrate(javax.persistence.EntityManager)
     */
    public void migrate(EntityManager em) throws JpaMigrateException {
        em.getTransaction().commit();
        migrateHeaders(em);
        // Commit after header migration.
        migrateProperties(em);
        em.getTransaction().begin();
    }
        
    /**
     * Migrate the headers.
     */
    @SuppressWarnings("rawtypes")
    private static void migrateHeaders(EntityManager em) {
        
        em.getTransaction().begin();
        Query headerCountQuery = em.createNativeQuery("SELECT COUNT(MESSAGE_ID) FROM MESSAGE_HEADER", Integer.class);
        Integer headerCount = (Integer) headerCountQuery.getResultList().get(0);
        System.out.println("Number of headers=" + headerCount);
        
        JpaMigrateQuery.executeUpdate(em, "ALTER TABLE HEADER ADD COLUMN MESSAGE_ID BIGINT");
        
        Query headerQuery = em.createNativeQuery("SELECT MESSAGE_ID, HEADERS_ID FROM MESSAGE_HEADER");
        em.getTransaction().commit();
        
        DelegatingResultList headerNameList = (DelegatingResultList) headerQuery.getResultList();
        ResultList rl = headerNameList.getDelegate();
        for (int i=0; i < rl.size(); i++) {
            Object[] results = (Object[]) rl.get(i);
            Long messageId = (Long) results[0];
            Long headerId = (Long) results[1];
            em.getTransaction().begin();
            Query update = em.createNativeQuery("UPDATE HEADER SET MESSAGE_ID = ? WHERE ID = ?");
            update.setParameter(1, messageId);
            update.setParameter(2, headerId);
            int result = update.executeUpdate();
            System.out.printf("ExecuteUpdate returned a result=" + result + " for header %d of %d\n", i+1, headerCount);
            em.getTransaction().commit();
        }

        em.getTransaction().begin();
        System.out.println("Creating index.");
        JpaMigrateQuery.executeUpdate(em, "CREATE INDEX SQL100727182411700 ON HEADER(MESSAGE_ID)");
        em.getTransaction().commit();
        
        em.getTransaction().begin();
        System.out.println("Creating foreign key.");
        JpaMigrateQuery.executeUpdate(em, "ALTER TABLE HEADER ADD CONSTRAINT SQL100727182411700 FOREIGN KEY (MESSAGE_ID) REFERENCES MESSAGE(ID)");
        em.getTransaction().commit();

        em.getTransaction().begin();
        System.out.println("Dropping table.");
        JpaMigrateQuery.executeUpdate(em, "DROP TABLE MESSAGE_HEADER");
        em.getTransaction().commit();

    }
        
    /**
     * Migrate the properties.
     */
    @SuppressWarnings("rawtypes")
    private static void migrateProperties(EntityManager em) {
        
        em.getTransaction().begin();
        Query propertyCountQuery = em.createNativeQuery("SELECT COUNT(MESSAGE_ID) FROM MESSAGE_PROPERTY", Integer.class);
        Integer propertyCount = (Integer) propertyCountQuery.getResultList().get(0);
        System.out.println("Number of headers=" + propertyCount);

        JpaMigrateQuery.executeUpdate(em, "ALTER TABLE PROPERTY ADD COLUMN MESSAGE_ID BIGINT");

        Query propertyQuery = em.createNativeQuery("SELECT MESSAGE_ID, PROPERTIES_ID FROM MESSAGE_PROPERTY");
        em.getTransaction().commit();

        DelegatingResultList propertyNameList = (DelegatingResultList) propertyQuery.getResultList();
        ResultList rl = propertyNameList.getDelegate();
        for (int i=0; i < rl.size(); i++) {
            Object[] results = (Object[]) rl.get(i);
            Long messageId = (Long) results[0];
            Long propertyId = (Long) results[1];
            em.getTransaction().begin();
            Query update = em.createNativeQuery("UPDATE PROPERTY SET MESSAGE_ID = ? WHERE ID = ?");
            update.setParameter(1, messageId);
            update.setParameter(2, propertyId);
            int result = update.executeUpdate();
            System.out.printf("ExecuteUpdate returned a result=" + result + " for property %d of %d\n", i+1, propertyCount);     
            em.getTransaction().commit();
        }
        
        em.getTransaction().begin();
        System.out.println("Creating index.");
        JpaMigrateQuery.executeUpdate(em, "CREATE INDEX SQL100727182411780 ON PROPERTY(MESSAGE_ID)");
        em.getTransaction().commit();
        
        em.getTransaction().begin();
        System.out.println("Creating foreign key.");
        JpaMigrateQuery.executeUpdate(em, "ALTER TABLE PROPERTY ADD CONSTRAINT SQL100727182411780 FOREIGN KEY (MESSAGE_ID) REFERENCES MESSAGE(ID)");
        em.getTransaction().commit();

        em.getTransaction().begin();
        System.out.println("Dropping table.");
        JpaMigrateQuery.executeUpdate(em, "DROP TABLE MESSAGE_PROPERTY");
        em.getTransaction().commit();
        
    }

}
