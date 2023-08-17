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


/**
 * </p>
 * A normal James server can contains a variety of
 * {@link org.apache.james.mailrepository.api.MailRepository MailRepositories},
 * usual examples include :
 * <ul>
 *     <li>a repository for mails that have been received and are pending processing,</li>
 *     <li>a repository for mails that ended in dead letter,</li>
 *     <li>a repository for mails that are queued for sending,</li>
 *     <li>etc.</li>
 * </ul>
 * Since the storage access profile can vary wildly depending on usage, it can make
 * sense to have different data store technologies for different mail repositories.
 * This allows server operators to leverage the corresponding store
 * characteristics to optimize for cost or performance.
 * </p>
 *
 * <p>
 * This is why a mail repository is associated to a specific
 * {@link org.apache.james.mailrepository.api.MailRepositoryUrl URL} defined by:
 * <ul>
 *   <li>a protocol which is bound to a specific data store implementation</li>
 *   <li>a path which uniquely identifies the repository for the given protocol</li>
 * </ul>
 *</p>
 *
 * <p>Mail repositories can be created both:
 * <ul>
 *     <li>Through static configuration (for instance through the repositoryPath
 *     directive in mailetcontainer.xml).</li>
 *     <li>Dynamically at runtime through webmin (such as the route defined in
 *     {@link org.apache.james.webadmin.routes.MailRepositoriesRoutes#definePutMailRepository() PUT /mailRepositories}).</li>
 * </ul>
 * Since URLs can be created dynamically as well as statically, they are stored
 * in a {@link org.apache.james.mailrepository.api.MailRepositoryUrlStore} so
 * that they can be restored automatically after a server restart. This also
 * lets the operator list the URLs in use in a system facilitating store
 * migrations or cleanups.
 * </p>
 * <p>The mail repository url store is public for store
 * implementors but should not be used directly. Prefer going through the
 * {@link org.apache.james.mailrepository.api.MailRepositoryStore}
 * </p>
 *
 *
 */
package org.apache.james.mailrepository.api;

