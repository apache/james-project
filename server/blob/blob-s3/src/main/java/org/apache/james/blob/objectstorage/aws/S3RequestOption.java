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

package org.apache.james.blob.objectstorage.aws;

import java.util.Optional;

import org.apache.james.blob.objectstorage.aws.sse.S3SSECustomerKeyFactory;

import com.google.common.base.Preconditions;

public record S3RequestOption(SSEC ssec) {
    static S3RequestOption DEFAULT = new S3RequestOption(S3RequestOption.SSEC.DISABLED);

    public record SSEC(boolean enable, java.util.Optional<S3SSECustomerKeyFactory> sseCustomerKeyFactory) {
        static S3RequestOption.SSEC DISABLED = new S3RequestOption.SSEC(false, Optional.empty());

        public SSEC {
            Preconditions.checkArgument(!enable || sseCustomerKeyFactory.isPresent(), "SSE Customer Key Factory must be present when SSE is enabled");
        }
    }
}
