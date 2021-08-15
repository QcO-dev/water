package water.compiler.parser.nodes.variable;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.*;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;

public class VariableDeclarationNode implements Node {

	private final Token name;
	private final Node value;
	private final boolean isConst;
	private final Token access;
	private final Token staticModifier;

	public VariableDeclarationNode(Token name, Node value, boolean isConst, Token access, Token staticModifier) {
		this.name = name;
		this.value = value;
		this.isConst = isConst;
		this.access = access;
		this.staticModifier = staticModifier;
	}

	@Override
	public void preprocess(Context context) throws SemanticException {
		if(context.getType() == ContextType.GLOBAL) {
			if(context.getScope().lookupVariable(name.getValue()) != null) throw new SemanticException(name, "Redefinition of variable '%s' in global scope.".formatted(name.getValue()));
			defineGetAndSet(true, true, context);

			context.getScope().addVariable(new Variable(VariableType.STATIC, name.getValue(), "", value.getReturnType(context), isConst));
		}
		else if(context.getType() == ContextType.CLASS) {
			Variable variable = context.getScope().lookupVariable(name.getValue());
			if(variable != null && variable.getVariableType() == VariableType.CLASS) throw new SemanticException(name, "Redefinition of variable '%s' within class.".formatted(name.getValue()));

			boolean isStatic = isStatic(context);

			defineGetAndSet(false, isStatic, context);

			context.getScope().addVariable(new Variable(isStatic ? VariableType.STATIC : VariableType.CLASS, name.getValue(), context.getCurrentClass(), value.getReturnType(context), isConst));
		}
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		Type returnType = value.getReturnType(context.getContext());

		boolean buildConstructor = context.getContext().getConstructors().size() != 0;

		if(context.getContext().getType() == ContextType.GLOBAL) {
			defineGetAndSet(true, true, context.getContext());

			context.getContext().setMethodVisitor(context.getContext().getStaticMethodVisitor());
			context.getContext().setStaticMethod(true);
			context.getContext().updateLine(name.getLine());
			value.visit(context);

			context.getContext().getMethodVisitor().visitFieldInsn(Opcodes.PUTSTATIC, Type.getInternalName(context.getCurrentClass()), name.getValue(), returnType.getDescriptor());
		}
		else if(context.getContext().getType() == ContextType.FUNCTION) {

			Scope scope = context.getContext().getScope();

			Variable testShadowing = scope.lookupVariable(name.getValue());

			if(testShadowing != null && testShadowing.getVariableType() == VariableType.LOCAL) {
				throw new SemanticException(name, "Redefinition of variable '%s' in same scope.".formatted(name.getValue()));
			}
			context.getContext().updateLine(name.getLine());
			value.visit(context);


			Variable var = new Variable(VariableType.LOCAL, name.getValue(), scope.nextLocal(), returnType, isConst);
			scope.addVariable(var);

			context.getContext().getMethodVisitor().visitVarInsn(returnType.getOpcode(Opcodes.ISTORE), var.getIndex());

			if(returnType.getSize() == 2) scope.nextLocal();
		}
		else if(context.getContext().getType() == ContextType.CLASS) {
			if(!buildConstructor) defineGetAndSet(false, isStatic(context.getContext()), context.getContext());

			if(buildConstructor) {
				context.getContext().getConstructors().forEach(c -> c.addVariable(this));
				return;
			}

			if(isStatic(context.getContext())) {
				context.getContext().setMethodVisitor(context.getContext().getStaticMethodVisitor());
				context.getContext().setStaticMethod(true);
			}
			else {
				context.getContext().setMethodVisitor(context.getContext().getDefaultConstructor());
				context.getContext().getMethodVisitor().visitVarInsn(Opcodes.ALOAD, 0);
				context.getContext().setStaticMethod(false);
			}
			context.getContext().updateLine(name.getLine());

			value.visit(context);

			int setOpcode = isStatic(context.getContext()) ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD;

			context.getContext().getMethodVisitor().visitFieldInsn(setOpcode, Type.getInternalName(context.getCurrentClass()), name.getValue(), returnType.getDescriptor());
		}
	}

	private void defineGetAndSet(boolean isFinal, boolean isStatic, Context context) throws SemanticException {

		int finalMod = isConst ? Opcodes.ACC_FINAL : 0;
		int staticMod = isStatic ? Opcodes.ACC_STATIC : 0;
		int methodFinalMod = isFinal ? Opcodes.ACC_FINAL : 0;

		int getOpcode = isStatic ? Opcodes.GETSTATIC : Opcodes.GETFIELD;
		int setOpcode = isStatic ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD;

		context.getCurrentClassWriter().visitField(Opcodes.ACC_PRIVATE | staticMod | finalMod, name.getValue(), value.getReturnType(context).getDescriptor(), null, null);

		String beanName = name.getValue().substring(0, 1).toUpperCase() + name.getValue().substring(1);

		Type fieldType = value.getReturnType(context);
		String descriptor = fieldType.getDescriptor();

		// Getter
		if(verifyAccess()) {
			String fName = name.getValue().matches("^is[\\p{Lu}].*") ? name.getValue() : "get" + beanName;
			MethodVisitor visitor = context.getCurrentClassWriter().visitMethod(Opcodes.ACC_PUBLIC | staticMod | methodFinalMod, fName, "()" + descriptor, null, null);
			visitor.visitCode();
			if(!isStatic) visitor.visitVarInsn(Opcodes.ALOAD, 0);
			visitor.visitFieldInsn(getOpcode, context.getCurrentClass(), name.getValue(), descriptor);
			visitor.visitInsn(fieldType.getOpcode(Opcodes.IRETURN));
			visitor.visitMaxs(1, 0);
			visitor.visitEnd();
		}
		//Setter
		if(verifyAccess() && !isConst) {
			String fName = "set" + (name.getValue().matches("^is[\\p{Lu}].*") ? beanName.substring(2) : beanName);
			MethodVisitor visitor = context.getCurrentClassWriter().visitMethod(Opcodes.ACC_PUBLIC | staticMod | methodFinalMod, fName, "("  + descriptor + ")V", null, null);
			visitor.visitCode();
			if(!isStatic) visitor.visitVarInsn(Opcodes.ALOAD, 0);
			visitor.visitVarInsn(fieldType.getOpcode(Opcodes.ILOAD), isStatic ? 0 : 1);
			visitor.visitFieldInsn(setOpcode, context.getCurrentClass(), name.getValue(), descriptor);
			visitor.visitInsn(Opcodes.RETURN);
			visitor.visitMaxs(1, 1);
			visitor.visitEnd();
		}

	}

	private boolean verifyAccess() throws SemanticException {
		if(access == null) return true;
		return switch (access.getType()) {
			case PUBLIC -> true;
			case PRIVATE -> false;
			default -> throw new SemanticException(access, "Invalid access modifier for variable '%s'".formatted(access.getValue()));
		};
	}

	public boolean isStatic(Context context) {
		return staticModifier != null || context.getType() == ContextType.GLOBAL;
	}

	@Override
	public String toString() {
		return (isConst ? "const" : "var") + " %s = %s;".formatted(name.getValue(), value);
	}
}
