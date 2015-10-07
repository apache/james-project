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

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.james.mailbox.jpa.migrator.exception.JpaMigrateException;

/**
 * <p>
 * JIRA IMAP-168 is "mailboxes can't be identified 100% unambiguously using virtual hosting".
 * 
 * MAILBOX.NAME contains data such as 
 * "#mail.eric@localhost.net"
 * "#mail.eric@localhost.net.INBOX"
 * "#mail.eric@localhost.net.INBOX.test"
 * "#mail.eric@localhost.net.Trash"
 * 
 * It needs to be splitted into MAILBOX.NAMESPACE | MAILBOX.USER0 | MAILBOX.NAME with 
 * "#mail" | "eric@localhost.net" | "" ==> was created before, but is not used anymore
 * "#mail" | "eric@localhost.net" | "INBOX"
 * "#mail" | "eric@localhost.net" | "INBOX.test"
 * "#mail" | "eric@localhost.net" | "Trash"
 *</p>
 *
 * @link https://issues.apache.org/jira/browse/IMAP-168
 * 
 */
public class IMAP168JpaMigrateCommand implements JpaMigrateCommand {

    /**
     * @see org.apache.james.mailbox.jpa.migrator.command#migrate(javax.persistence.EntityManager)
     */
    public void migrate(EntityManager em) throws JpaMigrateException {

        JpaMigrateQuery.executeUpdate(em, "ALTER TABLE MAILBOX ADD COLUMN NAMESPACE VARCHAR(255)");
        JpaMigrateQuery.executeUpdate(em, "ALTER TABLE MAILBOX ADD COLUMN USER0 VARCHAR(255)");

        Query query = em.createNativeQuery("SELECT NAME FROM MAILBOX");
        
        @SuppressWarnings("unchecked")
        List<String> nameList = query.getResultList();
        System.out.println("getResultList returned a result=" + nameList.size());
        for (String name: nameList) {
            MailboxPath mailboxPath = new MailboxPath(name);
            System.out.println(mailboxPath);
            Query update = em.createNativeQuery("UPDATE MAILBOX SET NAMESPACE = ?, USER0 = ?, NAME = ? WHERE NAME = ?");
            update.setParameter(1, mailboxPath.namespace);
            update.setParameter(2, mailboxPath.userName);
            update.setParameter(3, mailboxPath.mailboxName);
            update.setParameter(4, name);
            int resultUpdate = update.executeUpdate();
            System.out.println("ExecuteUpdate returned a result=" + resultUpdate);
        }

    }

    /**
     *
     */
    private class MailboxPath {

        protected String namespace;
        protected String userName;
        protected String mailboxName;

        /**
         * @param name
         */
        public MailboxPath (String name) {
            
            if (! name.startsWith("#mail")) {
                throw new IllegalArgumentException("The name must begin with #private");
            }
            
            namespace = "#mail";

            name = name.substring(6);
            
            int atIndex = name.indexOf("@");
            int firstDotIndex = name.indexOf(".", atIndex);
            int secondDotIndex = name.indexOf(".", firstDotIndex + 1);
            
            if (secondDotIndex > 0) {
                userName = name.substring(0, secondDotIndex);
                mailboxName = name.substring(userName.length() + 1);
            }
            else {
                // We don't have a mailbox name...
                userName = name.substring(0);
                mailboxName = "";
            }
            
        }

        @Override
        public String toString() {
            return "MailboxPath [namespace=" + namespace + 
                ", userName=" + userName + ", mailboxName=" + mailboxName + "]";
        }

    }

}
