package za.ac.sun.cs.green.service.z3;

import com.microsoft.z3.*;
import za.ac.sun.cs.green.Green;
import za.ac.sun.cs.green.Instance;
import za.ac.sun.cs.green.expr.VisitorException;
import za.ac.sun.cs.green.service.SATService;
import za.ac.sun.cs.green.util.Reporter;

import java.util.HashMap;
import java.util.Properties;

public class SATZ3JavaBVService extends SATService {

	Context ctx;
	Solver Z3solver;
	protected long timeConsumption = 0;
	protected long translationTimeConsumption = 0;
	protected long satTimeConsumption = 0;
	protected long unsatTimeConsumption = 0;
	protected int conjunctCount = 0;
	protected int variableCount = 0;

	private static class Z3Wrapper {
		private Context ctx;
		private Solver solver;
		private final String LOGIC = "QF_BV";

		private static Z3Wrapper instance = null;

		public static Z3Wrapper getInstance() {
			if (instance != null) {
				return instance;
			}
			return instance = new Z3Wrapper();
		}

		private Z3Wrapper() {
			HashMap<String, String> cfg = new HashMap<String, String>();
			cfg.put("model", "false"); //"true" ?
			try {
				ctx = new Context(cfg);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException("## Error Z3: Exception caught in Z3 JNI: \n" + e);
			}
			// TODO : Changed logic to QF_LIA from AUF_LIA
			solver = ctx.mkSolver(LOGIC);
		}

		public Solver getSolver() {
			return this.solver;
		}

		public Context getCtx() {
			return this.ctx;
		}
	}

	public SATZ3JavaBVService(Green solver, Properties properties) {
		super(solver);

		Z3Wrapper z3Wrapper = Z3Wrapper.getInstance();
		Z3solver = z3Wrapper.getSolver();
		ctx = z3Wrapper.getCtx();
		
		/*
		HashMap<String, String> cfg = new HashMap<String, String>();
        cfg.put("model", "false");
		try{
			ctx = new Context(cfg);		 
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("## Error Z3: Exception caught in Z3 JNI: \n" + e);
	    }
	    */
	}

	@Override
	protected Boolean solve(Instance instance) {
		long start = System.currentTimeMillis();
		Boolean result = false;
		// translate instance to Z3
		long T0translation = System.currentTimeMillis();
		Z3JavaBVTranslator translator = new Z3JavaBVTranslator(ctx);
		try {
			instance.getExpression().accept(translator);
		} catch (VisitorException e1) {
			log.warn("Error in translation to Z3 ({})", e1.getMessage());
		}
		// get context out of the translator
		BoolExpr expr = translator.getTranslation();
		// model should now be in ctx
		try {
			//Z3solver = ctx.mkSolver();
			Z3solver.push();
			Z3solver.add(expr);
		} catch (Z3Exception e1) {
			log.warn("Error in Z3 ({})", e1.getMessage());
		}
		conjunctCount += instance.getExpression().getCachedString().split("&&").length;
		variableCount += translator.getVariableCount();
		translationTimeConsumption += (System.currentTimeMillis() - T0translation);
		//solve
		try {
			result = Status.SATISFIABLE == Z3solver.check();
//            System.out.println("EXPR (" + result + "): " + instance.getFullExpression());
		} catch (Z3Exception e) {
			log.warn("Error in Z3 ({})", e.getMessage());
		}
		// clean up
		int scopes = Z3solver.getNumScopes();
		if (scopes > 0) {
			Z3solver.pop(scopes);
		}
		timeConsumption += (System.currentTimeMillis() - start);
		if (result) {
			satTimeConsumption += (System.currentTimeMillis() - start);
		} else {
			unsatTimeConsumption += (System.currentTimeMillis() - start);
		}
		return result;
	}

	@Override
	public void report(Reporter reporter) {
		reporter.report(getClass().getSimpleName(), "cacheHitCount = " + cacheHitCount);
		reporter.report(getClass().getSimpleName(), "cacheMissCount = " + cacheMissCount);
		reporter.report(getClass().getSimpleName(), "satCacheHitCount = " + satHitCount);
		reporter.report(getClass().getSimpleName(), "unsatCacheHitCount = " + unsatHitCount);
		reporter.report(getClass().getSimpleName(), "satCacheMissCount = " + satMissCount);
		reporter.report(getClass().getSimpleName(), "unsatCacheMissCount = " + unsatMissCount);
		reporter.report(getClass().getSimpleName(), "satQueries = " + satCount);
		reporter.report(getClass().getSimpleName(), "unsatQueries = " + unsatCount);
		reporter.report(getClass().getSimpleName(), "timeConsumption = " + timeConsumption);
		reporter.report(getClass().getSimpleName(), "satTimeConsumption = " + satTimeConsumption);
		reporter.report(getClass().getSimpleName(), "unsatTimeConsumption = " + unsatTimeConsumption);
		reporter.report(getClass().getSimpleName(), "storageTimeConsumption = " + storageTimeConsumption);
		reporter.report(getClass().getSimpleName(), "translationTimeConsumption = " + translationTimeConsumption);
		reporter.report(getClass().getSimpleName(), "conjunctCount = " + conjunctCount);
		reporter.report(getClass().getSimpleName(), "variableCount = " + variableCount);
	}


}
