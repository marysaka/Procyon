/*
 * VariableDefinitionCollection.java
 *
 * Copyright (c) 2013 Mike Strobel
 *
 * This source code is based on Mono.Cecil from Jb Evain, Copyright (c) Jb Evain;
 * and ILSpy/ICSharpCode from SharpDevelop, Copyright (c) AlphaSierraPapa.
 *
 * This source code is subject to terms and conditions of the Apache License, Version 2.0.
 * A copy of the license can be found in the License.html file at the root of this distribution.
 * By using this source code in any fashion, you are agreeing to be bound by the terms of the
 * Apache License, Version 2.0.
 *
 * You must not remove this notice, or any other, from this software.
 */

package com.strobel.assembler.metadata;

import com.strobel.assembler.Collection;
import com.strobel.assembler.ir.OpCode;
import com.strobel.core.StringUtilities;
import com.strobel.core.VerifyArgument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.NoSuchElementException;

public final class VariableDefinitionCollection extends Collection<VariableDefinition> {
    private final MethodDefinition _declaringMethod;

    public VariableDefinitionCollection(final MethodDefinition declaringMethod) {
        _declaringMethod = VerifyArgument.notNull(declaringMethod, "declaringMethod");
    }

    public int slotCount() {
        int count = 0;

        for (int i = 0; i < size(); i++) {
            count += get(i).getSize();
        }

        return count;
    }

    public VariableDefinition tryFind(final int slot) {
        return find(slot, -1);
    }

    public VariableDefinition tryFind(final int slot, final int instructionOffset) {
        VariableDefinition result = null;

        for (int i = 0; i < size(); i++) {
            final VariableDefinition variable = get(i);

            if (variable.getSlot() == slot &&
                (instructionOffset < 0 ||
                 variable.getScopeStart() <= instructionOffset &&
                 (variable.getScopeEnd() < 0 || variable.getScopeEnd() > instructionOffset)) &&
                (result == null || variable.getScopeStart() > result.getScopeStart())) {

                result = variable;
            }
        }

        return result;
    }

    public VariableDefinition find(final int slot) {
        return find(slot, -1);
    }

    public VariableDefinition find(final int slot, final int instructionOffset) {
        final VariableDefinition variable = tryFind(slot, instructionOffset);

        if (variable != null) {
            return variable;
        }

        throw new NoSuchElementException(
            String.format(
                "Could not find varible at slot %d and offset %d.",
                slot,
                instructionOffset
            )
        );
    }

    public VariableDefinition ensure(final int slot, final OpCode op, final int instructionOffset) {
        final TypeReference variableType;

        switch (op) {
            case ISTORE:
            case ISTORE_0:
            case ISTORE_1:
            case ISTORE_2:
            case ISTORE_3:
            case ISTORE_W:
            case ILOAD:
            case ILOAD_0:
            case ILOAD_1:
            case ILOAD_2:
            case ILOAD_3:
            case ILOAD_W:
                variableType = BuiltinTypes.Integer;
                break;

            case LSTORE:
            case LSTORE_0:
            case LSTORE_1:
            case LSTORE_2:
            case LSTORE_3:
            case LSTORE_W:
            case LLOAD:
            case LLOAD_0:
            case LLOAD_1:
            case LLOAD_2:
            case LLOAD_3:
            case LLOAD_W:
                variableType = BuiltinTypes.Long;
                break;

            case FSTORE:
            case FSTORE_0:
            case FSTORE_1:
            case FSTORE_2:
            case FSTORE_3:
            case FSTORE_W:
            case FLOAD:
            case FLOAD_0:
            case FLOAD_1:
            case FLOAD_2:
            case FLOAD_3:
            case FLOAD_W:
                variableType = BuiltinTypes.Float;
                break;

            case DSTORE:
            case DSTORE_0:
            case DSTORE_1:
            case DSTORE_2:
            case DSTORE_3:
            case DSTORE_W:
            case DLOAD:
            case DLOAD_0:
            case DLOAD_1:
            case DLOAD_2:
            case DLOAD_3:
            case DLOAD_W:
                variableType = BuiltinTypes.Double;
                break;

            case IINC:
            case IINC_W:
                variableType = BuiltinTypes.Integer;
                break;

            default:
                variableType = BuiltinTypes.Object;
                break;
        }

        final int effectiveOffset;

        if (op.isStore()) {
            effectiveOffset = instructionOffset + op.getSize() + op.getOperandType().getBaseSize();
        }
        else {
            effectiveOffset = instructionOffset;
        }

        VariableDefinition variable = tryFind(slot, effectiveOffset);

        if (variable != null) {
            final TypeReference targetType = op.isStore() ? variable.getVariableType() : variableType;
            final TypeReference sourceType = op.isStore() ? variableType : variable.getVariableType();

            if (variableType == BuiltinTypes.Object && !variable.getVariableType().getSimpleType().isPrimitive() ||
                isTargetTypeCompatible(targetType, sourceType)) {

                return variable;
            }

            variable.setScopeEnd(instructionOffset - 1);
        }

        variable = new VariableDefinition(
            slot,
            String.format("$%d_%d$", slot, effectiveOffset),
            _declaringMethod
        );

        variable.setVariableType(variableType);
        variable.setScopeStart(effectiveOffset);
        variable.setScopeEnd(-1);
        variable.setFromMetadata(false);

        if (variableType != BuiltinTypes.Object) {
            variable.setTypeKnown(true);
        }

        add(variable);

        updateScopes(-1);
        return variable;
    }

