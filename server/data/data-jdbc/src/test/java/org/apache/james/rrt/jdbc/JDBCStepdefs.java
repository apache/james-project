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
package org.apache.james.rrt.jdbc;

import java.util.UUID;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.derby.jdbc.EmbeddedDriver;
import org.apache.james.filesystem.api.mock.MockFileSystem;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.RecipientRewriteTableFixture;
import org.apache.james.rrt.lib.RewriteTablesStepdefs;

import cucumber.api.java.Before;

public class JDBCStepdefs {

    private final RewriteTablesStepdefs mainStepdefs;

    public JDBCStepdefs(RewriteTablesStepdefs mainStepdefs) {
        this.mainStepdefs = mainStepdefs;
    }

    @Before
    public void setup() throws Throwable {
        mainStepdefs.rewriteTable = getRecipientRewriteTable(); 
    }

    @SuppressWarnings("deprecation")
    protected AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception {
        JDBCRecipientRewriteTable localVirtualUserTable = new JDBCRecipientRewriteTable();
        localVirtualUserTable.setDataSource(getDataSource());
        localVirtualUserTable.setFileSystem(new MockFileSystem());
        DefaultConfigurationBuilder defaultConfiguration = new DefaultConfigurationBuilder();
        defaultConfiguration.addProperty("[@destinationURL]", "db://maildb/RecipientRewriteTable");
        defaultConfiguration.addProperty("sqlFile", "file://conf/sqlResources.xml");
        localVirtualUserTable.configure(defaultConfiguration);
        localVirtualUserTable.setDomainList(RecipientRewriteTableFixture.domainListForCucumberTests());
        localVirtualUserTable.init();
        return localVirtualUserTable;
    }
    
    private BasicDataSource getDataSource() {
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName(EmbeddedDriver.class.getName());
        ds.setUrl("jdbc:derby:target/" + UUID.randomUUID() + ";create=true");
        ds.setUsername("james");
        ds.setPassword("james");
        return ds;
    }
}
