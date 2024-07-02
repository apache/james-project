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

package org.apache.james.user.ldap;

import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.ThrowingFunction;
import com.google.common.collect.ImmutableList;
import com.unboundid.ldap.sdk.BindRequest;
import com.unboundid.ldap.sdk.FailoverServerSet;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ServerSet;
import com.unboundid.ldap.sdk.SimpleBindRequest;
import com.unboundid.ldap.sdk.SingleServerSet;

public class LDAPConnectionFactory {

    private static final TrustManager DUMMY_TRUST_MANAGER = new X509TrustManager() {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // Always trust
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // Always trust
        }
    };

    private final LdapRepositoryConfiguration configuration;
    private final LDAPConnectionPool ldapConnectionPool;

    public LDAPConnectionFactory(LdapRepositoryConfiguration configuration) throws LDAPException {
        this.configuration = configuration;

        LDAPConnectionOptions connectionOptions = new LDAPConnectionOptions();
        connectionOptions.setConnectTimeoutMillis(configuration.getConnectionTimeout());
        connectionOptions.setResponseTimeoutMillis(configuration.getReadTimeout());

        BindRequest bindRequest = new SimpleBindRequest(configuration.getPrincipal(), configuration.getCredentials());

        List<ServerSet> serverSets = configuration.getLdapHosts()
            .stream()
            .map(toSingleServerSet(connectionOptions, bindRequest))
            .collect(ImmutableList.toImmutableList());

        FailoverServerSet failoverServerSet = new FailoverServerSet(serverSets);
        ldapConnectionPool = new LDAPConnectionPool(failoverServerSet, bindRequest, configuration.getPoolSize());
        ldapConnectionPool.setRetryFailedOperationsDueToInvalidConnections(true);
        ldapConnectionPool.setMaxWaitTimeMillis(configuration.getMaxWaitTime());
    }

    private ThrowingFunction<URI, SingleServerSet> toSingleServerSet(LDAPConnectionOptions connectionOptions, BindRequest bindRequest) {
        return Throwing.function(uri -> new SingleServerSet(uri.getHost(), uri.getPort(), supportLDAPS(uri), connectionOptions, bindRequest, null));
    }

    private SocketFactory supportLDAPS(URI uri) throws KeyManagementException, NoSuchAlgorithmException {
        if (uri.getScheme().equals("ldaps")) {
            if (configuration.isTrustAllCerts()) {
                SSLContext context = SSLContext.getInstance("TLSv1.2");
                context.init(null, new TrustManager[]{DUMMY_TRUST_MANAGER}, null);
                return context.getSocketFactory();
            }
            return SSLSocketFactory.getDefault();
        } else {
            return null;
        }
    }

    public LDAPConnectionPool getLdapConnectionPool() {
        return ldapConnectionPool;
    }
}
