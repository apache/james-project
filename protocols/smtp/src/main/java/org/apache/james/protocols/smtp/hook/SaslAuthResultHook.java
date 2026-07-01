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

package org.apache.james.protocols.smtp.hook;

import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.smtp.SMTPSession;

/**
 * Decorates terminal SMTP SASL authentication results without participating in credential validation.
 * <p>
 * Use this hook for legacy {@link AuthHook} use cases that only need authentication result side effects,
 * such as audit events, notifications or metrics. Credential validation should be implemented by a SASL
 * mechanism instead.
 */
public interface SaslAuthResultHook extends Hook {
    void onSuccess(SMTPSession session, String mechanismName, SaslIdentity identity);

    void onFailure(SMTPSession session, String mechanismName, SaslFailure failure);
}
