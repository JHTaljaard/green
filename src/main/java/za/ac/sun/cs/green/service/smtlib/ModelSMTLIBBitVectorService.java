package za.ac.sun.cs.green.service.smtlib;

import za.ac.sun.cs.green.Green;
import za.ac.sun.cs.green.Instance;
import za.ac.sun.cs.green.expr.*;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.service.ModelService;
import za.ac.sun.cs.green.util.Misc;

import java.util.*;

public abstract class ModelSMTLIBBitVectorService extends ModelService {

	public ModelSMTLIBBitVectorService(Green solver) {
		super(solver);
	}

	@Override
	protected Map<Variable, Object> model(Instance instance) {
		try {
			Translator t = new Translator();
			instance.getExpression().accept(t);
			StringBuilder b = new StringBuilder();
			b.append("(set-option :produce-models true)");
			// b.append("(set-logic QF_BV)"); // Quantifier Free Bit Vector
			// b.append("(set-logic AUFLIRA)"); // Arrays Uninterpreted Functions Linear Integer Real Arithmetic
			b.append(Misc.join(t.getVariableDecls(), " "));
			b.append("(assert ").append(t.getTranslation()).append(')');
			b.append("(check-sat)");
			//b = new StringBuilder(); 
			//b.append("(set-option :produce-models true)(set-logic FloatingPoint)(declare-fun v () (_ Float32))(assert (and (and (bvsge v #x00000000) (bvsle v #x00000032)) (bvsge v #x00000001)))(check-sat)");
			return solve0(b.toString(), t.getVariables());
		} catch (TranslatorUnsupportedOperation x) {
			log.warn(x.getMessage(), x);
		} catch (VisitorException x) {
			log.fatal(x.getMessage(), x);
		}
		return null;
	}

	protected abstract Map<Variable, Object> solve0(String smtQuery, Map<Variable, String> variables);

	@SuppressWarnings("serial")
	private static class TranslatorUnsupportedOperation extends VisitorException {
		public TranslatorUnsupportedOperation(String message) {
			super(message);
		}
	}

	private static class TranslatorPair {
		private final String string;
		private final Class<? extends Variable> type;

		public TranslatorPair(final String string, final Class<? extends Variable> type) {
			this.string = string;
			this.type = type;
		}

		public String getString() {
			return string;
		}

		public Class<? extends Variable> getType() {
			return type;
		}
	}

	private static class Translator extends Visitor {

		private final Stack<TranslatorPair> stack;
		private Map<Variable, String> varMap;
		private final List<String> defs;
		private final List<String> domains;

		public Translator() {
			stack = new Stack<TranslatorPair>();
			varMap = new HashMap<Variable, String>();
			defs = new LinkedList<String>();
			domains = new LinkedList<String>();
		}

		public List<String> getVariableDecls() {
			return defs;
		}

		public Map<Variable, String> getVariables() {
			return varMap;
		}

		public String getTranslation() {
			StringBuilder b = new StringBuilder();
			b.append("(and");
			for (String domain : domains) {
				b.append(' ').append(domain);
			}
			TranslatorPair p = stack.pop();
			b.append(' ').append(p.getString()).append(')');
			assert stack.isEmpty();
			return b.toString();
		}

		private String transformNegative(long v) {
			if (v < 0) {
				StringBuilder b = new StringBuilder();
				b.append("(bvneg ").append(-v).append(')');
				return b.toString();
			} else {
				return Long.toString(v);
			}
		}

		private String transformNegative(double v) {
			if (v < 0) {
				StringBuilder b = new StringBuilder();
				b.append("(fp.neg ").append(-v).append(')');
				return b.toString();
			} else {
				return Double.toString(v);
			}
		}

		@Override
		public void postVisit(IntConstant constant) {
			int val = constant.getValue();
			stack.push(new TranslatorPair(transformNegative(val), IntVariable.class));
		}

		@Override
		public void postVisit(IntegerConstant constant) {
			long val = constant.getValue();
			stack.push(new TranslatorPair(transformNegative(val), IntegerVariable.class));
		}

		@Override
		public void postVisit(RealConstant constant) {
			double val = constant.getValue();
			stack.push(new TranslatorPair(transformNegative(val), RealVariable.class));
		}

		@Override
		public void postVisit(IntVariable variable) {
			String v = varMap.get(variable);
			String n = variable.getName();
			if (v == null) {
				StringBuilder b = new StringBuilder();
				b.append("(declare-fun ").append(n).append(" () (_ BitVec 64))");
				defs.add(b.toString());
				b.setLength(0);
				// lower bound
				b.append("(and (bvsge ").append(n).append(' ');
				b.append(transformNegative(variable.getLowerBound()));
				// upper bound
				b.append(") (bvsle ").append(n).append(' ');
				b.append(transformNegative(variable.getUpperBound()));
				b.append("))");
				domains.add(b.toString());
				varMap.put(variable, n);
			}
			stack.push(new TranslatorPair(n, IntVariable.class));
		}

		@Override
		public void postVisit(IntegerVariable variable) {
			String v = varMap.get(variable);
			String n = variable.getName();
			if (v == null) {
				StringBuilder b = new StringBuilder();
				b.append("(declare-fun ").append(n).append(" () (_ BitVec 64))");
				defs.add(b.toString());
				b.setLength(0);
				// lower bound
				b.append("(and (bvsge ").append(n).append(' ');
				b.append(transformNegative(variable.getLowerBound()));
				// upper bound
				b.append(") (bvsle ").append(n).append(' ');
				b.append(transformNegative(variable.getUpperBound()));
				b.append("))");
				domains.add(b.toString());
				varMap.put(variable, n);
			}
			stack.push(new TranslatorPair(n, IntegerVariable.class));
		}

