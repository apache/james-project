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
package org.apache.james.jmap.core

import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.string.Uri

object RequestLevelErrorType {
  type ErrorTypeIdentifier = String Refined Uri
  val UNKNOWN_CAPABILITY: ErrorTypeIdentifier = "urn:ietf:params:jmap:error:unknownCapability"
  val NOT_JSON: ErrorTypeIdentifier = "urn:ietf:params:jmap:error:notJSON"
  val NOT_REQUEST: ErrorTypeIdentifier = "urn:ietf:params:jmap:error:notRequest"
  val LIMIT: ErrorTypeIdentifier = "urn:ietf:params:jmap:error:limit"
  val DEFAULT_ERROR_TYPE: ErrorTypeIdentifier = "about:blank"
}
