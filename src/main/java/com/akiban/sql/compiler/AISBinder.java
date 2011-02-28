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

package com.akiban.sql.compiler;

import com.akiban.sql.parser.*;

import com.akiban.sql.StandardException;
import com.akiban.sql.types.DataTypeDescriptor;
import com.akiban.sql.types.TypeId;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.Column;
import com.akiban.ais.model.Table;
import com.akiban.ais.model.Type;

import java.util.*;

/** Bind objects to Akiban schema. */
public class AISBinder implements Visitor
{
  private AkibanInformationSchema ais;
  private String defaultSchemaName;
  private Stack<BindingContext> bindingContexts;
  private Set<QueryTreeNode> visited;

  public AISBinder(AkibanInformationSchema ais, String defaultSchemaName) {
    this.ais = ais;
    this.defaultSchemaName = defaultSchemaName;
    this.bindingContexts = new Stack<BindingContext>();
  }

  public void bind(StatementNode stmt) throws StandardException {
    visited = new HashSet<QueryTreeNode>();
    stmt.accept(this);
    visited = null;
  }
  
  /* Hierarchical Visitor */

  public boolean visitBefore(QueryTreeNode node) throws StandardException {
    boolean first = visited.add(node);

    if (first) {
      switch (node.getNodeType()) {
      case NodeTypes.SUBQUERY_NODE:
        {
          SubqueryNode subqueryNode = (SubqueryNode)node;
          // The LHS of a subquery operator is bound in the outer context.
          if (subqueryNode.getLeftOperand() != null)
            subqueryNode.getLeftOperand().accept(this);
        }
        break;
      case NodeTypes.SELECT_NODE:
        selectNode((SelectNode)node);
        break;
      case NodeTypes.COLUMN_REFERENCE:
        columnReference((ColumnReference)node);
        break;
      }
    }

    switch (node.getNodeType()) {
    case NodeTypes.CURSOR_NODE:
    case NodeTypes.FROM_SUBQUERY:
    case NodeTypes.SUBQUERY_NODE:
      pushBindingContext();
    }

    return first;
  }

  public void visitAfter(QueryTreeNode node) throws StandardException {
    switch (node.getNodeType()) {
    case NodeTypes.CURSOR_NODE:
    case NodeTypes.FROM_SUBQUERY:
    case NodeTypes.SUBQUERY_NODE:
      popBindingContext();
      break;
    }
  }

  /* Specific node types */

  protected void selectNode(SelectNode selectNode) throws StandardException {
    FromList fromList = selectNode.getFromList();
    // Subqueries in SELECT don't see earlier FROM list tables.
    fromList.accept(this);
    BindingContext bindingContext = getBindingContext();
    for (FromTable fromTable : fromList) {
      switch (fromTable.getNodeType()) {
      case NodeTypes.FROM_BASE_TABLE:
        fromBaseTable((FromBaseTable)fromTable);
        break;
      }
    }
    for (FromTable fromTable : fromList) {
      bindingContext.tables.add(fromTable);
      if (fromTable.getCorrelationName() != null) {
        if (bindingContext.correlationNames.put(fromTable.getCorrelationName(), 
                                                fromTable) != null) {
          throw new StandardException("More than one use of " + 
                                      fromTable.getCorrelationName() +
                                      " as correlation name");
        }
      }
    }
    expandAllsAndNameColumns(selectNode.getResultColumns(), fromList);
  }

  protected void fromBaseTable(FromBaseTable fromBaseTable) throws StandardException {
    TableName tableName = fromBaseTable.getTableName();
    Table table = lookupTableName(tableName);
    tableName.setUserData(table);
    // TODO: Some higher level object on the fromBaseTable.
  }
  
  protected void columnReference(ColumnReference columnReference) 
      throws StandardException {
    ColumnBinding columnBinding = (ColumnBinding)columnReference.getUserData();
    if (columnBinding != null)
      return;

    String columnName = columnReference.getColumnName();
    if (columnReference.getTableNameNode() != null) {
      FromTable fromTable = findFromTable(columnReference.getTableNameNode());
      columnBinding = getColumnBinding(fromTable, columnName);
      if (columnBinding == null)
        throw new StandardException("Column " + columnName +
                                    " not found in " + fromTable.getExposedName());
    }
    else {
      for (BindingContext bindingContext : bindingContexts) {
        for (FromTable fromTable : bindingContext.tables) {
          ColumnBinding aColumnBinding = getColumnBinding(fromTable, columnName);
          if (aColumnBinding != null) {
            if (columnBinding != null)
              throw new StandardException("Column " + columnName + " is ambiguous");
            else
              columnBinding = aColumnBinding;
          }
        }
      }
      if (columnBinding == null)
        throw new StandardException("Column " + columnName + " not found");
    }
    columnReference.setUserData(columnBinding);
  }

