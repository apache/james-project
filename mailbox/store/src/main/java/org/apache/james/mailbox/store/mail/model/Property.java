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
package org.apache.james.mailbox.store.mail.model;


/**
 * <p>Values a namespaced property.</p>
 * <p>
 * The full name of a namespaced property consists of 
 * a local part ({@link #getLocalName()}) and a namespace ({@link #getNamespace()()}). 
 * This is similar - in concept - the local part and namespace of a <code>QName</code>
 * in <abbr title='eXtensible Markup Language'>XML</a>. 
 * </p><p>
 * Conventionally, the namespace
 * is an <abbr title='Uniform Resource Identifier'>URI</abbr> 
 * and the name is simple, leading to a natural mapping into 
 * <abbr title='Resource Description Framework'>RDF</abbr>.
 * For example - namespace "http://james.apache.org/rfc2045",
 * name "Content-Transfer-Encoding", value "BASE64" mapping to
 * predicate "http://james.apache.org/rfc2045#Content-Transfer-Encoding",
 * object "BASE64".
 * </p>
 */
public interface Property {

    /**
     * Gets the namespace for the name.
     * @return not null
     */
    String getNamespace();
    
    /**
     * Gets the local part of the name of the property.
     * @return not null
     */
    String getLocalName();
    
    /**
     * Gets the value for this property.
     * @return not null
     */
    String getValue();


}
