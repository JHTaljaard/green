package za.ac.sun.cs.green.service;

import za.ac.sun.cs.green.Green;
import za.ac.sun.cs.green.Instance;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Variable;
import za.ac.sun.cs.green.service.grulia.gruliastore.SatEntry;
import za.ac.sun.cs.green.service.grulia.gruliastore.UnsatEntry;
import za.ac.sun.cs.green.util.Reporter;

import java.util.Map;
import java.util.Set;

/**
 * @author JH Taljaard (USnr 18509193)
 * @author Willem Visser (Supervisor)
 * @author Jaco Geldenhuys (Supervisor)
 */
public abstract class ModelCoreService extends BasicService {

	public static final String SERVICE_KEY = "MODELCORE:";

	public static final String SAT_KEY = "-SAT";

	public static final String MODEL_KEY = "-MODEL";

	public static final String CORE_KEY = "-CORE";

	private int invocationCount = 0;

	protected int cacheHitCount = 0;
	protected int satHitCount = 0;
	protected int unsatHitCount = 0;

	protected int cacheMissCount = 0;
	protected int satMissCount = 0;
	protected int unsatMissCount = 0;

	private long timeConsumption = 0;
	protected long storageTimeConsumption = 0;

	protected int satCount = 0;
	protected int unsatCount = 0;

	public ModelCoreService(Green solver) {
		super(solver);
	}

	@Override
	public void report(Reporter reporter) {
		reporter.report(getClass().getSimpleName(), "invocationCount = " + invocationCount);
		reporter.report(getClass().getSimpleName(), "cacheHitCount = " + cacheHitCount);
		reporter.report(getClass().getSimpleName(), "satCacheHitCount = " + satHitCount);
		reporter.report(getClass().getSimpleName(), "unsatCacheHitCount = " + unsatHitCount);
		reporter.report(getClass().getSimpleName(), "cacheMissCount = " + cacheMissCount);
		reporter.report(getClass().getSimpleName(), "satCacheMissCount = " + satMissCount);
		reporter.report(getClass().getSimpleName(), "unsatCacheMissCount = " + unsatMissCount);
		reporter.report(getClass().getSimpleName(), "timeConsumption = " + timeConsumption);
		reporter.report(getClass().getSimpleName(), "storageTimeConsumption = " + storageTimeConsumption);
		reporter.report(getClass().getSimpleName(), "satQueries = " + satCount);
		reporter.report(getClass().getSimpleName(), "unssatQueries = " + unsatCount);
	}

	@Override
	public Object allChildrenDone(Instance instance, Object result) {
		return instance;
	}

	@Override
	public Set<Instance> processRequest(Instance instance) {
		if (instance.getData(SAT_KEY) == null) {
			solve3(instance);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private void solve0(Instance instance) {
		invocationCount++;
		/*--- NO CACHING: ---*
		cacheMissCount++;
		ModelCore modelCore = solve1(instance);
		Boolean isSat = modelCore.getIsSat();
		instance.setData(SAT_KEY, isSat);
		if (isSat) {
			instance.setData(MODEL_KEY, modelCore.getModel());
		} else {
			instance.setData(CORE_KEY, modelCore.getCore());
		}
		/*------*/

		/*--- EXPERIMENTAL CACHING: ---*/
		Map<Variable, Object> model = null;
		Set<Expression> core = null;
		SatEntry se = null;
		UnsatEntry ue = null;
		String key = SERVICE_KEY + instance.getFullExpression().getCachedString();
		long tmpConsumption = 0L;
		long start = System.currentTimeMillis();
		Boolean isSat = (Boolean) store.get(key + SAT_KEY);
		if (isSat == null) {
			cacheMissCount++;
			long startTime = System.currentTimeMillis();
			ModelCore modelCore = solve1(instance);
			timeConsumption += (System.currentTimeMillis() - startTime);
			tmpConsumption = System.currentTimeMillis() - startTime;
			isSat = modelCore.getIsSat();
			store.put(key + SAT_KEY, isSat);
			if (isSat) {
				satMissCount++;
				// TODO: Change to storing model not se
				model = modelCore.getModel();
				se = new SatEntry(instance.getExpression().satDelta, model);
//				store.put(key + "-MODEL", (HashMap<Variable, Constant>) model);
				store.put(key + MODEL_KEY, se);
			} else {
				unsatMissCount++;
				// TODO: Change to storing core not ue
				core = modelCore.getCore();
				ue = new UnsatEntry(instance.getExpression().satDelta, core);
//                store.put(key + "-CORE", (HashSet<Expression>) core);
				store.put(key + CORE_KEY, ue);
			}
		} else {
			if (isSat) {
				satHitCount++;
//				model = (HashMap<Variable, Constant>) store.get(key + "-MODEL");
				se = (SatEntry) store.get(key + MODEL_KEY);
			} else {
				unsatHitCount++;
//				core = (HashSet<Expression>) store.get(key + "-CORE");
				ue = (UnsatEntry) store.get(key + CORE_KEY);
			}
			cacheHitCount++;
		}
		instance.setData(SAT_KEY, isSat);
		if (isSat) {
//			instance.setData(MODEL_KEY, model);
			instance.setData(MODEL_KEY, se);
		} else {
//			instance.setData(CORE_KEY, core);
			instance.setData(CORE_KEY, ue);
		}
		/*------*/
		storageTimeConsumption += ((System.currentTimeMillis() - start) - tmpConsumption);
	}

	private void solve3(Instance instance) {
		invocationCount++;
		Map<Variable, Object> model = null;
		Set<Expression> core = null;
		SatEntry se = null;
		UnsatEntry ue = null;
		long startTime = System.currentTimeMillis();
		ModelCore modelCore = solve1(instance);
		timeConsumption += (System.currentTimeMillis() - startTime);
		boolean isSat = modelCore.getIsSat();
		instance.setData(SAT_KEY, isSat);
		if (isSat) {
			satMissCount++;
			// TODO: Change to storing model not se
			model = modelCore.getModel();
			se = new SatEntry(instance.getExpression().satDelta, model);
			instance.setData(MODEL_KEY, se);
		} else {
			unsatMissCount++;
			// TODO: Change to storing core not ue
			core = modelCore.getCore();
			ue = new UnsatEntry(instance.getExpression().satDelta, core);
			instance.setData(CORE_KEY, ue);
		}
	}

	private ModelCore solve1(Instance instance) {
		long startTime = System.currentTimeMillis();
		ModelCore modelCore = modelCore(instance);
		timeConsumption += System.currentTimeMillis() - startTime;
		return modelCore;
	}

	protected abstract ModelCore modelCore(Instance instance);

	public static final Boolean isSat(Instance instance) {
		return (Boolean) instance.getData(SAT_KEY);
	}

	@SuppressWarnings("unchecked")
	public static final Map<Variable, Object> getModel(Instance instance) {
		return ((SatEntry) instance.getData(MODEL_KEY)).getSolution();
	}

	@SuppressWarnings("unchecked")
	public static final Set<Expression> getCore(Instance instance) {
		return ((UnsatEntry) instance.getData(CORE_KEY)).getSolution();
	}

	public static class ModelCore {
		private final Boolean isSat;
		private final Map<Variable, Object> model;
		private final Set<Expression> core;

		public ModelCore(final Boolean isSat, final Map<Variable, Object> model, final Set<Expression> core) {
			this.isSat = isSat;
			this.model = model;
			this.core = core;
		}

		public Boolean getIsSat() {
			return isSat;
		}

		public Map<Variable, Object> getModel() {
			return model;
		}

		public Set<Expression> getCore() {
			return core;
		}
	}
}