  protected Table lookupTableName(TableName tableName)
      throws StandardException {
    String schemaName = tableName.getSchemaName();
    if (schemaName == null)
      schemaName = defaultSchemaName;
    Table result = ais.getUserTable(schemaName, 
                                    // TODO: Akiban DB thinks it's case sensitive.
                                    tableName.getTableName().toLowerCase());
    if (result == null)
      throw new StandardException("Table " + tableName.getFullTableName() +
                                  " not found");
    return result;
  }

  protected FromTable findFromTable(TableName tableNameNode) throws StandardException {
    String schemaName = tableNameNode.getSchemaName();
    String tableName = tableNameNode.getTableName();
    if (schemaName == null) {
      FromTable fromTable = getBindingContext().correlationNames.get(tableName);
      if (fromTable != null)
        return fromTable;

      schemaName = defaultSchemaName;
    }
    FromTable result = null;
    for (BindingContext bindingContext : bindingContexts) {
      for (FromTable fromTable : bindingContext.tables) {
        if ((fromTable instanceof FromBaseTable) &&
            // Not allowed to reference correlated by underlying name.
            (fromTable.getCorrelationName() == null)) {
          FromBaseTable fromBaseTable = (FromBaseTable)fromTable;
          Table table = (Table)fromBaseTable.getTableName().getUserData();
          assert (table != null) : "table not bound yet";
          if (table.getName().getSchemaName().equalsIgnoreCase(schemaName) &&
              table.getName().getTableName().equalsIgnoreCase(tableName)) {
            if (result != null)
              throw new StandardException("Ambiguous table " + tableName);
            else
              result = fromBaseTable;
          }
        }
      }
    }
    if (result == null)
      throw new StandardException("Table " + tableNameNode + " not found");
    return result;
  }

  protected ColumnBinding getColumnBinding(FromTable fromTable, String columnName)
      throws StandardException {
    if (fromTable instanceof FromBaseTable) {
      FromBaseTable fromBaseTable = (FromBaseTable)fromTable;
      Table table = (Table)fromBaseTable.getTableName().getUserData();
      assert (table != null) : "table not bound yet";
      Column column = table.getColumn(columnName);
      if (column == null)
        return null;
      return new ColumnBinding(fromTable, column);
    }
    else if (fromTable instanceof FromSubquery) {
      FromSubquery fromSubquery = (FromSubquery)fromTable;
      ResultColumn resultColumn = fromSubquery.getSubquery().getResultColumns()
        .getResultColumn(columnName);
      if (resultColumn == null)
        return null;
      return new ColumnBinding(fromTable, resultColumn);
    }
    else {
      assert false;
      return null;
    }
  }

  /**
   * Expand any *'s in the ResultColumnList.  In addition, we will guarantee that
   * each ResultColumn has a name.  (All generated names will be unique across the
   * entire statement.)
   *
   * @exception StandardException               Thrown on error
   */
  public void expandAllsAndNameColumns(ResultColumnList rcl, FromList fromList) 
      throws StandardException {
    boolean expanded = false;
    ResultColumnList allExpansion;
    TableName fullTableName;

    for (int index = 0; index < rcl.size(); index++) {
      ResultColumn rc = rcl.get(index);
      if (rc instanceof AllResultColumn) {
        expanded = true;

        fullTableName = rc.getTableNameObject();
        allExpansion = expandAll(fullTableName, fromList);

        // Make sure that every column has a name.
        for (ResultColumn nrc : allExpansion) {
          guaranteeColumnName(nrc);
        }

        // Replace the AllResultColumn with the expanded list.
        rcl.remove(index);
        for (int inner = 0; inner < allExpansion.size(); inner++) {
          rcl.add(index + inner, allExpansion.get(inner));
        }
        index += allExpansion.size() - 1;

        // TODO: This is where Derby remembered the original size in
        // case other things get added to the RCL.
      }
      else {
        // Make sure that every column has a name.
        guaranteeColumnName(rc);
      }
    }
  }

