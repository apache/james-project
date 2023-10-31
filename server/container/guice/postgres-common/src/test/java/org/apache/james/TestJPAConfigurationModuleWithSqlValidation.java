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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.inject.Singleton;

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
            setupAuthenticationOnDerby();
        }

        @Provides
        @Singleton
        JPAConfiguration provideConfiguration() {
            return jpaConfigurationBuilder()
                .username(DATABASE_USERNAME)
                .password(DATABASE_PASSWORD)
                .build();
        }

        private void setupAuthenticationOnDerby() {
            try (Connection conn = DriverManager.getConnection(JDBC_EMBEDDED_URL, DATABASE_USERNAME, DATABASE_PASSWORD)) {
                // Setting and Confirming requireAuthentication
                setDerbyProperty(conn, "derby.connection.requireAuthentication", "true");

                // Setting authentication scheme and username password to Derby
                setDerbyProperty(conn, "derby.authentication.provider", "BUILTIN");
                setDerbyProperty(conn, "derby.user." + DATABASE_USERNAME + "", DATABASE_PASSWORD);
                setDerbyProperty(conn, "derby.database.propertiesOnly", "true");

                // Setting default connection mode to no access to restrict accesses without authentication information
                setDerbyProperty(conn, "derby.database.defaultConnectionMode", "noAccess");
                setDerbyProperty(conn, "derby.database.fullAccessUsers", DATABASE_USERNAME);
                setDerbyProperty(conn, "derby.database.propertiesOnly", "false");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        private void setDerbyProperty(Connection conn, String key, String value) {
            try (CallableStatement call = conn.prepareCall("CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(?, ?)")) {
                call.setString(1, key);
                call.setString(2, value);
                call.execute();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    String DATABASE_USERNAME = "james";
    String DATABASE_PASSWORD = "james-secret";
    String JDBC_EMBEDDED_URL = "jdbc:derby:memory:mailboxintegration;create=true";
    String JDBC_EMBEDDED_DRIVER = org.apache.derby.jdbc.EmbeddedDriver.class.getName();
    String VALIDATION_SQL_QUERY = "VALUES 1";

    static JPAConfiguration.ReadyToBuild jpaConfigurationBuilder() {
        return JPAConfiguration.builder()
                .driverName(JDBC_EMBEDDED_DRIVER)
                .driverURL(JDBC_EMBEDDED_URL)
                .testOnBorrow(true)
                .validationQueryTimeoutSec(2)
                .validationQuery(VALIDATION_SQL_QUERY);
    }
}
