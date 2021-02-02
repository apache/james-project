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

package org.apache.james.imap.api.message.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;


class StatusResponseTest  {
    @Test
    void testResponseCodeExtension() {
        assertThat(StatusResponse.ResponseCode.createExtension("XEXTENSION")
                        .getCode()).describedAs("Preserve names beginning with X").isEqualTo("XEXTENSION");
        assertThat(StatusResponse.ResponseCode.createExtension("EXTENSION")
                        .getCode()).describedAs("Correct other names").isEqualTo("XEXTENSION");
    }

    @Test
    void responseCodeShouldBuildTheLongestEntryForMetadata() {
        assertThat(StatusResponse.ResponseCode.longestMetadataEntry(1024).getCode()).isEqualTo("METADATA LONGENTRIES");
        assertThat(StatusResponse.ResponseCode.longestMetadataEntry(1024).getNumber()).isEqualTo(1024);
    }
}
