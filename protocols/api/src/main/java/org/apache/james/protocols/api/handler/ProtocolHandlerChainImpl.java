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
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import com.google.common.collect.ImmutableList;

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

    @Override
    public boolean add(ProtocolHandler handler) {
        checkReadOnly();
        return handlers.add(handler);
    }

    @Override
    protected List<ProtocolHandler> getHandlers() {
        return ImmutableList.copyOf(handlers);
    }

    @Override
    public int size() {
        return handlers.size();
    }

    @Override
    public boolean isEmpty() {
        return handlers.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return handlers.contains(o);
    }

    @Override
    public Iterator<ProtocolHandler> iterator() {
        return new ProtocolHandlerIterator(handlers.listIterator());
    }

    @Override
    public Object[] toArray() {
        return handlers.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return handlers.toArray(a);
    }

    @Override
    public boolean remove(Object o) {
        checkReadOnly();

        return handlers.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return handlers.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends ProtocolHandler> c) {
        checkReadOnly();

        return handlers.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends ProtocolHandler> c) {
        checkReadOnly();

        return handlers.addAll(index, c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        checkReadOnly();

        return handlers.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return handlers.retainAll(c);
    }

    @Override
    public void clear() {
        checkReadOnly();

        handlers.clear();
    }

    @Override
    public ProtocolHandler get(int index) {
        return (ProtocolHandler) handlers.get(index);
    }

    @Override
    public ProtocolHandler set(int index, ProtocolHandler element) {
        checkReadOnly();

        return (ProtocolHandler) handlers.set(index, element);
    }

    @Override
    public void add(int index, ProtocolHandler element) {
        checkReadOnly();

        handlers.add(index, element);
    }

    @Override
    public ProtocolHandler remove(int index) {
        checkReadOnly();

        return (ProtocolHandler) handlers.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return handlers.indexOf(o);
    }


    @Override
    public int lastIndexOf(Object o) {
        return handlers.lastIndexOf(o);
    }

    @Override
    public ListIterator<ProtocolHandler> listIterator() {
        return new ProtocolHandlerIterator(handlers.listIterator());
    }

    @Override
    public ListIterator<ProtocolHandler> listIterator(int index) {
        return new ProtocolHandlerIterator(handlers.listIterator(index));
    }

    @Override
    public List<ProtocolHandler> subList(int fromIndex, int toIndex) {
        List<ProtocolHandler> sList = new ArrayList<>(handlers.subList(fromIndex, toIndex));
        if (readyOnly) {
            return ImmutableList.copyOf(sList);
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

        @Override
        public boolean hasNext() {
            return handlers.hasNext();
        }

        @Override
        public ProtocolHandler next() {
            return (ProtocolHandler) handlers.next();
        }

        @Override
        public boolean hasPrevious() {
            return handlers.hasPrevious();
        }

        @Override
        public ProtocolHandler previous() {
            return handlers.previous();
        }

        @Override
        public int nextIndex() {
            return handlers.nextIndex();
        }

        @Override
        public int previousIndex() {
            return handlers.previousIndex();
        }

        @Override
        public void remove() {
            checkReadOnly();

            handlers.previousIndex();
        }

        @Override
        public void set(ProtocolHandler e) {
            checkReadOnly();

            handlers.set(e);
        }

        @Override
        public void add(ProtocolHandler e) {
            checkReadOnly();
            handlers.add(e);
        }

    }
    


}
