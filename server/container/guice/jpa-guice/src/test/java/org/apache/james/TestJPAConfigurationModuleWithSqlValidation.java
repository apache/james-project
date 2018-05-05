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

package org.apache.james;

import java.io.FileNotFoundException;

import javax.inject.Singleton;

import org.apache.commons.configuration.ConfigurationException;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class TestJPAConfigurationModuleWithSqlValidation extends AbstractModule {

    private static final String JDBC_EMBEDDED_URL = "jdbc:derby:memory:mailboxintegration;create=true";
    private static final String JDBC_EMBEDDED_DRIVER = org.apache.derby.jdbc.EmbeddedDriver.class.getName();
    private static final String VALIDATION_SQL_QUERY = "VALUES 1";

    @Override
    protected void configure() {
    }

    @Provides
    @Singleton
    JPAConfiguration provideConfiguration() throws FileNotFoundException, ConfigurationException {
        return JPAConfiguration.builder()
                .driverName(JDBC_EMBEDDED_DRIVER)
                .driverURL(JDBC_EMBEDDED_URL)
                .testOnBorrow(true)
                .validationQueryTimeoutSec(2)
                .validationQuery(VALIDATION_SQL_QUERY)
                .build();
    }
}
