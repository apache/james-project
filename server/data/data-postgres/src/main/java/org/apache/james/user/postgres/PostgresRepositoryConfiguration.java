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

package org.apache.james.user.postgres;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.user.lib.model.Algorithm;
import org.apache.james.user.lib.model.Algorithm.HashingMode;

public class PostgresRepositoryConfiguration {
    public static final String DEFAULT_ALGORITHM = "PBKDF2-SHA512";
    public static final String DEFAULT_HASHING_MODE = HashingMode.PLAIN.name();

    public static final PostgresRepositoryConfiguration DEFAULT = new PostgresRepositoryConfiguration(
        Algorithm.of(DEFAULT_ALGORITHM), HashingMode.parse(DEFAULT_HASHING_MODE)
    );

    private final Algorithm preferredAlgorithm;
    private final HashingMode fallbackHashingMode;

    public PostgresRepositoryConfiguration(Algorithm preferredAlgorithm, HashingMode fallbackHashingMode) {
        this.preferredAlgorithm = preferredAlgorithm;
        this.fallbackHashingMode = fallbackHashingMode;
    }

    public Algorithm getPreferredAlgorithm() {
        return preferredAlgorithm;
    }

    public HashingMode getFallbackHashingMode() {
        return fallbackHashingMode;
    }

    public static PostgresRepositoryConfiguration from(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {
        return new PostgresRepositoryConfiguration(
            Algorithm.of(config.getString("algorithm", DEFAULT_ALGORITHM)),
            HashingMode.parse(config.getString("hashingMode", DEFAULT_HASHING_MODE)));
    }
}
