/* Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

/* The original from which this derives bore the following: */

/*

   Derby - Class org.apache.derby.impl.sql.compile.IsNode

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

// TODO: I do not know when this is ever instantiated, even in the original Derby.

public class IsNode extends BinaryLogicalOperatorNode
{
  private boolean notMe; // set to true if we're to negate the sense of this node

  /**
   * Initializer for an IsNode
   *
   * @param leftOperand The left operand of the IS
   * @param rightOperand The right operand of the IS
   * @param notMe Whether to reverse the sense of this node.
   */

  public void init(Object leftOperand,
                   Object rightOperand,
                   Object notMe) {
    // the false for shortCir
    super.init(leftOperand, rightOperand, "is");
    this.notMe = ((Boolean)notMe).booleanValue();
  }

  public boolean isNegated() {
    return notMe;
  }

  public void toggleNegated() {
    notMe = !notMe;
  }

}