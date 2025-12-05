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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import jakarta.inject.Singleton;

import org.apache.james.backends.jpa.JPAConfiguration;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public interface TestJPAConfigurationModuleWithSqlValidation {

    class NoDatabaseAuthentication extends AbstractModule {
        @Override
        protected void configure() {
        }

        @Provides
        @Singleton
        JPAConfiguration provideConfiguration() {
            return jpaConfigurationBuilder().build();
        }
    }

    class WithDatabaseAuthentication extends AbstractModule {

        @Override
        protected void configure() {
            setupDatabaseAuthentication();
        }

        @Provides
        @Singleton
        JPAConfiguration provideConfiguration() {
            return jpaConfigurationBuilder()
                .username(DATABASE_USERNAME)
                .password(DATABASE_PASSWORD)
                .build();
        }

        private void setupDatabaseAuthentication() {
            try (Connection conn = DriverManager.getConnection(JDBC_EMBEDDED_URL, DATABASE_USERNAME, DATABASE_PASSWORD)) {
                // H2 authentication is handled through the connection URL and credentials
                // No additional setup required as H2 accepts user/password in connection parameters
                // The database will be created with the provided credentials
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    String DATABASE_USERNAME = "james";
    String DATABASE_PASSWORD = "james-secret";
    String JDBC_EMBEDDED_URL = "jdbc:h2:mem:mailboxintegration;DB_CLOSE_DELAY=-1";
    String JDBC_EMBEDDED_DRIVER = org.h2.Driver.class.getName();
    String VALIDATION_SQL_QUERY = "SELECT 1";

    static JPAConfiguration.ReadyToBuild jpaConfigurationBuilder() {
        return JPAConfiguration.builder()
                .driverName(JDBC_EMBEDDED_DRIVER)
                .driverURL(JDBC_EMBEDDED_URL)
                .testOnBorrow(true)
                .validationQueryTimeoutSec(2)
                .validationQuery(VALIDATION_SQL_QUERY);
    }
}
