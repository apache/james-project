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
package org.apache.james.rrt.jpa;

import java.util.HashMap;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.jpa.model.JPARecipientRewrite;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTableTest;
import org.apache.openjpa.persistence.OpenJPAEntityManagerFactory;
import org.apache.openjpa.persistence.OpenJPAPersistence;
import org.junit.Before;
import org.slf4j.LoggerFactory;

/**
 * Test the JPA Virtual User Table implementation.
 */
public class JPARecipientRewriteTableTest extends AbstractRecipientRewriteTableTest {

    /**
     * The OpenJPA Entity Manager used for the tests.
     */
    private OpenJPAEntityManagerFactory factory;

    @Before
    @Override
    public void setUp() throws Exception {

        // Use a memory database.
        /*
      The properties for the OpenJPA Entity Manager.
     */
        HashMap<String, String> properties = new HashMap<String, String>();
        properties.put("openjpa.ConnectionDriverName", "org.h2.Driver");
        properties.put("openjpa.ConnectionURL", "jdbc:h2:target/users/db");
        properties.put("openjpa.Log", "JDBC=WARN, SQL=WARN, Runtime=WARN");
        properties.put("openjpa.ConnectionFactoryProperties", "PrettyPrint=true, PrettyPrintLineLength=72");
        properties.put("openjpa.jdbc.SynchronizeMappings", "buildSchema(ForeignKeys=true)");
        properties.put("openjpa.MetaDataFactory", "jpa(Types=" + JPARecipientRewrite.class.getName() + ")");

        factory = OpenJPAPersistence.getEntityManagerFactory(properties);

        super.setUp();
    }

    @Override
    protected AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception {
        JPARecipientRewriteTable localVirtualUserTable = new JPARecipientRewriteTable();
        localVirtualUserTable.setLog(LoggerFactory.getLogger("MockLog"));
        localVirtualUserTable.setEntityManagerFactory(factory);
        DefaultConfigurationBuilder defaultConfiguration = new DefaultConfigurationBuilder();
        localVirtualUserTable.configure(defaultConfiguration);
        return localVirtualUserTable;
    }

    @Override
    protected boolean addMapping(String user, String domain, String mapping, int type) throws
            RecipientRewriteTableException {
        try {
            if (type == ERROR_TYPE) {
                virtualUserTable.addErrorMapping(user, domain, mapping);
            } else if (type == REGEX_TYPE) {
                virtualUserTable.addRegexMapping(user, domain, mapping);
            } else if (type == ADDRESS_TYPE) {
                virtualUserTable.addAddressMapping(user, domain, mapping);
            } else if (type == ALIASDOMAIN_TYPE) {
                virtualUserTable.addAliasDomainMapping(domain, mapping);
            } else {
                return false;
            }
            return true;
        } catch (RecipientRewriteTableException e) {
            return false;
        }
    }

    @Override
    protected boolean removeMapping(String user, String domain, String mapping, int type) throws
            RecipientRewriteTableException {
        try {
            if (type == ERROR_TYPE) {
                virtualUserTable.removeErrorMapping(user, domain, mapping);
            } else if (type == REGEX_TYPE) {
                virtualUserTable.removeRegexMapping(user, domain, mapping);
            } else if (type == ADDRESS_TYPE) {
                virtualUserTable.removeAddressMapping(user, domain, mapping);
            } else if (type == ALIASDOMAIN_TYPE) {
                virtualUserTable.removeAliasDomainMapping(domain, mapping);
            } else {
                return false;
            }
            return true;
        } catch (RecipientRewriteTableException e) {
            return false;
        }
    }
}
