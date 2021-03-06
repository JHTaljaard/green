package za.ac.sun.cs.green.service.grulia;

import za.ac.sun.cs.green.Green;
import za.ac.sun.cs.green.Instance;
import za.ac.sun.cs.green.expr.*;
import za.ac.sun.cs.green.service.ModelCoreService;
import za.ac.sun.cs.green.service.ModelService1;
import za.ac.sun.cs.green.service.grulia.gruliastore.BinaryTreeStore;
import za.ac.sun.cs.green.service.grulia.gruliastore.GruliaStore;
import za.ac.sun.cs.green.service.grulia.gruliastore.SatEntry;
import za.ac.sun.cs.green.service.grulia.gruliastore.UnsatEntry;
import za.ac.sun.cs.green.service.z3.ModelCoreZ3JavaService;
import za.ac.sun.cs.green.util.Reporter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author JH Taljaard (USnr 18509193)
 * @author Willem Visser (Supervisor)
 * @author Jaco Geldenhuys (Supervisor)
 * <p>
 * Description:
 * Based on Utopia (an SMT caching framework), which is defined in the paper:
 * "Heuristically Matching Formula Solution Spaces To Efficiently Reuse Solutions"
 * published at the International Conference on Software Engineering (ICSE'17)
 * by Andrea Aquino, Giovanni Denaro and Mauro Pezze'.
 * <p>
 * Julia (Java version of Utopia Linear Integer Arithmetic)
 * re-implemented to improve GREEN. Julia is implemented
 * as a service in GREEN -- Grulia.
 */
public class ModelGruliaService extends ModelService1 {
	/*
	##################################################################
	####################### Variables to set #########################
	##################################################################
	*/

	/**
	 * The number of closest entries to extract
	 */
	private final int K = 10;

	/**
	 * Substitute zero if is variable not present in model
	 */
	private final boolean defaultZero = false;

	/**
	 * TreeSet repo or not.
	 */
	private final boolean binarysearch = true;

	/**
	 * Use Z3 with java bindings or commandline.
	 */
//	private boolean z3java = false;

	/**
	 * The value of the reference solution.
	 * For experiments: -10000, 0, 100
	 */
	private final Integer[] REFERENCE_SOLUTIONS = {-10000, 0, 100};
	private final int REF_SOL_SIZE = REFERENCE_SOLUTIONS.length;

	private final String DEFAULT_Z3_PATH;
	private final String DEFAULT_Z3_ARGS = "-smt2 -in";
	private final String resourceName = "build.properties";

	/**
	 * For debugging purposes.
	 */
	private static final boolean DEBUG = false;

	/*##################################################################*/

	/**
	 * Stores data of satisfiable formulas.
	 */
	private final GruliaStore<SatEntry> SAT_REPO;

	/**
	 * Stores data of unsatisfiable formulas.
	 */
	private final GruliaStore<UnsatEntry> UNSAT_REPO;

	/**
	 * Instance of model checker.
	 */
	private final ModelCoreService mcs;

	/*
	 ##################################################################
	 #################### For logging purposes ########################
	 ##################################################################
	*/

	/*
	 #################### COUNTERS ########################
	*/

	/**
	 * Number of times the service has been invoked.
	 */
	private int invocationCount = 0;

	/**
	 * Number of times some model satisfied some expression (in a run).
	 */
	private int satModelCount = 0;

	/**
	 * Number of times some unsat-core was in some expression (in a run).
	 */
	private int sharesUnsatCoresCount = 0;

	/**
	 * Number of times model tested
	 */
	private int modelsTested = 0;

	/**
	 * Number of times core tested
	 */
	private int unsatCoresTested = 0;

	/**
	 * Number of times the SMT solver was called.
	 */
	private int solverCount = 0;

	/**
	 * Number of models cached.
	 */
	private int satEntryCount = 0;

	/**
	 * Number of cores cached.
	 */
	private int unsatEntryCount = 0;

	/**
	 * Number of satisfied expressions (for a run).
	 */
	private int satCount = 0;

	/**
	 * Number of unsatisfied expressions.
	 */
	private int unsatCount = 0;

	/**
	 * Number of times a valid sat entry found in the Repository.
	 */
	private int satCacheHitCount = 0;

	/**
	 * Number of times a valid unsat entry found in the Repository.
	 */
	private int unsatCacheHitCount = 0;

	/**
	 * Number of times a valid sat entry was not found in the satRepo.
	 */
	private int satCacheMissCount = 0;

	/**
	 * Number of times a valid unsat entry was not found in the satRepo.
	 */
	private int unsatCacheMissCount = 0;

	/**
	 * Number of times a reference model satisfied the constraint
	 */
	private long satDeltaIs0 = 0;

	/**
	 * Total number of variables encountered.
	 */
//	private int totalVariableCount = 0;

	/**
	 * To keep track of the already seen variables.
	 */
    /*
	protected static ArrayList<IntVariable> newVariables;
	protected static ArrayList<Double> satDeltaValues;
	protected static ArrayList<Double> satDeltaValuesInRepo;
    protected static int[] modelNumbers;
    */

	/**
	 * Total number of new variables encountered.
	 */
	protected int newVariableCount = 0;

	/*
	 #################### TIME ########################
	*/

	/**
	 * Execution Time of the service.
	 */
	private long timeConsumption = 0;
	private long satTimeConsumption = 0;
	private long unsatTimeConsumption = 0;
	private long cacheLoadTimeConsumption = 0;

	private long satCacheTime = 0;
	private long unsatCacheTime = 0;
	private long solverTime = 0;
	private long satDeltaCalculationTime = 0;

	private long modelsExtractionTime = 0;
	private long modelsTestingTime = 0;
	private long modelEvalTime = 0;
	private long coreExtractionTime = 0;
	private long coreTestingTime = 0;

	/*** Resetting counters ***/
	private void setSatModelCount(int x) {
		satModelCount = x;
	}

	private void setUnsatCoreCount(int x) {
		sharesUnsatCoresCount = x;
	}

	private void setSolverCount(int x) {
		solverCount = x;
	}

	private void setEntryCount(int x) {
		satEntryCount = x;
		unsatEntryCount = x;
	}

	private void setSatCount(int x) {
		satCount = x;
	}

	private void setUnsatCount(int x) {
		unsatCount = x;
	}

	private void setTimeConsumption(long x) {
		timeConsumption = x;
		satTimeConsumption = x;
		unsatTimeConsumption = x;
	}

	private void setCacheHitCount(int x) {
		satCacheHitCount = x;
	}

	private void setUnsatCacheHitCount(int x) {
		unsatCacheHitCount = x;
	}

	private void setCacheMissCount(int x) {
		satCacheMissCount = x;
	}

	private void setUnsatCacheMissCount(int x) {
		unsatCacheMissCount = x;
	}

	public void reset() {
		setCacheHitCount(0);
		setCacheMissCount(0);
		setEntryCount(0);
		setSatCount(0);
		setSatModelCount(0);
		setSolverCount(0);
		setTimeConsumption(0L);
		setUnsatCount(0);
		setUnsatCacheHitCount(0);
		setUnsatCacheMissCount(0);
		setUnsatCoreCount(0);
	}

	/*##################################################################*/

	/**
	 * Constructor for the basic service. It simply initializes its three
	 * attributes.
	 * <p>
	 * GuliaService recommends to run with Factorizer and Renamer.
	 *
	 * @param solver the {@link Green} solver this service will be added to
	 */
	public ModelGruliaService(Green solver) {
		super(solver);
		SAT_REPO = new BinaryTreeStore<SatEntry>(solver, defaultZero);
		UNSAT_REPO = new BinaryTreeStore<UnsatEntry>(solver, defaultZero);

		// Load from store (specifically redis)
		// -- for persistent storage
		long start = System.currentTimeMillis();
		for (String key : solver.getStore().keySet(SAT_REPO.getKey() + "*")) {
			Object val = solver.getStore().get(key);
			if (val instanceof SatEntry) {
				SAT_REPO.add((SatEntry) val);
			} else if (val instanceof UnsatEntry) {
				UNSAT_REPO.add((UnsatEntry) val);
			}
		}
		cacheLoadTimeConsumption += (System.currentTimeMillis() - start);

        /*
		newVariables = new ArrayList<IntVariable>();
		satDeltaValues = new ArrayList<Double>();
		satDeltaValuesInRepo = new ArrayList<Double>();
        modelNumbers = new int[K];
        */

		// Properties for model checking.
		Properties properties = new Properties();
		String z3Path = "/z3/build/z3";

		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		InputStream resourceStream;
		try {
			resourceStream = loader.getResourceAsStream(resourceName);
			if (resourceStream == null) {
				// If properties are correct, override with that specified path.
				resourceStream = new FileInputStream((new File("").getAbsolutePath()) + "/" + resourceName);

			}
			if (resourceStream != null) {
				properties.load(resourceStream);
				z3Path = properties.getProperty("z3path");

				resourceStream.close();
			}
		} catch (IOException x) {
			// ignore
		}

		DEFAULT_Z3_PATH = z3Path;

		String p = properties.getProperty("green.z3.path", DEFAULT_Z3_PATH);
		String store = properties.getProperty("green.store", "");
		properties.setProperty("green.z3.path", p);
		properties.setProperty("z3path", p);
		properties.setProperty("green.store", store);

		properties.setProperty("green.services", "solver");
		properties.setProperty("green.service.solver", "(z3mc)");
		properties.setProperty("green.service.solver.z3mc", "za.ac.sun.cs.green.service.z3.ModelCoreZ3JavaService");
		mcs = new ModelCoreZ3JavaService(solver, properties);
	}

	/**
	 * Executes the Utopia algorithm as described in the paper of Aquino.
	 *
	 * @param instance The instance to solve.
	 * @return satisfiability of the constraint.
	 */
	private Map<Variable, Object> solve1(Instance instance) {
		double satDelta;
		long startTime;
		Map<Variable, Object> model = null;

		invocationCount++;
		Expression target = instance.getFullExpression();
		ExprVisitor exprVisitor = new ExprVisitor();
		try {
			target.accept(exprVisitor);
		} catch (VisitorException x) {
			log.fatal("encountered an exception -- this should not be happening!", x);
		}

		SortedSet<IntVariable> setOfVars = exprVisitor.getVariableSet();

		startTime = System.currentTimeMillis();
		Object[] deltaAndModel = calculateSATDelta(target);
		satDeltaCalculationTime += (System.currentTimeMillis() - startTime);

		if (deltaAndModel[0] != null) {
			// The sat-delta computation produced a hit
			// The reference solution satisfies the constraint
			return (Map<Variable, Object>) deltaAndModel[0];
		}

		satDelta = (double) deltaAndModel[1];

		startTime = System.currentTimeMillis();
		SatEntry ref0 = new SatEntry(satDelta, null, setOfVars.size());
		model = sharesModel(ref0, target);
		satCacheTime += (System.currentTimeMillis() - startTime);
		if (model != null) {
			// if model satisfied expression, i.e. query is sat
			// return immediately
			return model;
		}

		startTime = System.currentTimeMillis();
		UnsatEntry ref1 = new UnsatEntry(satDelta, null, -1);
		Set<Expression> core = sharesUnsatCores(ref1, target);
		unsatCacheTime += (System.currentTimeMillis() - startTime);
		if (core != null) {
			// if shares unsat cores i.e. query is unsat
			// return immediately
			// null if it is false
			// (since rest of Green pipeline does not support cores)
			return null;
		}

		// else continue and calculate solution
		// call solver & store
		startTime = System.currentTimeMillis();
		model = callSolver(satDelta, target);

		solverTime += (System.currentTimeMillis() - startTime);
		return model;
	}

	@Override
	protected Map<Variable, Object> model(Instance instance) {
		// Wrapper function to calculate time consumption.
		long startTime = System.currentTimeMillis();
		Map<Variable, Object> hasModel;
		hasModel = solve1(instance);
		long a = (System.currentTimeMillis() - startTime);
		timeConsumption += a;
		if (hasModel != null) {
			satTimeConsumption += a;
		} else {
			unsatTimeConsumption += a;
		}
		return hasModel;
	}

	/**
	 * Calculates the average satDelta value of a given Expression.
	 *
	 * @param expr the given Expression.
	 * @return the average satDelta value.
	 */
	private Object[] calculateSATDelta(Expression expr) {
		Double result = 0.0;
		ModelGruliaVisitor gVisitor = new ModelGruliaVisitor();

		try {
			double referenceSatDelta = -1;

			for (int i = 0; i < REF_SOL_SIZE; i++) {
				gVisitor.setReferenceSolution(REFERENCE_SOLUTIONS[i]);
				expr.accept(gVisitor);
				referenceSatDelta = gVisitor.getResult();

				if (Math.round(referenceSatDelta) == 0) {
					// The sat-delta computation produced a hit
					// The reference solution satisfies the constraint
					satDeltaIs0++;
					expr.satDelta = referenceSatDelta;
					satCount++;
					return new Object[] {gVisitor.getModel(), referenceSatDelta};
				} else {
					// Record calculated delta
					result += referenceSatDelta;
				}
			}

			result = result / REF_SOL_SIZE; // calculate average satDelta
			expr.satDelta = result;    // record the final satDelta in expression
//			satDeltaValues.add(result);
//			totalVariableCount += VARS.size();
		} catch (VisitorException x) {
			result = null;
			log.fatal("encountered an exception -- this should not be happening!", x);
		}
		return new Object[] {null, result};
	}

	/**
	 * Finds reusable models for the given Expression from the given SATDelta
	 * value.
	 * <p>
	 * @param dummy entry containing SATDelta value for the model filtering
	 * @param expr the Expression to solve
	 * @return isSat - if the Expression could be satisfied from previous models
	 */
	private Map<Variable, Object> sharesModel(SatEntry dummy, Expression expr) {
		/*
		 * Strategy:
		 * Check if satDelta is in table
		 * If in table -> test if model satisfies
		 * If not take next (k) closest SAT-Delta
		 * If not satisfied, call solver
		 */
		if (SAT_REPO.size() != 0) {
			long start = System.currentTimeMillis();
			long start1;
			List<SatEntry> models = SAT_REPO.extract(dummy, K);
			modelsExtractionTime += (System.currentTimeMillis() - start);
			if (models == null || models.isEmpty()) {
				satCacheMissCount++;
				return null;
			}

			start = System.currentTimeMillis();
			for (SatEntry model : models) {
				// extract model
				if (model == null) {
					break;
				}

				// test model satisfiability
				start1 = System.currentTimeMillis();
				ModelGruliaExpressionEvaluator exprSATCheck = new ModelGruliaExpressionEvaluator(model.getSolution());
				try {
					expr.accept(exprSATCheck);
				} catch (VisitorException x) {
					log.fatal("encountered an exception -- this should not be happening!", x);
				}
				modelEvalTime += (System.currentTimeMillis() - start1);

				if (exprSATCheck.isSat()) {
					// already in repo,
					// don't have to do anything
					satModelCount++;
					satCount++;
					satCacheHitCount++;
//                    modelNumbers[i]++;
					modelsTestingTime += (System.currentTimeMillis() - start);
					return trimSolution(dummy, model);
				} else {
					modelsTested++;
				}
			}
			modelsTestingTime += (System.currentTimeMillis() - start);
		} // else :: repo empty -> check unsat cache

		satCacheMissCount++;
		return null;
	}

	/**
	 * Reduce the model to only the necessary information
	 * @param dummy - as refence for the needed assignments
	 * @param model - the model to reduce
	 * @return Map model - the reduced model
	 */
	private Map<Variable, Object> trimSolution(SatEntry dummy, SatEntry model) {
		if (dummy.getSize() == model.getSize()) {
			// equal, soo just return it
			return model.getSolution();
		} else if (dummy.getSize() < model.getSize()){
			// some assignments to cut off
			Map<Variable, Object> trimmedModel = new HashMap<>();
			TreeMap<Variable, Object> tm = new TreeMap<>(model.getSolution());
			for (Map.Entry e : tm.entrySet()) {
				trimmedModel.put((Variable) e.getKey(), e.getValue());
				if (trimmedModel.size() == dummy.getSize()) {
					return trimmedModel;
				}
			}
			return trimmedModel;
		} else {
			// this should have been dealt with either the zero substitution
			// or the var-size filtering when getting closest models
			log.error("This should not happen");
			// possible hack can be to add v=0 assignments for missing model size.
			return model.getSolution();
		}
	}

	/**
	 * Looks for shared unsat cores in the given Expression from the given SATDelta
	 * value.
	 * <p>
	 * @param dummy entry containing SATDelta value for the core filtering
	 * @param expr the Expression to solve
	 * @return SAT - if the Expression shares some unsat core from previous cores
	 */
	private Set<Expression> sharesUnsatCores(UnsatEntry dummy, Expression expr) {
		/*
		 * Strategy:
		 * Check if SAT-Delta is in table
		 * If in table -> test if shares unsat cores
		 * If not take next (k) closest SAT-Delta
		 * If not sharing unsat core, call solver
		 */
		if (UNSAT_REPO.size() != 0) {
			long start0 = System.currentTimeMillis();
			List<UnsatEntry> cores = UNSAT_REPO.extract(dummy, K);
			coreExtractionTime += (System.currentTimeMillis() - start0);
			boolean shares;

			if (cores == null || cores.isEmpty()) {
				unsatCacheMissCount++;
				return null;
			}

			start0 = System.currentTimeMillis();
			String exprStr = expr.toString();
			for (UnsatEntry entry : cores) {
				// extract expression
				if (entry == null) {
					break;
				}

				Set<Expression> core = entry.getSolution();
				if (core.size() != 0) {
					shares = true;
					for (Expression clause : core) {
						if (!exprStr.contains("(" + clause.toString() + ")")) {
							shares = false;
							break;
						}
					}
					if (shares) {
						sharesUnsatCoresCount++;
						unsatCount++;
						unsatCacheHitCount++;
						coreTestingTime += (System.currentTimeMillis() - start0);
						return core;
					} else {
						unsatCoresTested++;
					}
				} else {
//                    log.log(Level.WARN, "Core with no entry found");
				}
			}
			coreTestingTime += (System.currentTimeMillis() - start0);
		} // else :: repo empty -> call solver

		unsatCacheMissCount++;
		return null;
	}

	/**
	 * Calls the model checker to solve the Expression.
	 * If the Expression could be satisfied, its model and SATDelta value is stored.
	 *
	 * @param satDelta the SATDelta value to store as key.
	 * @param expr     the Expression to store
	 * @return SAT - if the Expression could be satisfied.
	 */
	private Map<Variable, Object> callSolver(Double satDelta, Expression expr) {
		// Get model for formula
//		boolean isSat;
		Instance i = new Instance(null, null, expr);
		solverCount++;

		mcs.processRequest(i);
		if (ModelCoreService.isSat(i)) {
			Map<Variable, Object> model = ModelCoreService.getModel(i);
			SatEntry newEntry = new SatEntry(satDelta, model);

			SAT_REPO.add(newEntry);
//			satDeltaValuesInRepo.add(satDelta);
			satEntryCount++;
			satCount++;
//			isSat = true;
			return model;
		} else {
			Set<Expression> core = ModelCoreService.getCore(i);
			UnsatEntry newEntry = new UnsatEntry(satDelta, core);
			UNSAT_REPO.add(newEntry);
			unsatEntryCount++;
			unsatCount++;
//			isSat = false;
			return null;
		}
	}

	/**
	 * Display the list of values as a histogram.
	 *
	 * @param reporter the output reporter
	 * @param list     the list containing values of type Double
	 */
	private void displayAsHistogram(Reporter reporter, ArrayList<Double> list) {
		HashMap<Double, Integer> histogram = new HashMap<Double, Integer>();

		for (Double x : list) {
			histogram.merge(x, 1, (a, b) -> a + b);
		}

		reporter.report(getClass().getSimpleName(), histogram.toString());
	}

	/**
	 * Display histogram array of values
	 *
	 * @param reporter
	 * @param a
	 */
	private void displayAsHistogram(Reporter reporter, int[] a) {
		StringBuilder s = new StringBuilder();
		s.append("{");
		int n = K - 1;
		for (int i = 0; i < n; i++) {
			s.append(i + 1).append("=").append(a[i]).append(", ");
		}
		s.append(K + "=").append(a[n]).append("}");
		reporter.report(getClass().getSimpleName(), s.toString());
	}

	/**
	 * Calculates the distribution of the SATDelta values, for the reporter.
	 *
	 * @param reporter the reporter
	 * @param list     SATDelta values
	 */
	private void distribution(Reporter reporter, ArrayList<Double> list) {
		double avg = 0.0;
		Collections.sort(list);

		reporter.report(getClass().getSimpleName(), "minSATDelta = " + list.get(0));
		reporter.report(getClass().getSimpleName(), "maxSATDelta = " + list.get(list.size() - 1));

		for (double x : list) {
			avg += x;
		}

		avg = avg / list.size();

		reporter.report(getClass().getSimpleName(), "meanSATDelta = " + avg);

		double sum = 0.0;

		for (double x : list) {
			sum += Math.pow((x - avg), 2);
		}

		double sigma = sum / (list.size() - 1);
		sigma = Math.sqrt(sigma);

		reporter.report(getClass().getSimpleName(), "standard deviation of SATDelta = " + sigma);

		double cv = sigma / avg;

		reporter.report(getClass().getSimpleName(), "coefficient of variation of SATDelta = " + cv);
	}

	@Override
	public void report(Reporter reporter) {
		mcs.report(reporter);
//        reporter.report(getClass().getSimpleName(), "totalVariables = " + totalVariableCount);
//        reporter.report(getClass().getSimpleName(), "totalNewVariables = " + newVariableCount);
//        reporter.report(getClass().getSimpleName(), "totalOldVariables = " + (totalVariableCount-newVariableCount));
//		reporter.report(getClass().getSimpleName(), "total SAT queries = " + totSatCount);
		// COUNTS
		reporter.report(getClass().getSimpleName(), "invocations = " + invocationCount);
		reporter.report(getClass().getSimpleName(), "satQueries = " + satCount);
		reporter.report(getClass().getSimpleName(), "unsatQueries = " + unsatCount);
		reporter.report(getClass().getSimpleName(), "satCacheHitCount = " + satCacheHitCount);
		reporter.report(getClass().getSimpleName(), "satCacheMissCount = " + satCacheMissCount);
		reporter.report(getClass().getSimpleName(), "unsatCacheHitCount = " + unsatCacheHitCount);
		reporter.report(getClass().getSimpleName(), "unsatCacheMissCount = " + unsatCacheMissCount);
		reporter.report(getClass().getSimpleName(), "solverCalls = " + solverCount);
		reporter.report(getClass().getSimpleName(), "models_tested = " + modelsTested);
		reporter.report(getClass().getSimpleName(), "models_reused = " + satModelCount);
		reporter.report(getClass().getSimpleName(), "unsatCores_tested = " + unsatCoresTested);
		reporter.report(getClass().getSimpleName(), "unsatCores_reused = " + sharesUnsatCoresCount);
		reporter.report(getClass().getSimpleName(), "satEntries added to cache = " + satEntryCount);
		reporter.report(getClass().getSimpleName(), "unsatEntries added to cache = " + unsatEntryCount);
		reporter.report(getClass().getSimpleName(), "satDeltaIs0 = " + satDeltaIs0);
//		reporter.report(getClass().getSimpleName(), "total Models reused = " + totSatModelCount);

		// TIMES
		reporter.report(getClass().getSimpleName(), "timeConsumption = " + timeConsumption);
		reporter.report(getClass().getSimpleName(), "satTimeConsumption = " + satTimeConsumption);
		reporter.report(getClass().getSimpleName(), "unsatTimeConsumption = " + unsatTimeConsumption);
		reporter.report(getClass().getSimpleName(), "cacheLoadTime = " + cacheLoadTimeConsumption);
		reporter.report(getClass().getSimpleName(), "K_Model_extractTime = " + modelsExtractionTime);
		reporter.report(getClass().getSimpleName(), "K_Model_testingTime = " + modelsTestingTime);
		reporter.report(getClass().getSimpleName(), "model_evaluationTime = " + modelEvalTime);
		reporter.report(getClass().getSimpleName(), "core_extractTime = " + coreExtractionTime);
		reporter.report(getClass().getSimpleName(), "core_testingTime = " + coreTestingTime);
		reporter.report(getClass().getSimpleName(), "satDelta_computationTime = " + satDeltaCalculationTime);
		reporter.report(getClass().getSimpleName(), "satCache_checkTime = " + satCacheTime);
		reporter.report(getClass().getSimpleName(), "unsatCache_checkTime = " + unsatCacheTime);
		reporter.report(getClass().getSimpleName(), "solverCallTime = " + solverTime);
/*
		if (false) {
			// Sat delta values
			reporter.report(getClass().getSimpleName(), "totalSatDeltaValues distribution: ");
			distribution(reporter, satDeltaValues);
			reporter.report(getClass().getSimpleName(), "SatDeltaValues in Repository distribution: ");
			distribution(reporter, satDeltaValuesInRepo);

			reporter.report(getClass().getSimpleName(), "Display SAT-Delta as histogram: ");
			displayAsHistogram(reporter, satDeltaValues);
			reporter.report(getClass().getSimpleName(), "Display SAT-Delta (in repo) as histogram: ");
			displayAsHistogram(reporter, satDeltaValuesInRepo);
		}

		if (false) {
			// Model numbers
			reporter.report(getClass().getSimpleName(), "Display ModelNumbers as histogram: ");
			displayAsHistogram(reporter, modelNumbers);
		}
*/
	}

	/**
	 * Upon service shutdown, flush repository data (to the secondary Green store)
	 */
	@Override
	public void shutdown() {
//		store.flushAll();
		SAT_REPO.flushAll();
		UNSAT_REPO.flushAll();
		super.shutdown();
	}

	private static class ExprVisitor extends Visitor {

		private SortedSet<IntVariable> variableSet;

		private boolean unsatisfiable;

		private boolean linearInteger;

		public ExprVisitor() {
			variableSet = new TreeSet<IntVariable>();
			unsatisfiable = false;
			linearInteger = true;
		}

		public SortedSet<IntVariable> getVariableSet() {
			return variableSet;
		}

		@Override
		public void postVisit(Variable variable) {
			if (linearInteger && !unsatisfiable) {
				if (variable instanceof IntVariable) {
					variableSet.add((IntVariable) variable);
				} else {
					linearInteger = false;
				}
			}
		}
	}
}

class ModelGruliaVisitor extends Visitor {

	/*
	 * Local stack to calculate the SAT-Delta value
	 */
	private Stack<Integer> stack = new Stack<Integer>();

	private Integer referenceSolution;
	private Double result;
	private Map<Variable, Object> newVars;

	/**
	 *
	 * @return Built model
	 */
	public Map<Variable, Object> getModel() {
		return newVars;
	}

	void setReferenceSolution(int value) {
		stack.clear();
		newVars = new HashMap<>();
		result = null;
		this.referenceSolution = value;
	}

	/**
	 * @return x - SAT-Delta value
	 */
	public double getResult() {
		if (result == null) {
			result = 0.0 + stack.pop();
		}
		return result;
	}

	@Override
	public void postVisit(Expression expression) throws VisitorException {
		super.postVisit(expression);
	}

	@Override
	public void postVisit(Variable variable) throws VisitorException {
		super.postVisit(variable);

//		if (!GruliaService.newVariables.contains((IntVariable) variable)) {
//			GruliaService.newVariables.add((IntVariable) variable);
//			GruliaService.newVariableCount++;
//		}
		if (!newVars.containsKey(variable)) {
			newVars.put(variable, new IntConstant(referenceSolution));
		}

		stack.push(referenceSolution);
	}

	@Override
	public void postVisit(IntConstant constant) throws VisitorException {
		super.postVisit(constant);
		stack.push(constant.getValue());
	}

	@Override
	public void postVisit(Operation operation) throws VisitorException {
		super.postVisit(operation);
		calculateSatDelta(operation, stack);
	}

	/**
	 * Calculates the SAT-Delta value for a given operation and
	 * pushes the result to a given stack.
	 * <p>
	 * The distance of an expression from a set of reference models is called
	 * "SatDelta" and is defined in the paper:
	 * "Heuristically Matching Formula Solution Spaces To Efficiently Reuse Solutions"
	 * published at the International Conference on Software Engineering (ICSE'17)
	 * by Andrea Aquino, Giovanni Denaro and Mauro Pezze'.
	 *
	 * @param operation the current operation working with
	 * @param stack     the stack to push the result to
	 */
	private void calculateSatDelta(Operation operation, Stack<Integer> stack) {
		Integer l = null;
		Integer r = null;

		int arity = operation.getOperator().getArity();
		if (arity == 2) {
			if (!stack.isEmpty()) {
				r = stack.pop();
			}
			if (!stack.isEmpty()) {
				l = stack.pop();
			}
		}

		Operation.Operator op = operation.getOperator();
		assert (l != null);
		assert (r != null);

		switch (op) {
			case OR:
				// aggregate OR by taking minimum(LHS, RHS)
				stack.push(Math.min(l, r));
				break;
			case AND:
				// aggregate AND by adding LHS and RHS
			case ADD:
				stack.push(l + r);
				break;
			case LT:
				stack.push(l >= r ? (l - r) + 1 : 0);
				break;
			case LE:
				stack.push(l > r ? l - r : 0);
				break;
			case GT:
				stack.push(l <= r ? (r - l) + 1 : 0);
				break;
			case GE:
				stack.push(l < r ? r - l : 0);
				break;
			case EQ:
				stack.push(!l.equals(r) ? Math.abs(l - r) : 0);
				break;
			case NE:
				stack.push(l.equals(r) ? 1 : 0);
				break;
			case MUL:
				stack.push(l * r);
				break;
			case SUB:
				stack.push(Math.abs(Math.abs(r) - Math.abs(l)));
				break;
			case MOD:
				stack.push(l % r);
				break;
			default:
				stack.push(0);
				break;
		}
	}
}

class ModelGruliaExpressionEvaluator extends Visitor {

	/*
	 * Local stack for the evaluation of the expression.
	 */
	private Stack<Object> evalStack = new Stack<Object>();

	private Map<Variable, Object> modelMap;

	ModelGruliaExpressionEvaluator(Map<Variable, Object> modelMap) {
		super();
		this.modelMap = modelMap;
	}

	/**
	 * Public method to get the satisfiability status of the
	 * expression.
	 *
	 * @return SAT - true if the expression is satisfied,
	 * - false otherwise
	 */
	Boolean isSat() {
		return (Boolean) evalStack.pop();
	}

	@Override
	public void postVisit(Expression expression) throws VisitorException {
		super.postVisit(expression);
	}

	@Override
	public void postVisit(Variable variable) throws VisitorException {
		super.postVisit(variable);
		Constant val = (Constant) modelMap.get(variable);
		int value = -1;
		if (val == null) {
			value = 0;
		} else if (val instanceof IntConstant) {
			value = ((IntConstant) val).getValue();
		} else {
			value = 0;
		}
		evalStack.push(value);
	}

	@Override
	public void postVisit(IntConstant constant) throws VisitorException {
		super.postVisit(constant);
		evalStack.push(constant.getValue());
	}

	@Override
	public void postVisit(Operation operation) throws VisitorException {
		super.postVisit(operation);

		boolean isSat = false;
		Object l = null;
		Object r = null;

		int arity = operation.getOperator().getArity();
		if (arity == 2) {
			if (!evalStack.isEmpty()) {
				r = evalStack.pop();
			}
			if (!evalStack.isEmpty()) {
				l = evalStack.pop();
			}
		} else if (arity == 1) {
			if (!evalStack.isEmpty()) {
				l = evalStack.pop();
			}
		}

		Operation.Operator op = operation.getOperator();

		// Vars for casting
		Integer left_i, right_i;
		Boolean left_b, right_b;

		// test sat
		switch (op) {
			case LE:
				left_i = (Integer) l;
				right_i = (Integer) r;
				assert (left_i != null && right_i != null);

				isSat = (left_i <= right_i);
				evalStack.push(isSat);
				break;
			case LT:
				left_i = (Integer) l;
				right_i = (Integer) r;
				assert (left_i != null && right_i != null);

				isSat = (left_i < right_i);
				evalStack.push(isSat);
				break;
			case AND:
				left_b = (Boolean) l;
				right_b = (Boolean) r;
				assert (left_b != null && right_b != null);

				isSat = (left_b && right_b);
				evalStack.push(isSat);
				break;
			case ADD:
				left_i = (Integer) l;
				right_i = (Integer) r;
				assert (left_i != null && right_i != null);

				evalStack.push(left_i + right_i);
				break;
			case SUB:
				left_i = (Integer) l;
				right_i = (Integer) r;
				assert (left_i != null && right_i != null);

				evalStack.push(left_i - right_i);
				break;
			case EQ:
				left_i = (Integer) l;
				right_i = (Integer) r;
				assert (left_i != null && right_i != null);

				isSat = (left_i.equals(right_i));
				evalStack.push(isSat);
				break;
			case GE:
				left_i = (Integer) l;
				right_i = (Integer) r;
				assert (left_i != null && right_i != null);

				isSat = (left_i >= right_i);
				evalStack.push(isSat);
				break;
			case GT:
				left_i = (Integer) l;
				right_i = (Integer) r;
				assert (left_i != null && right_i != null);

				isSat = (left_i > right_i);
				evalStack.push(isSat);
				break;
			case MUL:
				left_i = (Integer) l;
				right_i = (Integer) r;
				assert (left_i != null && right_i != null);

				evalStack.push(left_i * right_i);
				break;
			case OR:
				left_b = (Boolean) l;
				right_b = (Boolean) r;
				assert (left_b != null && right_b != null);

				isSat = (left_b || right_b);
				evalStack.push(isSat);
				break;
			case NE:
				left_i = (Integer) l;
				right_i = (Integer) r;
				assert (left_i != null && right_i != null);

				isSat = (!left_i.equals(right_i));
				evalStack.push(isSat);
				break;
			case MOD:
				left_i = (Integer) l;
				right_i = (Integer) r;
				assert (left_i != null && right_i != null);

				evalStack.push(left_i % right_i);
				break;
			default:
				break;
		}
	}
}
