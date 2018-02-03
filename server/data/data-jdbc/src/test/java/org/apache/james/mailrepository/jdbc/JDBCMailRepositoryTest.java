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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.derby.jdbc.EmbeddedDriver;
import org.apache.james.filesystem.api.mock.MockFileSystem;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailrepository.MailRepositoryContract;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.mailet.Mail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class JDBCMailRepositoryTest implements MailRepositoryContract {

    private JDBCMailRepository mailRepository;

    @BeforeEach
    void init() throws Exception {
        MockFileSystem fs = new MockFileSystem();
        DataSource datasource = getDataSource();
        mailRepository = new JDBCMailRepository();

        DefaultConfigurationBuilder defaultConfiguration = new DefaultConfigurationBuilder();
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

    @Test
    @Disabled("JAMES-2304 JDBC doesn't update the message Content")
    @Override
    public void storingMessageWithSameKeyTwiceShouldUpdateMessageContent() {
    }

    /**
     * JAMES-2303 JDBC doesn't store PerRecipientSpecificHeaders
     */
    @Override
    public void checkMailEquality(Mail actual, Mail expected) {
        assertAll(
            () -> assertThat(actual.getMessage().getContent()).isEqualTo(expected.getMessage().getContent()),
            () -> assertThat(actual.getMessageSize()).isEqualTo(expected.getMessageSize()),
            () -> assertThat(actual.getName()).isEqualTo(expected.getName()),
            () -> assertThat(actual.getState()).isEqualTo(expected.getState()),
            () -> assertThat(actual.getAttribute(TEST_ATTRIBUTE)).isEqualTo(expected.getAttribute(TEST_ATTRIBUTE)),
            () -> assertThat(actual.getErrorMessage()).isEqualTo(expected.getErrorMessage()),
            () -> assertThat(actual.getRemoteHost()).isEqualTo(expected.getRemoteHost()),
            () -> assertThat(actual.getRemoteAddr()).isEqualTo(expected.getRemoteAddr()),
            () -> assertThat(actual.getLastUpdated()).isEqualTo(expected.getLastUpdated())
        );
    }
}
