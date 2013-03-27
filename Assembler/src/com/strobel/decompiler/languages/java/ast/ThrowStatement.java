/*
 * ThrowStatement.java
 *
 * Copyright (c) 2013 Mike Strobel
 *
 * This source code is subject to terms and conditions of the Apache License, Version 2.0.
 * A copy of the license can be found in the License.html file at the root of this distribution.
 * By using this source code in any fashion, you are agreeing to be bound by the terms of the
 * Apache License, Version 2.0.
 *
 * You must not remove this notice, or any other, from this software.
 */

package com.strobel.decompiler.languages.java.ast;

import com.strobel.decompiler.patterns.INode;
import com.strobel.decompiler.patterns.Match;

public class ThrowStatement extends Statement {
    public final static TokenRole THROW_KEYWORD_ROLE = new TokenRole("throw");

    public ThrowStatement() {
    }

    public ThrowStatement(final Expression expression) {
        setExpression(expression);
    }

    public final JavaTokenNode getThrowToken() {
        return getChildByRole(THROW_KEYWORD_ROLE);
    }

    public final JavaTokenNode getSemicolonToken() {
        return getChildByRole(Roles.SEMICOLON);
    }

    public final Expression getExpression() {
        return getChildByRole(Roles.EXPRESSION);
    }

    public final void setExpression(final Expression value) {
        setChildByRole(Roles.EXPRESSION, value);
    }

    @Override
    public <T, R> R acceptVisitor(final IAstVisitor<? super T, ? extends R> visitor, final T data) {
        return visitor.visitThrowStatement(this, data);
    }

    @Override
    public boolean matches(final INode other, final Match match) {
        return other instanceof ThrowStatement &&
               !other.isNull() &&
               getExpression().matches(((ThrowStatement) other).getExpression(), match);
    }
}
