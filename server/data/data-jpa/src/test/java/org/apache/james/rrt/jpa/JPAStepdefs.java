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
import java.util.UUID;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.rrt.jpa.model.JPARecipientRewrite;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.RewriteTablesStepdefs;
import org.apache.openjpa.persistence.OpenJPAEntityManagerFactory;
import org.apache.openjpa.persistence.OpenJPAPersistence;
import org.slf4j.LoggerFactory;

import cucumber.api.java.Before;

public class JPAStepdefs {

    private final RewriteTablesStepdefs mainStepdefs;

    public JPAStepdefs(RewriteTablesStepdefs mainStepdefs) {
        this.mainStepdefs = mainStepdefs;
    }

    @Before
    public void setup() throws Throwable {
        mainStepdefs.rewriteTable = getRecipientRewriteTable(); 
    }

    private OpenJPAEntityManagerFactory managerFactory() throws Exception {

        // Use a memory database.
        /*
      The properties for the OpenJPA Entity Manager.
     */
        HashMap<String, String> properties = new HashMap<String, String>();
        properties.put("openjpa.ConnectionDriverName", "org.h2.Driver");
        properties.put("openjpa.ConnectionURL", "jdbc:h2:target/users/" + UUID.randomUUID());
        properties.put("openjpa.Log", "JDBC=WARN, SQL=WARN, Runtime=WARN");
        properties.put("openjpa.ConnectionFactoryProperties", "PrettyPrint=true, PrettyPrintLineLength=72");
        properties.put("openjpa.jdbc.SynchronizeMappings", "buildSchema(ForeignKeys=true)");
        properties.put("openjpa.MetaDataFactory", "jpa(Types=" + JPARecipientRewrite.class.getName() + ")");

        return OpenJPAPersistence.getEntityManagerFactory(properties);
    }

    private AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception {
        JPARecipientRewriteTable localVirtualUserTable = new JPARecipientRewriteTable();
        localVirtualUserTable.setLog(LoggerFactory.getLogger("MockLog"));
        localVirtualUserTable.setEntityManagerFactory(managerFactory());
        DefaultConfigurationBuilder defaultConfiguration = new DefaultConfigurationBuilder();
        localVirtualUserTable.configure(defaultConfiguration);
        return localVirtualUserTable;
    }
}
