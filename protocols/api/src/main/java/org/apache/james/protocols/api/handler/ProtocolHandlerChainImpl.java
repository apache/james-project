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

package org.apache.james.protocols.api.handler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * {@link AbstractProtocolHandlerChain} which is mutable till the
 * {@link #wireExtensibleHandlers()} is called. After that all operations which
 * try to modify the instance will throw and
 * {@link UnsupportedOperationException}
 * 
 * 
 */
public class ProtocolHandlerChainImpl extends AbstractProtocolHandlerChain implements List<ProtocolHandler> {

    private final List<ProtocolHandler> handlers = new ArrayList<>();
    private volatile boolean readyOnly = false;

    /**
     * Once this is called all tries to modify this
     * {@link ProtocolHandlerChainImpl} will throw an
     * {@link UnsupportedOperationException}
     */
    @Override
    public void wireExtensibleHandlers() throws WiringException {
        super.wireExtensibleHandlers();
        readyOnly = true;
    }

    protected final boolean isReadyOnly() {
        return readyOnly;
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#add(java.lang.Object)
     */
    public boolean add(ProtocolHandler handler) {
        checkReadOnly();
        return handlers.add(handler);
    }

    @Override
    protected List<ProtocolHandler> getHandlers() {
        return Collections.unmodifiableList(handlers);
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#size()
     */
    public int size() {
        return handlers.size();
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#isEmpty()
     */
    public boolean isEmpty() {
        return handlers.isEmpty();
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#contains(java.lang.Object)
     */
    public boolean contains(Object o) {
        return handlers.contains(o);
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#iterator()
     */
    public Iterator<ProtocolHandler> iterator() {
        return new ProtocolHandlerIterator(handlers.listIterator());
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#toArray()
     */
    public Object[] toArray() {
        return handlers.toArray();
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#toArray(T[])
     */
    public <T> T[] toArray(T[] a) {
        return handlers.toArray(a);
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#remove(java.lang.Object)
     */
    public boolean remove(Object o) {
        checkReadOnly();

        return handlers.remove(o);
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#containsAll(java.util.Collection)
     */
    public boolean containsAll(Collection<?> c) {
        return handlers.containsAll(c);
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#addAll(java.util.Collection)
     */
    public boolean addAll(Collection<? extends ProtocolHandler> c) {
        checkReadOnly();

        return handlers.addAll(c);
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#addAll(int, java.util.Collection)
     */
    public boolean addAll(int index, Collection<? extends ProtocolHandler> c) {
        checkReadOnly();

        return handlers.addAll(index, c);
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#removeAll(java.util.Collection)
     */
    public boolean removeAll(Collection<?> c) {
        checkReadOnly();

        return handlers.removeAll(c);
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#retainAll(java.util.Collection)
     */
    public boolean retainAll(Collection<?> c) {
        return handlers.retainAll(c);
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#clear()
     */
    public void clear() {
        checkReadOnly();

        handlers.clear();
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#get(int)
     */
    public ProtocolHandler get(int index) {
        return (ProtocolHandler) handlers.get(index);
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#set(int, java.lang.Object)
     */
    public ProtocolHandler set(int index, ProtocolHandler element) {
        checkReadOnly();

        return (ProtocolHandler) handlers.set(index, element);
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#add(int, java.lang.Object)
     */
    public void add(int index, ProtocolHandler element) {
        checkReadOnly();

        handlers.add(index, element);
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#remove(int)
     */
    public ProtocolHandler remove(int index) {
        checkReadOnly();

        return (ProtocolHandler) handlers.remove(index);
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#indexOf(java.lang.Object)
     */
    public int indexOf(Object o) {
        return handlers.indexOf(o);
    }


    /*
     * (non-Javadoc)
     * @see java.util.List#lastIndexOf(java.lang.Object)
     */
    public int lastIndexOf(Object o) {
        return handlers.lastIndexOf(o);
    }

    /*
     * 
     * (non-Javadoc)
     * @see java.util.List#listIterator()
     */
    public ListIterator<ProtocolHandler> listIterator() {
        return new ProtocolHandlerIterator(handlers.listIterator());
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#listIterator(int)
     */
    public ListIterator<ProtocolHandler> listIterator(int index) {
        return new ProtocolHandlerIterator(handlers.listIterator(index));
    }

    /*
     * (non-Javadoc)
     * @see java.util.List#subList(int, int)
     */
    public List<ProtocolHandler> subList(int fromIndex, int toIndex) {
        List<ProtocolHandler> sList = new ArrayList<>(handlers.subList(fromIndex, toIndex));
        if (readyOnly) {
            return Collections.unmodifiableList(sList);
        }
        return sList;
    }

    private void checkReadOnly() {
        if (readyOnly) {
            throw new UnsupportedOperationException("Ready-only");
        }
    }
    
    private final class ProtocolHandlerIterator implements ListIterator<ProtocolHandler> {
        private final ListIterator<ProtocolHandler> handlers;

        public ProtocolHandlerIterator(ListIterator<ProtocolHandler> handlers) {
            this.handlers = handlers;
        }

        /*
         * (non-Javadoc)
         * @see java.util.ListIterator#hasNext()
         */
        public boolean hasNext() {
            return handlers.hasNext();
        }

        /*
         * (non-Javadoc)
         * @see java.util.ListIterator#next()
         */
        public ProtocolHandler next() {
            return (ProtocolHandler) handlers.next();
        }

        /*
         * (non-Javadoc)
         * @see java.util.ListIterator#hasPrevious()
         */
        public boolean hasPrevious() {
            return handlers.hasPrevious();
        }

        /*
         * (non-Javadoc)
         * @see java.util.ListIterator#previous()
         */
        public ProtocolHandler previous() {
            return handlers.previous();
        }

        /*
         * (non-Javadoc)
         * @see java.util.ListIterator#nextIndex()
         */
        public int nextIndex() {
            return handlers.nextIndex();
        }

        /*
         * (non-Javadoc)
         * @see java.util.ListIterator#previousIndex()
         */
        public int previousIndex() {
            return handlers.previousIndex();
        }

        /*
         * (non-Javadoc)
         * @see java.util.ListIterator#remove()
         */
        public void remove() {
            checkReadOnly();

            handlers.previousIndex();
        }

        /*
         * (non-Javadoc)
         * @see java.util.ListIterator#set(java.lang.Object)
         */
        public void set(ProtocolHandler e) {
            checkReadOnly();

            handlers.set(e);
        }

        /*
         * (non-Javadoc)
         * @see java.util.ListIterator#add(java.lang.Object)
         */
        public void add(ProtocolHandler e) {
            checkReadOnly();
            handlers.add(e);
        }

    }
    


}
