/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james.quota.search.elasticsearch.v7;

import org.apache.james.backends.es.v7.IndexName;
import org.apache.james.backends.es.v7.ReadAliasName;
import org.apache.james.backends.es.v7.WriteAliasName;

public interface QuotaRatioElasticSearchConstants {

    interface InjectionNames {
        String QUOTA_RATIO = "quotaRatio";
    }

    WriteAliasName DEFAULT_QUOTA_RATIO_WRITE_ALIAS = new WriteAliasName("quota_ratio_write_alias");
    ReadAliasName DEFAULT_QUOTA_RATIO_READ_ALIAS = new ReadAliasName("quota_ratio_read_alias");
    IndexName DEFAULT_QUOTA_RATIO_INDEX = new IndexName("quota_ratio_v1");
}
