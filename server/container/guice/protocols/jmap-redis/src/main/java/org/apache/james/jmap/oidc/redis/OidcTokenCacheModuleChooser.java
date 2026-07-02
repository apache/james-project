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

package org.apache.james.jmap.oidc.redis;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.jmap.oidc.CaffeineOidcTokenCacheModule;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Module;
import com.google.inject.util.Modules;

public class OidcTokenCacheModuleChooser {
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcTokenCacheModuleChooser.class);

    public enum Implementation {
        CAFFEINE, REDIS;

        public static Implementation from(PropertiesProvider propertiesProvider) {
            try {
                propertiesProvider.getConfiguration("redis");
                LOGGER.info("Redis configuration was found, using Redis OIDC token cache");
                return REDIS;
            } catch (FileNotFoundException e) {
                LOGGER.info("Redis configuration was not found, using in-memory OIDC token cache");
                return CAFFEINE;
            } catch (ConfigurationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Module chooseModules(Implementation implementation) {
        return chooseModules(implementation, new RedisOidcTokenCacheKeyPrefixModule());
    }

    public static Module chooseModules(Implementation implementation, Module redisOidcTokenCacheKeyPrefixModule) {
        return switch (implementation) {
            case CAFFEINE -> new CaffeineOidcTokenCacheModule();
            case REDIS -> Modules.combine(new RedisOidcTokenCacheModule(), redisOidcTokenCacheKeyPrefixModule);
        };
    }
}
