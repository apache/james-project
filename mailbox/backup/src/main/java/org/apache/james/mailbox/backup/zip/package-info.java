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
 *  The zip archive format for the backup of an account
 *
 *  This archive contains the mailboxes of the account. And for each mailbox :
 *   - its annotations
 *   - its messages
 *
 *  This structure is repeated sequentially for each mailbox.
 *  The order presented here is the order in which the entries are added to the zip file.
 *  This is important and must be respected because during the restoration, the file is read iteratively in memory from an inputstream,
 *  without storing the whole structure on memory nor on disk.
 *
 *  So we have by order of insertion mailbox1 followed by it's elements then mailbox2 and its elements ...
 *
 *  This archive contains the following entries for each mailbox of the user :
 *<ul>
 * <li><b>'mailboxName/'</b> : directory entry</li>
 * <li>'mailboxName/annotations/' : directory entry, optional. Is present only if the mailbox contains some annotations</li>
 * <li>'mailboxName/annotations/annotation1Key' : file entry which name is the key of the annotation,
 *                                              the content of the annotation is stored in the content of the file</li>
 * <li>'mailboxName/annotations/annotation2Key' : idem a mailbox may contains several annotations</li>
 * <li>'messageId1' : file entry which name is the serialized {@link org.apache.james.mailbox.model.MessageId} of the message,
 *                  the raw RFC822 message is stored in the content of the file</li>
 * <li>'messageId2' : a mailbox may contains several messages.</li>
 * <li><b>mailbox2Name</b></li>
 * <li>...</li>
 *</ul>
 *
 *
 * @see the unit test {@link= org.apache.james.mailbox.backup.ZipperTest} for more information about this format
 *
 */
package org.apache.james.mailbox.backup.zip;