    public void updateScopes(final int codeSize) {
        boolean modified;

        do {
            modified = false;

            for (int i = 0; i < size(); i++) {
                final VariableDefinition variable = get(i);

                for (int j = 0; j < size(); j++) {
                    if (i == j) {
                        continue;
                    }

                    final VariableDefinition other = get(j);

                    if (variable.getSlot() == other.getSlot() &&
                        variable.getScopeEnd() < 0 &&
                        variable.getScopeStart() < other.getScopeStart()) {

                        variable.setScopeEnd(other.getScopeStart());
                        modified = true;
                    }
                }
            }
        }
        while (modified);

        for (int i = 0; i < size(); i++) {
            final VariableDefinition variable = get(i);

            if (variable.getScopeEnd() < 0) {
                variable.setScopeEnd(codeSize);
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    public final void mergeVariables() {
        final ArrayList<VariableDefinition> slotSharers = new ArrayList<>();
        final ArrayList<VariableDefinition> sortedVariables = new ArrayList<>(this);

        Collections.sort(
            sortedVariables,
            new Comparator<VariableDefinition>() {
                @Override
                public int compare(final VariableDefinition o1, final VariableDefinition o2) {
                    return Integer.compare(o1.getScopeStart(), o2.getScopeStart());
                }
            }
        );

    outer:
        for (int i = 0; i < sortedVariables.size(); i++) {
            final VariableDefinition variable = sortedVariables.get(i);
            final TypeReference variableType = variable.getVariableType();

            for (int j = i + 1; j < sortedVariables.size(); j++) {
                final VariableDefinition other;

                if (variable.getSlot() == (other = sortedVariables.get(j)).getSlot()) {
                    if (StringUtilities.equals(other.getName(), variable.getName())) {
                        slotSharers.add(other);
                    }
                    else {
                        break;
                    }
                }
            }

            boolean merged = false;
            int minScopeStart = variable.getScopeStart();
            int maxScopeEnd = variable.getScopeEnd();

            for (int j = 0; j < slotSharers.size(); j++) {
                final VariableDefinition slotSharer = slotSharers.get(j);

                if (slotSharer.isFromMetadata() &&
                    !StringUtilities.equals(slotSharer.getName(), variable.getName())) {

                    continue;
                }

                final TypeReference slotSharerType = slotSharer.getVariableType();

                if (isTargetTypeCompatible(variableType, slotSharerType) ||
                    isTargetTypeCompatible(slotSharerType, variableType)) {

                    if (slotSharer.getScopeStart() < minScopeStart) {
                        merged = true;
                        minScopeStart = slotSharer.getScopeStart();
                    }

                    if (slotSharer.getScopeEnd() > maxScopeEnd) {
                        merged = true;
                        maxScopeEnd = slotSharer.getScopeEnd();
                    }

                    if (merged) {
                        remove(slotSharer);
                        sortedVariables.remove(slotSharer);
                    }

                    if (variable.isFromMetadata()) {
                        continue;
                    }

                    if (!isTargetTypeCompatible(variableType, slotSharerType)) {
                        variable.setVariableType(slotSharerType);
                    }
                }
            }

            if (merged) {
                variable.setScopeStart(minScopeStart);
                variable.setScopeEnd(maxScopeEnd);
            }
        }
    }

    private boolean isTargetTypeCompatible(final TypeReference targetType, final TypeReference sourceType) {
        //noinspection SimplifiableIfStatement
        if (sourceType.isPrimitive() || targetType.isPrimitive()) {
            if (sourceType.isPrimitive() != targetType.isPrimitive()) {
                return false;
            }
        }

        if (MetadataHelper.isAssignableFrom(targetType, sourceType)) {
            return true;
        }

        final JvmType simpleTarget = targetType.getSimpleType();
        final JvmType simpleSource = sourceType.getSimpleType();

        if (simpleTarget.isIntegral()) {
            if (simpleSource == JvmType.Integer) {
                return true;
            }
            return simpleSource.isIntegral() &&
                   simpleSource.bitWidth() <= simpleTarget.bitWidth();
        }

        if (simpleTarget.isFloating()) {
            return simpleSource.isFloating() &&
                   simpleSource.bitWidth() <= simpleTarget.bitWidth();
        }

        return false;
    }
}