/*
 * MethodPrinter.java
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

import com.strobel.assembler.DisassemblerOptions;
import com.strobel.assembler.ir.ErrorOperand;
import com.strobel.assembler.ir.ExceptionHandler;
import com.strobel.assembler.ir.Frame;
import com.strobel.assembler.ir.Instruction;
import com.strobel.assembler.ir.InstructionCollection;
import com.strobel.assembler.ir.InstructionVisitor;
import com.strobel.assembler.ir.OpCode;
import com.strobel.assembler.ir.OpCodeHelpers;
import com.strobel.assembler.ir.StackMapFrame;
import com.strobel.assembler.ir.attributes.AttributeNames;
import com.strobel.assembler.ir.attributes.ExceptionsAttribute;
import com.strobel.assembler.ir.attributes.LocalVariableTableAttribute;
import com.strobel.assembler.ir.attributes.LocalVariableTableEntry;
import com.strobel.assembler.ir.attributes.SignatureAttribute;
import com.strobel.assembler.ir.attributes.SourceAttribute;
import com.strobel.assembler.metadata.annotations.CustomAnnotation;
import com.strobel.core.StringUtilities;
import com.strobel.core.VerifyArgument;
import com.strobel.decompiler.DecompilerHelpers;
import com.strobel.decompiler.ITextOutput;
import com.strobel.decompiler.NameSyntax;
import com.strobel.decompiler.PlainTextOutput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static java.lang.String.format;

public class MethodPrinter implements MethodVisitor {
    private final ITextOutput _output;
    private final long _flags;
    private final String _name;
    private final IMethodSignature _signature;
    private final TypeReference[] _thrownTypes;
    private final DisassemblerOptions _options;

    private MethodBody _body;
    private int[] _lineNumbers;

    public MethodPrinter(
        final ITextOutput printer,
        final DisassemblerOptions options,
        final long flags,
        final String name,
        final IMethodSignature signature,
        final TypeReference... thrownTypes) {

        _output = VerifyArgument.notNull(printer, "printer");
        _options = options;
        _flags = flags;
        _name = VerifyArgument.notNull(name, "name");
        _signature = signature;
        _thrownTypes = thrownTypes;

        printDescription();
    }

    private void printDescription() {
        final List<String> flagStrings = new ArrayList<>();

        if ("<clinit>".equals(_name)) {
            _output.writeKeyword("static");
            _output.write(" {}");
        }
        else {
            final EnumSet<Flags.Flag> flagSet = Flags.asFlagSet(_flags & Flags.MethodFlags);

            for (final Flags.Flag flag : flagSet) {
                flagStrings.add(flag.toString());
            }

            if (flagSet.size() > 0) {
                for (int i = 0; i < flagStrings.size(); i++) {
                    _output.writeKeyword(flagStrings.get(i));
                    _output.write(' ');
                }
            }

            final List<GenericParameter> genericParameters = _signature.getGenericParameters();

            if (!genericParameters.isEmpty()) {
                _output.writeDelimiter("<");

                for (int i = 0; i < genericParameters.size(); i++) {
                    if (i != 0) {
                        _output.writeDelimiter(", ");
                    }

                    DecompilerHelpers.writeType(_output, genericParameters.get(i), NameSyntax.TYPE_NAME, true);
                }

                _output.writeDelimiter(">");
                _output.write(' ');
            }

            DecompilerHelpers.writeType(_output, _signature.getReturnType(), NameSyntax.TYPE_NAME, false);

            _output.write(' ');
            _output.writeReference(_name, _signature);
            _output.writeDelimiter("(");

            final List<ParameterDefinition> parameters = _signature.getParameters();

            for (int i = 0; i < parameters.size(); i++) {
                if (i != 0) {
                    _output.writeDelimiter(", ");
                }

                final ParameterDefinition parameter = parameters.get(i);

                if (Flags.testAny(_flags, Flags.ACC_VARARGS) && i == parameters.size() - 1) {
                    DecompilerHelpers.writeType(_output, parameter.getParameterType().getUnderlyingType(), NameSyntax.TYPE_NAME, false);
                    _output.writeDelimiter("...");
                }
                else {
                    DecompilerHelpers.writeType(_output, parameter.getParameterType(), NameSyntax.TYPE_NAME, false);
                }

                _output.write(' ');

                final String parameterName = parameter.getName();

                if (StringUtilities.isNullOrEmpty(parameterName)) {
                    _output.write("p%d", i);
                }
                else {
                    _output.write(parameterName);
                }
            }

            _output.writeDelimiter(")");

            if (_thrownTypes != null && _thrownTypes.length > 0) {
                _output.writeKeyword(" throws ");

                for (int i = 0; i < _thrownTypes.length; i++) {
                    if (i != 0) {
                        _output.writeDelimiter(", ");
                    }

                    DecompilerHelpers.writeType(_output, _thrownTypes[i], NameSyntax.TYPE_NAME);
                }
            }
        }

        _output.writeDelimiter(";");
        _output.writeLine();

        flagStrings.clear();

        for (final Flags.Flag flag : Flags.asFlagSet(_flags & (Flags.MethodFlags | ~Flags.StandardFlags))) {
            flagStrings.add(flag.name());
        }

        if (flagStrings.isEmpty()) {
            return;
        }

        _output.indent();

        try {
            _output.writeAttribute("Flags");
            _output.write(": ");

            for (int i = 0; i < flagStrings.size(); i++) {
                if (i != 0) {
                    _output.write(", ");
                }

                _output.writeLiteral(flagStrings.get(i));
            }

            _output.writeLine();
        }
        finally {
            _output.unindent();
        }
    }

    @Override
    public boolean canVisitBody() {
        return true;
    }

    @Override
    public InstructionVisitor visitBody(final MethodBody body) {
        _body = body;

        _output.indent();

        try {
            _output.writeAttribute("Code");
            _output.writeLine(":");

            _output.indent();

            try {
                _output.write("stack=");
                _output.writeLiteral(body.getMaxStackSize());
                _output.write(", locals=");
                _output.writeLiteral(body.getMaxLocals());
                _output.write(", arguments=");
                _output.writeLiteral(_signature.getParameters().size());
                _output.writeLine();
            }
            finally {
                _output.unindent();
            }

            final InstructionCollection instructions = body.getInstructions();

            if (!instructions.isEmpty()) {
                _lineNumbers = new int[body.getCodeSize()];
                Arrays.fill(_lineNumbers, -1);
            }
        }
        finally {
            _output.unindent();
        }

        return new InstructionPrinter();
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public void visitEnd() {
        if (_body == null) {
            return;
        }

        final List<ExceptionHandler> handlers = _body.getExceptionHandlers();
        final List<StackMapFrame> stackMapFrames = _body.getStackMapFrames();

        if (!handlers.isEmpty()) {
            _output.indent();

            try {
                int longestType = "Type".length();

                for (final ExceptionHandler handler : handlers) {
                    final TypeReference catchType = handler.getCatchType();

                    if (catchType != null) {
                        final String signature = catchType.getSignature();

                        if (signature.length() > longestType) {
                            longestType = signature.length();
                        }
                    }
                }

                _output.writeAttribute("Exceptions");
                _output.writeLine(":");

                _output.indent();

                try {
                    _output.write("Try           Handler");
                    _output.writeLine();
                    _output.write("Start  End    Start  End    %1$-" + longestType + "s", "Type");
                    _output.writeLine();

                    _output.write(
                        "-----  -----  -----  -----  %1$-" + longestType + "s",
                        StringUtilities.repeat('-', longestType)
                    );

                    _output.writeLine();

                    for (final ExceptionHandler handler : handlers) {
                        final boolean isFinally;

                        TypeReference catchType = handler.getCatchType();

                        if (catchType != null) {
                            isFinally = false;
                        }
                        else {
                            catchType = MetadataSystem.instance().lookupType("java/lang/Throwable");
                            isFinally = true;
                        }

                        _output.writeLiteral(format("%1$-5d", handler.getTryBlock().getFirstInstruction().getOffset()));
                        _output.write("  ");
                        _output.writeLiteral(format("%1$-5d", handler.getTryBlock().getLastInstruction().getEndOffset()));
                        _output.write("  ");
                        _output.writeLiteral(format("%1$-5d", handler.getHandlerBlock().getFirstInstruction().getOffset()));
                        _output.write("  ");
                        _output.writeLiteral(format("%1$-5d", handler.getHandlerBlock().getLastInstruction().getEndOffset()));
                        _output.write("  ");

                        if (isFinally) {
                            _output.writeReference("Any", catchType);
                        }
                        else {
                            DecompilerHelpers.writeType(_output, catchType, NameSyntax.SIGNATURE);
                        }

                        _output.writeLine();
                    }
                }
                finally {
                    _output.unindent();
                }
            }
            finally {
                _output.unindent();
            }
        }

        if (!stackMapFrames.isEmpty()) {
            _output.indent();

            try {
                _output.writeAttribute("Stack Map Frames");
                _output.writeLine(":");

                _output.indent();

                try {
                    for (final StackMapFrame frame : stackMapFrames) {
                        DecompilerHelpers.writeOffsetReference(_output, frame.getStartInstruction());
                        _output.write(' ');
                        DecompilerHelpers.writeFrame(_output, frame.getFrame());
                        _output.writeLine();
                    }
                }
                finally {
                    _output.unindent();
                }
            }
            finally {
                _output.unindent();
            }
        }
    }

    @Override
    public void visitFrame(final Frame frame) {
    }

    @Override
    public void visitLineNumber(final Instruction instruction, final int lineNumber) {
        if (_options != null &&
            _options.getPrintLineNumbers() &&
            _lineNumbers != null &&
            lineNumber >= 0) {

            _lineNumbers[instruction.getOffset()] = lineNumber;
        }
    }

    @Override
    public void visitAttribute(final SourceAttribute attribute) {
        switch (attribute.getName()) {
            case AttributeNames.Exceptions: {
                final ExceptionsAttribute exceptionsAttribute = (ExceptionsAttribute) attribute;
                final List<TypeReference> exceptionTypes = exceptionsAttribute.getExceptionTypes();

                if (!exceptionTypes.isEmpty()) {
                    _output.indent();

                    try {
                        _output.writeAttribute("Exceptions");
                        _output.writeLine(":");

                        _output.indent();

                        try {
                            for (final TypeReference exceptionType : exceptionTypes) {
                                _output.writeKeyword("throws");
                                _output.write(' ');
                                DecompilerHelpers.writeType(_output, exceptionType, NameSyntax.TYPE_NAME);
                                _output.writeLine();
                            }
                        }
                        finally {
                            _output.unindent();
                        }
                    }
                    finally {
                        _output.unindent();
                    }
                }

                break;
            }

            case AttributeNames.LocalVariableTable:
            case AttributeNames.LocalVariableTypeTable: {
                final LocalVariableTableAttribute localVariables = (LocalVariableTableAttribute) attribute;
                final List<LocalVariableTableEntry> entries = localVariables.getEntries();

                int longestName = "Name".length();
                int longestSignature = "Signature".length();

                for (final LocalVariableTableEntry entry : entries) {
                    final String name = entry.getName();
                    final String signature;
                    final TypeReference type = entry.getType();

                    if (type != null) {
                        if (attribute.getName().equals(AttributeNames.LocalVariableTypeTable)) {
                            signature = type.getSignature();
                        }
                        else {
                            signature = type.getErasedSignature();
                        }

                        if (signature.length() > longestSignature) {
                            longestSignature = signature.length();
                        }
                    }

                    if (name != null && name.length() > longestName) {
                        longestName = name.length();
                    }
                }

                _output.indent();

                try {
                    _output.writeAttribute(attribute.getName());
                    _output.writeLine(":");

                    _output.indent();

                    try {
                        _output.write("Start  Length  Slot  %1$-" + longestName + "s  Signature", "Name");
                        _output.writeLine();

                        _output.write(
                            "-----  ------  ----  %1$-" + longestName + "s  %2$-" + longestSignature + "s",
                            StringUtilities.repeat('-', longestName),
                            StringUtilities.repeat('-', longestSignature)
                        );

                        _output.writeLine();

                        for (final LocalVariableTableEntry entry : entries) {
                            final NameSyntax nameSyntax;

                            if (attribute.getName().equals(AttributeNames.LocalVariableTypeTable)) {
                                nameSyntax = NameSyntax.SIGNATURE;
                            }
                            else {
                                nameSyntax = NameSyntax.ERASED_SIGNATURE;
                            }

                            _output.writeLiteral(format("%1$-5d", entry.getScopeOffset()));
                            _output.write("  ");
                            _output.writeLiteral(format("%1$-6d", entry.getScopeLength()));
                            _output.write("  ");
                            _output.writeLiteral(format("%1$-4d", entry.getIndex()));

                            _output.writeReference(
                                String.format("  %1$-" + longestName + "s  ", entry.getName()),
                                _body.getVariables().tryFind(entry.getIndex(), entry.getScopeOffset())
                            );

                            DecompilerHelpers.writeType(_output, entry.getType(), nameSyntax);

                            _output.writeLine();
                        }
                    }
                    finally {
                        _output.unindent();
                    }
                }
                finally {
                    _output.unindent();
                }

                break;
            }

            case AttributeNames.Signature: {
                _output.indent();

                try {
                    final String signature = ((SignatureAttribute) attribute).getSignature();

                    _output.writeAttribute(attribute.getName());
                    _output.writeLine(":");
                    _output.indent();

                    final PlainTextOutput temp = new PlainTextOutput();

                    DecompilerHelpers.writeMethodSignature(temp, _signature);

                    if (StringUtilities.equals(temp.toString(), signature)) {
                        DecompilerHelpers.writeMethodSignature(_output, _signature);
                    }
                    else {
                        _output.writeTextLiteral(signature);
                    }

                    _output.writeLine();
                    _output.unindent();
                }
                finally {
                    _output.unindent();
                }

                break;
            }
        }
    }

    @Override
    public void visitAnnotation(final CustomAnnotation annotation, final boolean visible) {
    }

    @Override
    public void visitParameterAnnotation(final int parameter, final CustomAnnotation annotation, final boolean visible) {
    }

    // <editor-fold defaultstate="collapsed" desc="InstructionPrinter Class">

    private static final int MAX_OPCODE_LENGTH;
    private static final String[] OPCODE_NAMES;
    private static final String LINE_NUMBER_CODE = "linenumber";

    static {
        int maxLength = LINE_NUMBER_CODE.length();

        final OpCode[] values = OpCode.values();
        final String[] names = new String[values.length];

        for (int i = 0; i < values.length; i++) {
            final OpCode op = values[i];
            final int length = op.name().length();

            if (length > maxLength) {
                maxLength = length;
            }

            names[i] = op.name().toLowerCase();
        }

        MAX_OPCODE_LENGTH = maxLength;
        OPCODE_NAMES = names;
    }

    private final class InstructionPrinter implements InstructionVisitor {
        private int _currentOffset = -1;

        private void printOpCode(final OpCode opCode) {
            switch (opCode) {
                case TABLESWITCH:
                case LOOKUPSWITCH:
                    _output.writeReference(OPCODE_NAMES[opCode.ordinal()], opCode);
                    break;

                default:
                    _output.writeReference(String.format("%1$-" + MAX_OPCODE_LENGTH + "s", OPCODE_NAMES[opCode.ordinal()]), opCode);
                    break;
            }
        }

        @Override
        public void visit(final Instruction instruction) {
            VerifyArgument.notNull(instruction, "instruction");

            if (_lineNumbers != null) {
                final int lineNumber = _lineNumbers[instruction.getOffset()];

                if (lineNumber >= 0) {
                    _output.write(
                        "          %1$-" + MAX_OPCODE_LENGTH + "s %2$d",
                        LINE_NUMBER_CODE,
                        lineNumber
                    );
                    _output.writeLine();
                }
            }

            _currentOffset = instruction.getOffset();

            try {
                _output.writeLabel(String.format("%1$8d", instruction.getOffset()));
                _output.write(": ");
                instruction.accept(this);
            }
            catch (Throwable t) {
                printOpCode(instruction.getOpCode());

                boolean foundError = false;

                for (int i = 0; i < instruction.getOperandCount(); i++) {
                    final Object operand = instruction.getOperand(i);

                    if (operand instanceof ErrorOperand) {
                        _output.write(String.valueOf(operand));
                        foundError = true;
                        break;
                    }
                }

                if (!foundError) {
                    _output.write("!!! ERROR");
                }

                _output.writeLine();
            }
            finally {
                _currentOffset = -1;
            }
        }

        @Override
        public void visit(final OpCode op) {
            printOpCode(op);

            final int slot = OpCodeHelpers.getLoadStoreMacroArgumentIndex(op);

            if (slot >= 0) {
                final VariableDefinitionCollection variables = _body.getVariables();

                if (slot < variables.size()) {
                    final VariableDefinition variable = findVariable(op, slot, _currentOffset);

                    assert variable != null;

                    if (variable.hasName() && variable.isFromMetadata()) {
                        _output.writeComment(" /* %s */", variable.getName());
                    }
                    else {
                        _output.writeLiteral(slot);
                    }
                }
            }

            _output.writeLine();
        }

        private VariableDefinition findVariable(final OpCode op, final int slot, final int offset) {
            VariableDefinition variable = _body.getVariables().tryFind(slot, offset);

            if (variable == null && op.isStore()) {
                variable = _body.getVariables().tryFind(slot, offset + op.getSize() + op.getOperandType().getBaseSize());
            }
            return variable;
        }

        @Override
        public void visitConstant(final OpCode op, final TypeReference value) {
            printOpCode(op);

            _output.write(' ');
            DecompilerHelpers.writeType(_output, value, NameSyntax.ERASED_SIGNATURE);
            _output.write(".class");

            _output.writeLine();
        }

        @Override
        public void visitConstant(final OpCode op, final int value) {
            printOpCode(op);

            _output.write(' ');
            _output.writeLiteral(value);

            _output.writeLine();
        }

        @Override
        public void visitConstant(final OpCode op, final long value) {
            printOpCode(op);

            _output.write(' ');
            _output.writeLiteral(value);

            _output.writeLine();
        }

        @Override
        public void visitConstant(final OpCode op, final float value) {
            printOpCode(op);

            _output.write(' ');
            _output.writeLiteral(value);

            _output.writeLine();
        }

        @Override
        public void visitConstant(final OpCode op, final double value) {
            printOpCode(op);

            _output.write(' ');
            _output.writeLiteral(value);

            _output.writeLine();
        }

        @Override
        public void visitConstant(final OpCode op, final String value) {
            printOpCode(op);

            _output.write(' ');
            _output.writeTextLiteral(StringUtilities.escape(value, true));

            _output.writeLine();
        }

        @Override
        public void visitBranch(final OpCode op, final Instruction target) {
            printOpCode(op);

            _output.write(' ');
            _output.writeLabel(String.valueOf(target.getOffset()));

            _output.writeLine();
        }

        @Override
        public void visitVariable(final OpCode op, final VariableReference variable) {
            printOpCode(op);

            _output.write(' ');

            final VariableDefinition definition = findVariable(op, variable.getSlot(), _currentOffset);

            if (definition != null && definition.hasName() && definition.isFromMetadata()) {
                _output.writeReference(variable.getName(), variable);
            }
            else {
                _output.writeLiteral(variable.getSlot());
            }

            _output.writeLine();
        }

        @Override
        public void visitVariable(final OpCode op, final VariableReference variable, final int operand) {
            printOpCode(op);
            _output.write(' ');

            final VariableDefinition definition;

            if (variable instanceof VariableDefinition) {
                definition = (VariableDefinition) variable;
            }
            else {
                definition = findVariable(op, variable.getSlot(), _currentOffset);
            }

            if (definition != null && definition.hasName() && definition.isFromMetadata()) {
                _output.writeReference(variable.getName(), variable);
            }
            else {
                _output.writeLiteral(variable.getSlot());
            }

            _output.write(", ");
            _output.writeLiteral(String.valueOf(operand));

            _output.writeLine();
        }

        @Override
        public void visitType(final OpCode op, final TypeReference type) {
            printOpCode(op);

            _output.write(' ');

            DecompilerHelpers.writeType(_output, type, NameSyntax.SIGNATURE);

            _output.writeLine();
        }

        @Override
        public void visitMethod(final OpCode op, final MethodReference method) {
            printOpCode(op);

            _output.write(' ');

            DecompilerHelpers.writeMethod(_output, method);

            _output.writeLine();
        }

        @Override
        public void visitDynamicCallSite(final OpCode op, final DynamicCallSite callSite) {
            printOpCode(op);

            _output.write(' ');

            _output.writeReference(callSite.getMethodName(), callSite.getMethodType());
            _output.writeDelimiter(":");

            DecompilerHelpers.writeMethodSignature(_output, callSite.getMethodType());

            _output.writeLine();
        }

        @Override
        public void visitField(final OpCode op, final FieldReference field) {
            printOpCode(op);

            _output.write(' ');

            DecompilerHelpers.writeField(_output, field);

            _output.writeLine();
        }

        @Override
        public void visitLabel(final Label label) {
        }

        @Override
        public void visitSwitch(final OpCode op, final SwitchInfo switchInfo) {
            printOpCode(op);
            _output.write(" {");
            _output.writeLine();

            switch (op) {
                case TABLESWITCH: {
                    final Instruction[] targets = switchInfo.getTargets();

                    int caseValue = switchInfo.getLowValue();

                    for (final Instruction target : targets) {
                        _output.write("            ");
                        _output.writeLiteral(format("%1$7d", switchInfo.getLowValue() + caseValue++));
                        _output.write(": ");
                        _output.writeLabel(String.valueOf(target.getOffset()));
                        _output.writeLine();
                    }

                    _output.write("            ");
                    _output.writeKeyword("default");
                    _output.write(": ");
                    _output.writeLabel(String.valueOf(switchInfo.getDefaultTarget().getOffset()));
                    _output.writeLine();

                    break;
                }

                case LOOKUPSWITCH: {
                    final int[] keys = switchInfo.getKeys();
                    final Instruction[] targets = switchInfo.getTargets();

                    for (int i = 0; i < keys.length; i++) {
                        final int key = keys[i];
                        final Instruction target = targets[i];

                        _output.write("            ");
                        _output.writeLiteral(format("%1$7d", key));
                        _output.write(": ");
                        _output.writeLabel(String.valueOf(target.getOffset()));
                        _output.writeLine();
                    }

                    _output.write("            ");
                    _output.writeKeyword("default");
                    _output.write(": ");
                    _output.writeLabel(String.valueOf(switchInfo.getDefaultTarget().getOffset()));
                    _output.writeLine();

                    break;
                }
            }

            _output.write("          }");
            _output.writeLine();
        }

        @Override
        public void visitEnd() {
        }
    }

    // </editor-fold>
}

