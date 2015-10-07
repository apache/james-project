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

package org.apache.james.mailbox.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.model.MailboxPath;

/**
 * Receive a {@link org.apache.james.mailbox.MailboxListener.Event} and delegate it to an other
 * {@link MailboxListener} depending on the registered name
 *
 */
public class HashMapDelegatingMailboxListener extends AbstractDelegatingMailboxListener{

    private Map<MailboxPath, List<MailboxListener>> listeners = new HashMap<MailboxPath, List<MailboxListener>>();
    private List<MailboxListener> globalListeners = new ArrayList<MailboxListener>();

    @Override
    protected Map<MailboxPath, List<MailboxListener>> getListeners() {
        return listeners;
    }

    @Override
    protected List<MailboxListener> getGlobalListeners() {
        return globalListeners;
    }
    
}
