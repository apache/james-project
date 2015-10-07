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
package org.apache.james.domainlist.jpa;

import java.util.HashMap;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.jpa.model.JPADomain;
import org.apache.james.domainlist.lib.AbstractDomainListTest;
import org.apache.openjpa.persistence.OpenJPAEntityManagerFactory;
import org.apache.openjpa.persistence.OpenJPAPersistence;
import org.slf4j.LoggerFactory;

/**
 * Test the JPA implementation of the DomainList.
 */
public class JPADomainListTest extends AbstractDomainListTest {

    @Override
    protected DomainList createDomainList() {
        // Use a memory database.
        HashMap<String, String> properties = new HashMap<String, String>();
        properties.put("openjpa.ConnectionDriverName", org.apache.derby.jdbc.EmbeddedDriver.class.getName());
        properties.put("openjpa.ConnectionURL", "jdbc:derby:memory:JPADomainListTestDB;create=true");
        properties.put("openjpa.Log", "JDBC=WARN, SQL=WARN, Runtime=WARN");
        properties.put("openjpa.ConnectionFactoryProperties", "PrettyPrint=true, PrettyPrintLineLength=72");
        properties.put("openjpa.jdbc.SynchronizeMappings", "buildSchema(ForeignKeys=true)");
        properties.put("openjpa.MetaDataFactory", "jpa(Types=" + JPADomain.class.getName() + ")");
        /*
      The OpenJPA Entity Manager used for the tests.
     */
        OpenJPAEntityManagerFactory factory = OpenJPAPersistence.getEntityManagerFactory(properties);

        // Initialize the JPADomainList (no autodetect,...).
        JPADomainList jpaDomainList = new JPADomainList();
        jpaDomainList.setLog(LoggerFactory.getLogger("JPADomainListMockLog"));
        jpaDomainList.setDNSService(getDNSServer("localhost"));
        jpaDomainList.setAutoDetect(false);
        jpaDomainList.setAutoDetectIP(false);
        jpaDomainList.setEntityManagerFactory(factory);
        
        return jpaDomainList;

    }

}
