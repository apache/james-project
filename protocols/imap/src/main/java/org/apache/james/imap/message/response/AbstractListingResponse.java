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

package org.apache.james.imap.message.response;

import org.apache.james.imap.api.process.MailboxType;

/**
 * <code>LIST</code> and <code>LSUB</code> return identical data.
 */
public abstract class AbstractListingResponse {

    private final boolean children;

    private final boolean noChildren;

    private final boolean noInferiors;

    private final boolean noSelect;

    private final boolean marked;

    private final boolean unmarked;

    private final char hierarchyDelimiter;

    private final String name;

    private MailboxType type;

    public AbstractListingResponse(final boolean noInferiors, final boolean noSelect, final boolean marked, final boolean unmarked, boolean hasChildren, boolean hasNoChildren, final String name, final char hierarchyDelimiter, final MailboxType type) {
        super();
        this.noInferiors = noInferiors;
        this.noSelect = noSelect;
        this.marked = marked;
        this.unmarked = unmarked;
        this.children = hasChildren;
        this.noChildren = hasNoChildren;
        this.name = name;
        this.hierarchyDelimiter = hierarchyDelimiter;
        this.type = type;
    }

    /**
     * Gets hierarchy delimiter.
     * 
     * @return hierarchy delimiter, or null if no hierarchy exists
     */
    public final char getHierarchyDelimiter() {
        return hierarchyDelimiter;
    }

    /**
     * Is <code>Marked</code> name attribute set?
     * 
     * @return true if <code>Marked</code>, false otherwise
     */
    public final boolean isMarked() {
        return marked;
    }

    /**
     * Gets the listed name.
     * 
     * @return name of the listed mailbox, not null
     */
    public final String getName() {
        return name;
    }

    /**
     * Is <code>Noinferiors</code> name attribute set?
     * 
     * @return true if <code>Noinferiors</code>, false otherwise
     */
    public final boolean isNoInferiors() {
        return noInferiors;
    }

    /**
     * Is <code>Noselect</code> name attribute set?
     * 
     * @return true if <code>Noselect</code>, false otherwise
     */
    public final boolean isNoSelect() {
        return noSelect;
    }

    /**
     * Is <code>Unmarked</code> name attribute set?
     * 
     * @return true if <code>Unmarked</code>, false otherwise
     */
    public final boolean isUnmarked() {
        return unmarked;
    }

    /**
     * Is the <code>HasNoChildren</code> name attribute set?
     * 
     * @return true if <code>HasNoChildren</code>, false otherwise
     */
    public boolean hasNoChildren() {
        return noChildren;
    }

    /**
     * Is the <code>HasChildren</code> name attribute set?
     * 
     * @return true if <code>HasChildren</code>, false otherwise
     */
    public boolean hasChildren() {
        return children;
    }

    /**
     * returns type of the mailbox
     * 
     * @return mailbox type
     */
    public MailboxType getType() {
        return type;
    }

    /**
     * Are any name attributes set?
     * 
     * @return true if {@link #isNoInferiors()}, {@link #isNoSelect()},
     *         {@link #isMarked()} or {@link #isUnmarked()}
     */
    public final boolean isNameAttributed() {
        return noInferiors || noSelect || marked || unmarked || children || noChildren || (!MailboxType.OTHER.equals(type));
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + (children ? 1231 : 1237);
        result = PRIME * result + hierarchyDelimiter;
        result = PRIME * result + type.ordinal();
        result = PRIME * result + (marked ? 1231 : 1237);
        result = PRIME * result + ((name == null) ? 0 : name.hashCode());
        result = PRIME * result + (noChildren ? 1231 : 1237);
        result = PRIME * result + (noInferiors ? 1231 : 1237);
        result = PRIME * result + (noSelect ? 1231 : 1237);
        result = PRIME * result + (unmarked ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final AbstractListingResponse other = (AbstractListingResponse) obj;
        if (children != other.children)
            return false;
        if (hierarchyDelimiter != other.hierarchyDelimiter)
            return false;
        if (marked != other.marked)
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (noChildren != other.noChildren)
            return false;
        if (noInferiors != other.noInferiors)
            return false;
        if (noSelect != other.noSelect)
            return false;
        if (unmarked != other.unmarked)
            return false;
        if (!type.equals(other.type))
            return false;
        return true;
    }

    /**
     * Renders object as a string suitable for logging.
     * 
     * @return a <code>String</code> representation of this object.
     */
    public String toString() {
        final String TAB = " ";

        String retValue = getClass().getName() + " ( " + "noInferiors = " + this.noInferiors + TAB + "noSelect = " + this.noSelect + TAB + "marked = " + this.marked + TAB + "unmarked = " + this.unmarked + TAB + "hierarchyDelimiter = " + this.hierarchyDelimiter + TAB + "name = " + this.name + TAB
                + "type = " + this.type + TAB + " )";

        return retValue;
    }

}