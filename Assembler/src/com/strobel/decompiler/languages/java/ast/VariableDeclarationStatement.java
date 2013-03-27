/*
 * VariableDeclarationStatement.java
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

import com.strobel.core.Predicate;
import com.strobel.core.StringUtilities;
import com.strobel.decompiler.patterns.INode;
import com.strobel.decompiler.patterns.Match;
import com.strobel.decompiler.patterns.Role;

import javax.lang.model.element.Modifier;
import java.util.List;

public class VariableDeclarationStatement extends Statement {
    public final static Role<JavaModifierToken> MODIFIER_ROLE = EntityDeclaration.MODIFIER_ROLE;

    public VariableDeclarationStatement() {
    }

    public VariableDeclarationStatement(final AstType type, final String name) {
        this(type, name, null);
    }

    public VariableDeclarationStatement(final AstType type, final String name, final Expression initializer) {
        setType(type);
        getVariables().add(new VariableInitializer(name, initializer));
    }

    public final List<Modifier> getModifiers() {
        return EntityDeclaration.getModifiers(this);
    }

    public final void addModifier(final Modifier modifier) {
        EntityDeclaration.addModifier(this, modifier);
    }

    public final void removeModifier(final Modifier modifier) {
        EntityDeclaration.removeModifier(this, modifier);
    }

    public final void setModifiers(final List<Modifier> modifiers) {
        EntityDeclaration.setModifiers(this, modifiers);
    }

    public final AstType getType() {
        return getChildByRole(Roles.TYPE);
    }

    public final void setType(final AstType value) {
        setChildByRole(Roles.TYPE, value);
    }

    public final JavaTokenNode getSemicolonToken() {
        return getChildByRole(Roles.SEMICOLON);
    }

    public final AstNodeCollection<VariableInitializer> getVariables() {
        return getChildrenByRole(Roles.VARIABLE);
    }

    public final VariableInitializer getVariable(final String name) {
        return getVariables().firstOrNullObject(
            new Predicate<VariableInitializer>() {
                @Override
                public boolean test(final VariableInitializer variable) {
                    return StringUtilities.equals(variable.getName(), name);
                }
            }
        );
    }

    @Override
    public <T, R> R acceptVisitor(final IAstVisitor<? super T, ? extends R> visitor, final T data) {
        return visitor.visitVariableDeclaration(this, data);
    }

    @Override
    public boolean matches(final INode other, final Match match) {
        if (other instanceof VariableDeclarationStatement) {
            final VariableDeclarationStatement otherDeclaration = (VariableDeclarationStatement) other;

            return !other.isNull() &&
                   getChildrenByRole(MODIFIER_ROLE).matches(otherDeclaration.getChildrenByRole(MODIFIER_ROLE), match) &&
                   getVariables().matches(otherDeclaration.getVariables(), match);
        }

        return false;
    }
}