  /**
   * Generate a unique (across the entire statement) column name for unnamed
   * ResultColumns
   *
   * @exception StandardException		Thrown on error
   */
  protected void guaranteeColumnName(ResultColumn rc) throws StandardException {
    if (rc.getName() == null) {
      rc.setName(((SQLParser)rc.getParserContext()).generateColumnName());
      rc.setNameGenerated(true);
    }
  }

  /**
   * Expand a "*" into the appropriate ResultColumnList. If the "*"
   * is unqualified it will expand into a list of all columns in all
   * of the base tables in the from list at the current nesting level;
   * otherwise it will expand into a list of all of the columns in the
   * base table that matches the qualification.
   * 
   * @param allTableName The qualification on the "*" as a String
   * @param fromList The select list
   *
   * @return ResultColumnList representing expansion
   *
   * @exception StandardException Thrown on error
   */
  protected ResultColumnList expandAll(TableName allTableName, FromList fromList)
      throws StandardException {
    ResultColumnList resultColumnList = null;
    ResultColumnList tempRCList = null;

    for (FromTable fromTable : fromList) {
      tempRCList = getAllResultColumns(allTableName, fromTable);

      if (tempRCList == null)
        continue;

      /* Expand the column list and append to the list that
       * we will return.
       */
      if (resultColumnList == null)
        resultColumnList = tempRCList;
      else
        resultColumnList.addAll(tempRCList);

      // If the "*" is qualified, then we can stop the expansion as
      // soon as we find the matching table.
      if (allTableName != null)
        break;
    }

    // Give an error if the qualification name did not match an exposed name.
    if (resultColumnList == null) {
      throw new StandardException("Table not found: " + allTableName);
    }

    return resultColumnList;
  }

  protected ResultColumnList getAllResultColumns(TableName allTableName, 
                                                 FromTable fromTable)
      throws StandardException {
    switch (fromTable.getNodeType()) {
    case NodeTypes.FROM_BASE_TABLE:
      return getAllResultColumns(allTableName, (FromBaseTable)fromTable);
    default:
      return null;
    }
  }

  protected ResultColumnList getAllResultColumns(TableName allTableName, 
                                                 FromBaseTable fromTable)
      throws StandardException {
    TableName exposedName = fromTable.getExposedTableName();
    if ((allTableName != null) && !allTableName.equals(exposedName))
      return null;

    NodeFactory nodeFactory = fromTable.getNodeFactory();
    SQLParserContext parserContext = fromTable.getParserContext();
    ResultColumnList rcList = (ResultColumnList)
      nodeFactory.getNode(NodeTypes.RESULT_COLUMN_LIST,
                          parserContext);
    Table table = (Table)fromTable.getTableName().getUserData();
    for (Column column : table.getColumns()) {
      String columnName = column.getName().toUpperCase();
      ValueNode valueNode = (ValueNode)
        nodeFactory.getNode(NodeTypes.COLUMN_REFERENCE,
                            columnName,
                            exposedName,
                            parserContext);
      ResultColumn resultColumn = (ResultColumn)
        nodeFactory.getNode(NodeTypes.RESULT_COLUMN,
                            columnName,
                            valueNode,
                            parserContext);
      rcList.addResultColumn(resultColumn);
      // Easy to do binding right here.
      valueNode.setUserData(new ColumnBinding(fromTable, column));
    }
    return rcList;
  }

  protected static class BindingContext {
    Collection<FromTable> tables = new ArrayList<FromTable>();
    Map<String,FromTable> correlationNames = new HashMap<String,FromTable>();
  }

  protected BindingContext getBindingContext() {
    return bindingContexts.peek();
  }
  protected void pushBindingContext() {
    BindingContext next = new BindingContext();
    if (!bindingContexts.empty()) {
      next.correlationNames.putAll(bindingContexts.peek().correlationNames);
    }
    bindingContexts.push(next);
  }
  protected void popBindingContext() {
    bindingContexts.pop();
  }

  /* Visitor interface.
     This is messy. Perhaps there should be an abstract class which makes the common
     Visitor interface into a Hierarchical Vistor pattern. 
  */

  // To understand why this works, see QueryTreeNode.accept().
  public Visitable visit(Visitable node) throws StandardException {
    visitAfter((QueryTreeNode)node);
    return node;
  }

  public boolean skipChildren(Visitable node) throws StandardException {
    return ! visitBefore((QueryTreeNode)node);
  }

  public boolean visitChildrenFirst(Visitable node) {
    return true;
  }
  public boolean stopTraversal() {
    return false;
  }

}