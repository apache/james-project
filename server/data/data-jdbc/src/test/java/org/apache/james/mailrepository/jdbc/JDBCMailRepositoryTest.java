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

package org.apache.james.mailrepository.jdbc;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.derby.jdbc.EmbeddedDriver;
import org.apache.james.filesystem.api.mock.MockFileSystem;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailrepository.MailRepositoryContract;
import org.apache.james.mailrepository.api.MailRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

public class JDBCMailRepositoryTest implements MailRepositoryContract {

    private JDBCMailRepository mailRepository;

    @BeforeEach
    void init() throws Exception {
        MockFileSystem fs = new MockFileSystem();
        DataSource datasource = getDataSource();
        mailRepository = new JDBCMailRepository();

        BaseHierarchicalConfiguration defaultConfiguration = new BaseHierarchicalConfiguration();
        defaultConfiguration.addProperty("[@destinationURL]", "db://maildb/mr/testrepo");
        defaultConfiguration.addProperty("sqlFile", "file://conf/sqlResources.xml");
        defaultConfiguration.addProperty("[@type]", "MAIL");
        mailRepository.setFileSystem(fs);
        mailRepository.setDatasource(datasource);
        mailRepository.configure(defaultConfiguration);
        mailRepository.init();
    }

    @AfterEach
    void tearDown() throws SQLException {
        mailRepository.getConnection().prepareStatement("DELETE from " + mailRepository.tableName).execute();
        LifecycleUtil.dispose(mailRepository);
    }

    @Override
    public MailRepository retrieveRepository() {
        return mailRepository;
    }

    private BasicDataSource getDataSource() {
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName(EmbeddedDriver.class.getName());
        ds.setUrl("jdbc:derby:target/testdb;create=true");
        ds.setUsername("james");
        ds.setPassword("james");
        return ds;
    }

    @Override
    @Disabled("JAMES-2546 This mail repository does not support null sender")
    public void storeRegularMailShouldNotFailWhenNullSender() {

    }

    @Override
    @Disabled("JAMES-3431 No support for Attribute collection Java serialization yet")
    public void shouldPreserveDsnParameters() {

    }
}
