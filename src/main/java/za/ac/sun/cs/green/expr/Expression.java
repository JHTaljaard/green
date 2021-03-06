package za.ac.sun.cs.green.expr;

import java.io.Serializable;

public abstract class Expression implements Comparable<Expression>, Serializable {

	private String stringRep = null;

	public abstract void accept(Visitor visitor) throws VisitorException;

	public String getCachedString() {
		if (stringRep == null) {
			stringRep = this.toString();
		}
		return stringRep;
	}

	public double satDelta = 0.0;

	@Override
	public final int compareTo(Expression expression) {
		// TODO
		return getCachedString().compareTo(expression.getCachedString());
	}

	@Override
	public abstract boolean equals(Object object);

	@Override
	public abstract String toString();

}
