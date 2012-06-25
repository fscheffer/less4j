package org.porting.less4j.core.ast;

import org.antlr.runtime.tree.CommonTree;

public class FunctionExpression extends Expression {

  private String name;
  private Expression parameter;

  public FunctionExpression(CommonTree token, String name, Expression parameter) {
    super(token);
    this.name = name;
    this.parameter = parameter;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Expression getParameter() {
    return parameter;
  }

  public void setParameter(Expression parameter) {
    this.parameter = parameter;
  }

  @Override
  public ASTCssNodeType getType() {
    return ASTCssNodeType.FUNCTION;
  }

}
