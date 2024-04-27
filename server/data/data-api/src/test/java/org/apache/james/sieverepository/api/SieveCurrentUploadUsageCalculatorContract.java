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

package org.apache.james.sieverepository.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

public interface SieveCurrentUploadUsageCalculatorContract {

    Username USERNAME = Username.of("test");
    ScriptName SCRIPT_NAME = new ScriptName("script");
    ScriptContent SCRIPT_CONTENT = new ScriptContent("Hello World");
    ScriptName OTHER_SCRIPT_NAME = new ScriptName("other_script");
    ScriptContent OTHER_SCRIPT_CONTENT = new ScriptContent("Other script content");

    Username USER_1 = Username.of("user1");

    SieveCurrentUploadUsageCalculator sieveCurrentUploadUsageCalculator();

    SieveRepository sieveRepository();

    long getSpaceUsage(Username username);

    @Test
    default void recomputeCurrentUploadUsageShouldRecomputeSuccessfully() throws Exception {
        sieveRepository().resetSpaceUsedReactive(USER_1, 9L).block();
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository().putScript(USERNAME, OTHER_SCRIPT_NAME, OTHER_SCRIPT_CONTENT);
        sieveCurrentUploadUsageCalculator().recomputeCurrentUploadUsage(USERNAME);

        assertThat(getSpaceUsage(USERNAME)).isEqualTo(31L);
    }

    @Test
    default void recomputeCurrentUploadUsageShouldRecomputeSuccessfullyWhenUserHasNoUpload() throws Exception {
        sieveRepository().resetSpaceUsedReactive(USER_1, 9L).block();
        sieveCurrentUploadUsageCalculator().recomputeCurrentUploadUsage(USERNAME);

        assertThat(getSpaceUsage(USERNAME)).isEqualTo(0L);
    }

}
