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

	public VariableDeclarationNode(Token name, Node value, boolean isConst, Token access) {
		this.name = name;
		this.value = value;
		this.isConst = isConst;
		this.access = access;
	}

	@Override
	public void preprocess(Context context) throws SemanticException {
		if(context.getType() == ContextType.GLOBAL) {
			if(context.getScope().lookupVariable(name.getValue()) != null) throw new SemanticException(name, "Redefinition of variable '%s' in global scope.".formatted(name.getValue()));
			defineGlobal(context);

			context.getScope().addVariable(new Variable(VariableType.GLOBAL, name.getValue(), "", value.getReturnType(context), isConst));
		}
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		Type returnType = value.getReturnType(context.getContext());

		context.getContext().updateLine(name.getLine());

		if(context.getContext().getType() == ContextType.GLOBAL) {
			defineGlobal(context.getContext());

			context.getContext().setMethodVisitor(context.getContext().getStaticMethodVisitor());
			value.visit(context);

			context.getContext().getMethodVisitor().visitFieldInsn(Opcodes.PUTSTATIC, Type.getInternalName(context.getCurrentClass()), name.getValue(), returnType.getDescriptor());
		}
		else if(context.getContext().getType() == ContextType.FUNCTION) {

			Scope scope = context.getContext().getScope();

			Variable testShadowing = scope.lookupVariable(name.getValue());

			if(testShadowing != null && testShadowing.getVariableType() == VariableType.LOCAL) {
				throw new SemanticException(name, "Redefinition of variable '%s' in same scope.".formatted(name.getValue()));
			}

			value.visit(context);


			Variable var = new Variable(VariableType.LOCAL, name.getValue(), scope.nextLocal(), returnType, isConst);
			scope.addVariable(var);

			context.getContext().getMethodVisitor().visitVarInsn(returnType.getOpcode(Opcodes.ISTORE), var.getIndex());

			if(returnType.getSize() == 2) scope.nextLocal();
		}
		//TODO class
	}

	private void defineGlobal(Context context) throws SemanticException {

		int finalMod = isConst ? Opcodes.ACC_FINAL : 0;

		context.getCurrentClassWriter().visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | finalMod, name.getValue(), value.getReturnType(context).getDescriptor(), null, null);

		String beanName = name.getValue().substring(0, 1).toUpperCase() + name.getValue().substring(1);

		Type fieldType = value.getReturnType(context);
		String descriptor = fieldType.getDescriptor();

		// Getter
		if(verifyAccess()) {
			String fName = name.getValue().matches("^is[\\p{Lu}].*") ? name.getValue() : "get" + beanName;
			MethodVisitor visitor = context.getCurrentClassWriter().visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, fName, "()" + descriptor, null, null);
			visitor.visitCode();
			visitor.visitFieldInsn(Opcodes.GETSTATIC, context.getCurrentClass(), name.getValue(), descriptor);
			visitor.visitInsn(fieldType.getOpcode(Opcodes.IRETURN));
			visitor.visitMaxs(1, 0);
			visitor.visitEnd();
		}
		//Setter
		if(verifyAccess() && !isConst) {
			String fName = "set" + (name.getValue().matches("^is[\\p{Lu}].*") ? beanName.substring(2) : beanName);
			MethodVisitor visitor = context.getCurrentClassWriter().visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, fName, "("  + descriptor + ")V", null, null);
			visitor.visitCode();
			visitor.visitVarInsn(fieldType.getOpcode(Opcodes.ILOAD), 0);
			visitor.visitFieldInsn(Opcodes.PUTSTATIC, context.getCurrentClass(), name.getValue(), descriptor);
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

	@Override
	public String toString() {
		return (isConst ? "const" : "var") + " %s = %s;".formatted(name.getValue(), value);
	}
}
