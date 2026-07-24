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

package org.apache.james.protocols.sasl.kerberos;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KeyTab;

import org.apache.commons.configuration2.ex.ConfigurationException;

class KeyTabPrincipalVerifier {
    private static KerberosKey[] keys(GssapiSaslConfiguration configuration) throws ConfigurationException {
        try {
            return KeyTab.getInstance(configuration.keyTab().toFile())
                .getKeys(new KerberosPrincipal(configuration.principal()));
        } catch (RuntimeException e) {
            throw new ConfigurationException("Unable to read the configured GSSAPI keytab", e);
        }
    }

    private static void destroy(KerberosKey[] keys) throws ConfigurationException {
        DestroyFailedException failure = null;
        for (KerberosKey key : keys) {
            try {
                key.destroy();
            } catch (DestroyFailedException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw new ConfigurationException("Unable to release keys read from the configured GSSAPI keytab", failure);
        }
    }

    void verify(GssapiSaslConfiguration configuration) throws ConfigurationException {
        KerberosKey[] keys = keys(configuration);
        try {
            if (keys.length == 0) {
                throw new ConfigurationException("The configured GSSAPI keytab does not contain the configured principal");
            }
        } finally {
            destroy(keys);
        }
    }
}