		@Override
		public void postVisit(RealVariable variable) {
			String v = varMap.get(variable);
			String n = variable.getName();
			if (v == null) {
				StringBuilder b = new StringBuilder();
				b.append("(declare-fun ").append(n).append(" () (_ Float64))");
				defs.add(b.toString());
				b.setLength(0);
				// lower bound
				b.append("(and (fp.geq ").append(n).append(' ');
				b.append(transformNegative(variable.getLowerBound()));
				// upper bound
				b.append(") (fp.leq ").append(n).append(' ');
				b.append(transformNegative(variable.getUpperBound()));
				b.append("))");
				domains.add(b.toString());
				varMap.put(variable, n);
			}
			stack.push(new TranslatorPair(n, RealVariable.class));
		}

		private Class<? extends Variable> superType(TranslatorPair left, TranslatorPair right) {
			assert left != null;
			assert right != null;
			if ((left.getType() == RealVariable.class) || (right.getType() == RealVariable.class)) {
				return RealVariable.class;
			} else {
				return IntVariable.class;
			}
		}

		private String adjust(TranslatorPair term, Class<? extends Variable> type) {
			String s = term.getString();
			Class<? extends Variable> t = term.getType();
			if (t == type) {
				return s;
			} else {
				StringBuilder b = new StringBuilder();
				b.append("(_ to_fp 11 53 RNE ").append(s).append(')');
				return b.toString();
			}
		}

		private String setFPOperator(Operator op) throws TranslatorUnsupportedOperation {
			switch (op) {
				case EQ:
					return "fp.eq";
				case LT:
					return "fp.lt";
				case LE:
					return "fp.leq";
				case GT:
					return "fp.gt";
				case GE:
					return "fp.geq";
				case NOT:
					return "not";
				case AND:
					return "and";
				case OR:
					return "or";
				case IMPLIES:
					return "=>"; // not sure about this one?
				case ADD:
					return "fp.add";
				case SUB:
					return "fp.sub";
				case MUL:
					return "fp.mul";
				case DIV:
					return "fp.div";
				case MOD:
					return "fp.mod";
				case SQRT:
					return "fp.sqrt";
				case BIT_AND:
				case BIT_OR:
				case BIT_XOR:
				case SHIFTL:
				case SHIFTR:
				case SHIFTUR:
				case SIN:
				case COS:
				case TAN:
				case ASIN:
				case ACOS:
				case ATAN:
				case ATAN2:
				case ROUND:
				case LOG:
				case EXP:
				case POWER:
				default:
					throw new TranslatorUnsupportedOperation("unsupported operation " + op);
			}
		}

		private String setBVOperator(Operator op) throws TranslatorUnsupportedOperation {
			switch (op) {
				case EQ:
					return "=";
				case LT:
					return "bvslt";
				case LE:
					return "bvsle";
				case GT:
					return "bvsgt";
				case GE:
					return "bvsge";
				case NOT:
					return "not";
				case AND:
					return "and";
				case OR:
					return "or";
				case IMPLIES:
					return "=>"; // not sure about this one?
				case ADD:
					return "bvadd";
				case SUB:
					return "bvsub";
				case MUL:
					return "bvmul";
				case DIV:
					return "bvsdiv";
				case MOD:
					return "bvsmod";
				case BIT_AND:
					return "bvand";
				case BIT_OR:
					return "bvor";
				case BIT_XOR:
					return "bvxor";
				case SHIFTL:
					return "bvshl";
				case SHIFTR:
					return "bvashr";
				case SHIFTUR:
					return "bvshr";
				case SIN:
				case COS:
				case TAN:
				case ASIN:
				case ACOS:
				case ATAN:
				case ATAN2:
				case ROUND:
				case LOG:
				case EXP:
				case POWER:
				case SQRT:
				default:
					throw new TranslatorUnsupportedOperation("unsupported operation " + op);
			}
		}

		public void postVisit(Operation operation) throws TranslatorUnsupportedOperation {
			TranslatorPair l = null;
			TranslatorPair r = null;
			Operator op = operation.getOperator();
			int arity = op.getArity();
			if (arity == 2) {
				if (!stack.isEmpty()) {
					r = stack.pop();
				}
				if (!stack.isEmpty()) {
					l = stack.pop();
				}
				if (op.equals(Operator.NE)) {
					Class<? extends Variable> v = superType(l, r);
					StringBuilder b = new StringBuilder();
					b.append("(not (= ");
					b.append(adjust(l, v)).append(' ');
					b.append(adjust(r, v)).append("))");
					stack.push(new TranslatorPair(b.toString(), v));
				} else {
					Class<? extends Variable> v = superType(l, r);
					StringBuilder b = new StringBuilder();
					//b.append('(').append(setOperator(op)).append(' ');
					b.append(adjust(l, v)).append(' ');
					b.append(adjust(r, v)).append(')');
					stack.push(new TranslatorPair(b.toString(), v));
				}
			} else if (arity == 1) {
				if (!stack.isEmpty()) {
					l = stack.pop();
				}
				Class<? extends Variable> v = IntVariable.class;
				StringBuilder b = new StringBuilder();
				//b.append('(').append(setOperator(op)).append(' ');
				b.append(adjust(l, v)).append(')');
				stack.push(new TranslatorPair(b.toString(), v));
			}
		}
	}

}
