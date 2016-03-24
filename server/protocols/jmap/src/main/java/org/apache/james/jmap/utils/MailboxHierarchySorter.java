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

package org.apache.james.jmap.utils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.james.jmap.model.mailbox.Mailbox;

import com.google.common.collect.Lists;

public class MailboxHierarchySorter {

    public List<Mailbox> sortFromRootToLeaf(List<Mailbox> mailboxes) {

        Map<String, Mailbox> mapOfMailboxesById = indexMailboxesById(mailboxes);

        DependencyGraph<Mailbox> graph = new DependencyGraph<>(m ->
                m.getParentId().map(mapOfMailboxesById::get));

        mailboxes.stream().forEach(graph::registerItem);

        return graph.getBuildChain().collect(Collectors.toList());
    }

    private Map<String, Mailbox> indexMailboxesById(List<Mailbox> mailboxes) {
        return mailboxes.stream()
                .collect(Collectors.toMap(Mailbox::getId, Function.identity()));
    }

    public List<Mailbox> sortFromLeafToRoot(List<Mailbox> mailboxes) {
        return Lists.reverse(sortFromRootToLeaf(mailboxes));
    }
}
