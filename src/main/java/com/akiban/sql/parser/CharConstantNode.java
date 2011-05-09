/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

/* The original from which this derives bore the following: */

/*

   Derby - Class org.apache.derby.impl.sql.compile.CharConstantNode

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package com.akiban.sql.parser;

import com.akiban.sql.StandardException;
import com.akiban.sql.types.TypeId;

public final class CharConstantNode extends ConstantNode
{
    /**
     * Initializer for a CharConstantNode.
     *
     * @param arg1 A String containing the value of the constant OR The TypeId for the type of the node
     *
     * @exception StandardException
     */
    public void init(Object arg1) throws StandardException {
        if (arg1 instanceof TypeId) {
            super.init(arg1,
                       Boolean.TRUE,
                       0);
        }
        else {
            String val = (String)arg1;

            super.init(TypeId.CHAR_ID,
                       (val == null) ? Boolean.TRUE : Boolean.FALSE,
                       (val != null) ? val.length() : 0);

            setValue(val);
        }
    }

    /**
     * Initializer for a CharConstantNode of a specific length.
     *
     * @param newValue A String containing the value of the constant
     * @param newLength The length of the new value of the constant
     *
     * @exception StandardException
     */
    public void init(Object newValue, Object newLength) throws StandardException {
        String val = (String)newValue;
        int newLen = ((Integer)newLength).intValue();

        super.init(TypeId.CHAR_ID,
                   (val == null) ? Boolean.TRUE : Boolean.FALSE,
                   newLength);

        if (val.length() > newLen) {
            throw new StandardException("Value truncated");
        }

        // Blank pad the string if necessesary
        while (val.length() < newLen) {
            val = val + ' ';
        }

        setValue(val);
    }

    /**
     * Return the value from this CharConstantNode
     *
     * @return The value of this CharConstantNode.
     *
     * @exception StandardException Thrown on error
     */

    public String getString() throws StandardException {
        return (String)value;
    }

    /**
     * Return an Object representing the bind time value of this
     * expression tree.  If the expression tree does not evaluate to
     * a constant at bind time then we return null.
     * This is useful for bind time resolution of VTIs.
     * RESOLVE: What do we do for primitives?
     *
     * @return An Object representing the bind time value of this expression tree.
     *               (null if not a bind time constant.)
     *
     * @exception StandardException Thrown on error
     */
    Object getConstantValueAsObject() throws StandardException {
        return (String)value;
    }

}
