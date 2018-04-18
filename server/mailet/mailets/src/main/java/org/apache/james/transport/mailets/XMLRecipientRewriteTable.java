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

package org.apache.james.transport.mailets;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.mail.MessagingException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.RecipientRewriteTableUtil;
import org.apache.mailet.Experimental;

/**
 * <p>
 * Implements a Virtual User Table to translate virtual users to real users.
 * This implementation has the same functionality as
 * <code>JDBCRecipientRewriteTable</code>, but is configured in the JAMES
 * configuration and is thus probably most suitable for smaller and less dynamic
 * mapping requirements.
 * </p>
 * <p>
 * The configuration is specified in the form:
 * 
 * <pre>
 * &lt;mailet match="All" class="XMLRecipientRewriteTable"&gt;
 *   &lt;mapping&gt;virtualuser@xxx=realuser[@yyy][;anotherrealuser[@zzz]]&lt;/mapping&gt;
 *   &lt;mapping&gt;virtualuser2@*=realuser2[@yyy][;anotherrealuser2[@zzz]]&lt;/mapping&gt;
 *   ...
 * &lt;/mailet&gt;
 * </pre>
 * 
 * </p>
 * <p>
 * As many &lt;mapping&gt; elements can be added as necessary. As indicated,
 * wildcards are supported, and multiple recipients can be specified with a
 * semicolon-separated list. The target domain does not need to be specified if
 * the real user is local to the server.
 * </p>
 * <p>
 * Matching is done in the following order:
 * <ol>
 * <li>
 * user@domain - explicit mapping for user@domain</li>
 * <li>
 * user@* - catchall mapping for user anywhere</li>
 * <li>
 * *@domain - catchall mapping for anyone at domain</li>
 * <li>
 * null - no valid mapping</li>
 * </ol>
 * </p>
 * 
 * @deprecated use the definitions in virtualusertable-store.xml instead
 * 
 */
@Experimental
@Deprecated
public class XMLRecipientRewriteTable extends AbstractRecipientRewriteTable {
    /**
     * Holds the configured mappings
     */
    private Map<MappingSource, String> mappings = new HashMap<>();

    @Override
    public void init() throws MessagingException {
        String mapping = getInitParameter("mapping");

        if (mapping != null) {
            mappings = RecipientRewriteTableUtil.getXMLMappings(mapping);
        }
    }

    /**
     * Map any virtual recipients to real recipients using the configured
     * mapping.
     * 
     * @param recipientsMap
     *            the mapping of virtual to real recipients
     */
    @Override
    protected void mapRecipients(Map<MailAddress, String> recipientsMap) {
        Collection<MailAddress> recipients = recipientsMap.keySet();

        for (MailAddress source : recipients) {
            String user = source.getLocalPart().toLowerCase(Locale.US);
            Domain domain = source.getDomain();

            String targetString = RecipientRewriteTableUtil.getTargetString(user, domain, mappings);

            if (targetString != null) {
                recipientsMap.put(source, targetString);
            }
        }
    }

    @Override
    public String getMailetInfo() {
        return "XML Virtual User Table mailet";
    }
}
