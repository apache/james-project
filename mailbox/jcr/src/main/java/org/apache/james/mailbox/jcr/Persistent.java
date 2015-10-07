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
package org.apache.james.mailbox.jcr;

import java.io.IOException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;


public interface Persistent {

    /**
     * Merge the object with the node
     * 
     * @param node
     */
    public void merge(Node node) throws RepositoryException, IOException;

    /**
     * Return underlying Node
     * 
     * @return node
     */
    public Node getNode();

    /**
     * Return if the object is persistent
     * 
     * @return <code>true</code> if object is persistent else <code>false</code>
     */
    public boolean isPersistent();

}
