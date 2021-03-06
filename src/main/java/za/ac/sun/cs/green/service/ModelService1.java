package za.ac.sun.cs.green.service;

import za.ac.sun.cs.green.Green;
import za.ac.sun.cs.green.Instance;
import za.ac.sun.cs.green.expr.Variable;
import za.ac.sun.cs.green.util.Reporter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public abstract class ModelService1 extends BasicService {

	private static final String SERVICE_KEY = "MODEL:";

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

	public ModelService1(Green solver) {
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
		return instance.getData(getClass());
	}

	@Override
	public Set<Instance> processRequest(Instance instance) {
		@SuppressWarnings("unchecked")
		Map<Variable, Object> result = (Map<Variable, Object>) instance.getData(getClass());
		if (result == null) {
			result = solve0(instance);
			if (result != null) {
				instance.setData(getClass(), result);
			}
		}
		return null;
	}

	private Map<Variable, Object> solve0(Instance instance) {
		invocationCount++;
		HashMap<Variable, Object> result = (HashMap<Variable, Object>) instance.getData(getClass());
		if (result == null) {
			result = solve1(instance);
			if (result != null) {
				instance.setData(getClass(), result);
			}
		}
//        assert result != null;
		if (result != null)
			satCount++;
		else
			unsatCount++;
		return null;
	}

	private HashMap<Variable, Object> solve1(Instance instance) {
		HashMap<Variable, Object> result = (HashMap<Variable, Object>) model(instance);
		return result; // change this!
	}

	protected abstract Map<Variable, Object> model(Instance instance);

}
