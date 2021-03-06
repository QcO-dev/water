package water.compiler.parser.nodes.variable;

import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.compiler.Variable;
import water.compiler.compiler.VariableType;
import water.compiler.lexer.Token;
import water.compiler.parser.LValue;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;
import water.compiler.util.WaterType;

public class VariableAccessNode implements Node {
	private final Token name;
	private boolean isMemberAccess;
	private boolean isStaticClassAccess;

	public VariableAccessNode(Token name) {
		this.name = name;
		this.isMemberAccess = false;
		this.isStaticClassAccess = false;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		context.getContext().updateLine(name.getLine());
		Variable v = context.getContext().getScope().lookupVariable(name.getValue());

		if(v == null) {
			if(isMemberAccess) {
				try {
					TypeUtil.classForName(name.getValue(), context.getContext());
					isStaticClassAccess = true;
					return;
				} catch (ClassNotFoundException e) {
					throw new SemanticException(name, "Cannot resolve variable '%s' in current scope.".formatted(name.getValue()));
				}
			}
			throw new SemanticException(name, "Cannot resolve variable '%s' in current scope.".formatted(name.getValue()));
		}

		if(v.getVariableType() == VariableType.STATIC) {
			context.getContext().getMethodVisitor().visitFieldInsn(Opcodes.GETSTATIC, v.getOwner(), v.getName(), v.getType().getDescriptor());
		}
		else if(v.getVariableType() == VariableType.CLASS) {
			if(context.getContext().isStaticMethod())  throw new SemanticException(name, "Cannot access instance member '%s' in a static context".formatted(name.getValue()));
			context.getContext().getMethodVisitor().visitVarInsn(Opcodes.ALOAD, 0);
			context.getContext().getMethodVisitor().visitFieldInsn(Opcodes.GETFIELD, v.getOwner(), v.getName(), v.getType().getDescriptor());
		}
		else {
			context.getContext().getMethodVisitor().visitVarInsn(v.getType().getOpcode(Opcodes.ILOAD), v.getIndex());
		}
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		Variable v = context.getScope().lookupVariable(name.getValue());

		if(v == null) {
			if(isMemberAccess) {
				try {
					Class<?> staticClass = TypeUtil.classForName(name.getValue(), context);
					isStaticClassAccess = true;
					return WaterType.getType(staticClass);
				} catch (ClassNotFoundException e) {
					throw new SemanticException(name, "Cannot resolve variable '%s' in current scope.".formatted(name.getValue()));
				}
			}
			throw new SemanticException(name, "Cannot resolve variable '%s' in current scope.".formatted(name.getValue()));
		}

		return v.getType();
	}

	@Override
	public LValue getLValue() {
		return LValue.VARIABLE;
	}

	@Override
	public Object[] getLValueData() {
		return new Object[] { name };
	}

	public void setMemberAccess(boolean memberAccess) {
		isMemberAccess = memberAccess;
	}

	public boolean isStaticClassAccess() {
		return isStaticClassAccess;
	}

	@Override
	public String toString() {
		return name.getValue();
	}
}
