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

package org.apache.james.modules.mailbox;

import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class CassandraSchemaVersionStartUpCheck implements StartUpCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraSchemaVersionStartUpCheck.class);
    static final String CHECK_NAME = "CassandraSchemaVersionStartUpCheck";

    private final CassandraSchemaVersionManager versionManager;

    @Inject
    public CassandraSchemaVersionStartUpCheck(CassandraSchemaVersionManager versionManager) {
        this.versionManager = versionManager;
    }

    @Override
    public CheckResult check() {
        CassandraSchemaVersionManager.SchemaState schemaState = versionManager.computeSchemaState();
        switch (schemaState) {
            case TOO_OLD:
                return checkTooOldState();
            case TOO_RECENT:
                return checkTooRecentState();
            case UP_TO_DATE:
                return checkUpToDateState();
            case UPGRADABLE:
                return checkUpgradeAbleState();
            default:
                String unknownSchemaStateMessage = "Unknown schema state " + schemaState;
                LOGGER.error(unknownSchemaStateMessage);
                return CheckResult.builder()
                    .checkName(checkName())
                    .resultType(ResultType.BAD)
                    .description(unknownSchemaStateMessage)
                    .build();
        }
    }

    @Override
    public String checkName() {
        return CHECK_NAME;
    }

    private CheckResult checkUpgradeAbleState() {
        String upgradeVersionMessage =
            String.format("Current schema version is %d. Recommended version is %d",
                versionManager.computeVersion().block().getValue(),
                versionManager.getMaximumSupportedVersion().getValue());
        LOGGER.warn(upgradeVersionMessage);
        return CheckResult.builder()
            .checkName(checkName())
            .resultType(ResultType.GOOD)
            .description(upgradeVersionMessage)
            .build();
    }

    private CheckResult checkUpToDateState() {
        String message = "Schema version is up-to-date";
        LOGGER.info(message);
        return CheckResult.builder()
            .checkName(checkName())
            .resultType(ResultType.GOOD)
            .description(message)
            .build();
    }

    private CheckResult checkTooRecentState() {
        String versionExceedMaximumSupportedMessage =
            String.format("Current schema version is %d whereas the maximum supported version is %d. " +
                "Recommended version is %d.",
                versionManager.computeVersion().block().getValue(),
                versionManager.getMaximumSupportedVersion().getValue(),
                versionManager.getMaximumSupportedVersion().getValue());
        LOGGER.error(versionExceedMaximumSupportedMessage);
        return CheckResult.builder()
            .checkName(checkName())
            .resultType(ResultType.BAD)
            .description(versionExceedMaximumSupportedMessage)
            .build();
    }

    private CheckResult checkTooOldState() {
        String versionToOldMessage =
            String.format("Current schema version is %d whereas minimum required version is %d. " +
                "Recommended version is %d",
                versionManager.computeVersion().block().getValue(),
                versionManager.getMinimumSupportedVersion().getValue(),
                versionManager.getMaximumSupportedVersion().getValue());
        LOGGER.error(versionToOldMessage);
        return CheckResult.builder()
            .checkName(checkName())
            .resultType(ResultType.BAD)
            .description(versionToOldMessage)
            .build();
    }
}
