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

package org.apache.james.mailrepository.file;

import java.util.Hashtable;

/**
 * Provides Lock functionality
 */
public class Lock {
    /**
     * An internal hash table of keys to locks
     */
    private final Hashtable<Object, Object> locks = new Hashtable<>();

    /**
     * Check to see if the object is locked
     * 
     * @param key
     *            the Object on which to check the lock
     * @return true if the object is locked, false otherwise
     */
    public boolean isLocked(Object key) {
        return (locks.get(key) != null);
    }

    /**
     * Lock on a given object.
     * 
     * @param key
     *            the Object on which to lock
     * @return true if the locking was successful, false otherwise
     */
    public boolean lock(Object key) {
        Object theLock;

        synchronized (this) {
            theLock = locks.get(key);

            if (null == theLock) {
                locks.put(key, getCallerId());
                return true;
            } else {
                return getCallerId() == theLock;
            }
        }
    }

    /**
     * Release the lock on a given object.
     * 
     * @param key
     *            the Object on which the lock is held
     * @return true if the unlocking was successful, false otherwise
     */
    public boolean unlock(Object key) {
        Object theLock;
        synchronized (this) {
            theLock = locks.get(key);

            if (null == theLock) {
                return true;
            } else if (getCallerId() == theLock) {
                locks.remove(key);
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Private helper method to abstract away caller ID.
     * 
     * @return the id of the caller (i.e. the Thread reference)
     */
    private Object getCallerId() {
        return Thread.currentThread();
    }
}
