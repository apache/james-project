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

package org.apache.james.backends.opensearch.json;

/**
 * See {@link https://github.com/opensearch-project/opensearch-java/issues/292}
 *
 * The Jackson related code was copied from {@link https://github.com/opensearch-project/opensearch-java} in order to get rid of a SPI lookup for each deserialized request,
 * that was happening on the event loop.
 *
 * The only modified class is {@link jackson.JsonValueParser} where the SPI lookup was made static.
 */