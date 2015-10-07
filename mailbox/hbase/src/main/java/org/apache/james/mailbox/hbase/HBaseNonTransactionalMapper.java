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
package org.apache.james.mailbox.hbase;

import org.apache.james.mailbox.store.transaction.NonTransactionalMapper;

/**
 * HBase implementation of TransactionMapper. 
 * I don't know if this class is thread-safe!
 * Assume it is not!
 * 
 */
public class HBaseNonTransactionalMapper extends NonTransactionalMapper {

    /**
     * End request
     */
    @Override
    public void endRequest() {
        //TODO: maybe do some thing more wise here?
        //System.out.println("Bye!");
    }
}
