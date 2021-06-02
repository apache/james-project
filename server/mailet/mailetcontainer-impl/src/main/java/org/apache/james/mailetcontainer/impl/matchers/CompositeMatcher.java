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

package org.apache.james.mailetcontainer.impl.matchers;

import java.util.List;

import org.apache.mailet.Matcher;

/**
 * A CompositeMatcher contains child matchers that are invoked in turn and their
 * recipient results are composed from the composite operation. See And, Or, Xor
 * and Not. One or more children may be supplied to a composite via declaration
 * inside a processor in the james-config.xml file. When the composite is the
 * outter-most declaration it must be named, as in the example below. The
 * composite matcher may be referenced by name and used in a subsequent mailet.
 * Any matcher may be included as a child of a composite matcher, including
 * another composite matcher or the Not matcher. As a consequence, the class
 * names: And, Or, Not and Xor are permanently reserved.
 * 
 * <pre>
 *   &lt;matcher name=&quot;a-composite&quot; match=&quot;Or&quot;&gt;
 *      &lt;matcher match=&quot;And&quot;&gt;
 *          &lt;matcher match=&quot;Not&quot;&gt;
 *              &lt;matcher match=&quot;HostIs=65.55.116.84&quot;/&gt;
 *          &lt;/matcher&gt;
 *          &lt;matcher match=&quot;HasHeaderWithRegex=X-Verify-SMTP,Host(.*)sending to us was not listening&quot;/&gt;
 *      &lt;/matcher&gt;
 *      &lt;matchwe match=&quot;HasHeaderWithRegex=X-DNS-Paranoid,(.*)&quot;/&gt;
 *   &lt;/matcher&gt;
 *   
 *   &lt;mailet match=&quot;a-composite&quot; class=&quot;ToProcessor&quot;&gt;
 *       &lt;processor&gt;spam&lt;/processor&gt;
 *   &lt;/mailet&gt;
 * *
 * </pre>
 * 
 */
public interface CompositeMatcher extends Matcher {

    /**
     * @return Immutable list for the child matchers
     */
    List<Matcher> getMatchers();

    /**
     * Add a child matcher to this composite matcher. This is called by
     * SpoolManager.setupMatcher()
     * 
     * @param matcher
     *            Matcher is the child that this composite treats.
     */
    void add(Matcher matcher);

}
