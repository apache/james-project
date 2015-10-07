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
import java.util.List;
import java.util.Map;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxListenerSupport;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;

public abstract class AbstractDelegatingMailboxListener implements MailboxListener, MailboxListenerSupport{
    
    protected AbstractDelegatingMailboxListener() {
    }
    
    /**
     * Receive the event and dispatch it to the right {@link MailboxListener} depending on
     * {@link org.apache.james.mailbox.MailboxListener.Event#getMailboxPath()}
     */
    public void event(Event event) {
        MailboxPath path = event.getMailboxPath();
        Map<MailboxPath, List<MailboxListener>> listeners = getListeners();
        List<MailboxListener> mListeners = null;
        synchronized (listeners) {
            mListeners = listeners.get(path);
            if (mListeners != null && mListeners.isEmpty() == false) {
             // take snapshot of the listeners list for later
                mListeners = new ArrayList<MailboxListener>(mListeners);
                
                if (event instanceof MailboxDeletion) {
                    // remove listeners if the mailbox was deleted
                    listeners.remove(path);
                } else if (event instanceof MailboxRenamed) {
                    // handle rename events
                    MailboxRenamed renamed = (MailboxRenamed) event;
                    List<MailboxListener> l = listeners.remove(path);
                    if (l != null) {
                        listeners.put(renamed.getNewPath(), l);
                    }
                }
                
            }
            
        }
        //outside the synchronized block against deadlocks from propagated events wanting to lock the listeners
        if (mListeners != null) {
            int sz = mListeners.size();
            for (int i = 0; i < sz; i++) {
                MailboxListener l = mListeners.get(i);
                l.event(event);
            }
        }
        
        List<MailboxListener> globalListeners = getGlobalListeners();
        if (globalListeners != null) {
            synchronized (globalListeners) {
                if (globalListeners.isEmpty() == false) {
                    List<MailboxListener> closedListener = new ArrayList<MailboxListener>();
                    //TODO do not fire them inside synchronized block too?
                    int sz = globalListeners.size();
                    for (int i = 0; i < sz; i++) {
                        MailboxListener l = globalListeners.get(i);
                        l.event(event);
                        
                    }
                    
                  
                    if (closedListener.isEmpty() == false) {
                        globalListeners.removeAll(closedListener);
                    }
                }
            }
        }
        
    }
    
    /**
     * @see org.apache.james.mailbox.MailboxListenerSupport#addListener(org.apache.james.mailbox.model.MailboxPath, org.apache.james.mailbox.MailboxListener, org.apache.james.mailbox.MailboxSession)
     */
    public void addListener(MailboxPath path, MailboxListener listener, MailboxSession session) throws MailboxException {
        Map<MailboxPath, List<MailboxListener>> listeners = getListeners();
        
        if (listeners != null) {
            synchronized (listeners) {
                List<MailboxListener> mListeners = listeners.get(path);
                if (mListeners == null) {
                    mListeners = new ArrayList<MailboxListener>();
                    listeners.put(path, mListeners);
                }
                if (mListeners.contains(listener) == false) {
                    mListeners.add(listener);
                }        
            }
        } else {
            throw new MailboxException("Cannot add MailboxListener to null list");
        }
    }

    /**
     * @see org.apache.james.mailbox.MailboxListenerSupport#addGlobalListener(org.apache.james.mailbox.MailboxListener, org.apache.james.mailbox.MailboxSession)
     */
    public void addGlobalListener(MailboxListener listener, MailboxSession session) throws MailboxException {
        List<MailboxListener> gListeners = getGlobalListeners();
        
        if (gListeners != null) {
            synchronized (gListeners) {
                gListeners.add(listener);
            }
        } else {
            throw new MailboxException("Cannot add MailboxListener to null list");
        }
    }

    /**
     * @see org.apache.james.mailbox.MailboxListenerSupport#removeListener(org.apache.james.mailbox.model.MailboxPath, org.apache.james.mailbox.MailboxListener, org.apache.james.mailbox.MailboxSession)
     */
    public void removeListener(MailboxPath mailboxPath, MailboxListener listener, MailboxSession session) throws MailboxException {
        Map<MailboxPath, List<MailboxListener>> listeners = getListeners();
        
        if (listeners != null) {
            synchronized (listeners) {
                List<MailboxListener> mListeners = listeners.get(mailboxPath);
                if (mListeners != null) {
                    mListeners.remove(listener);
                    if (mListeners.isEmpty()) {
                        listeners.remove(mailboxPath);
                    }
                }
            }
        } else {
            throw new MailboxException("Cannot remove MailboxListener from null list");
        }
    }

    /**
     * @see org.apache.james.mailbox.MailboxListenerSupport#removeGlobalListener(org.apache.james.mailbox.MailboxListener, org.apache.james.mailbox.MailboxSession)
     */
    public void removeGlobalListener(MailboxListener listener, MailboxSession session) throws MailboxException {
        List<MailboxListener> gListeners = getGlobalListeners();

        if (gListeners != null) {
            synchronized (gListeners) {
                gListeners.remove(listener);
            }
        } else {
            throw new MailboxException("Cannot remove MailboxListener from null list");
        }
    }

    /**
     * Return the {@link Map} which is used to store the {@link MailboxListener}
     * 
     * @return listeners
     */
    protected abstract Map<MailboxPath, List<MailboxListener>> getListeners();
    
    /**
     * Return the {@link List} which is used tos tore the global {@link MailboxListener}
     * 
     * @return globalListeners
     */
    protected abstract List<MailboxListener> getGlobalListeners();
    
    
}
