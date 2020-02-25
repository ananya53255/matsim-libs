/* *********************************************************************** *
 * project: org.matsim.*
 * CharyparNagelScoringConfigGroup.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.core.config.groups;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.api.internal.MatsimParameters;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.PtConstants;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.ScoringParameters;

import java.util.*;

/**
 * Design decisions:
 * <ul>
 * <li>I have decided to modify those setters/getters that do not use SI units
 * such that the units are attached. This means all the utility parameters which
 * are "per hour" instead of "per second". kai, dec'10
 * <li>Note that a similar thing is not necessary for money units since money
 * units do not need to be specified (they are always implicit). kai, dec'10
 * <li>The parameter names in the config file are <i>not</i> changed in this way
 * since this would mean a public api change. kai, dec'10
 * </ul>
 * 
 * @author nagel
 *
 */
public final class PlanCalcScoreConfigGroup extends ReflectiveConfigGroup {

	private static final Logger log = Logger.getLogger(PlanCalcScoreConfigGroup.class);

	public static final String GROUP_NAME = "planCalcScore";

	private static final String LEARNING_RATE = "learningRate";
	private static final String BRAIN_EXP_BETA = "BrainExpBeta";
	private static final String PATH_SIZE_LOGIT_BETA = "PathSizeLogitBeta";
	private static final String LATE_ARRIVAL = "lateArrival";
	private static final String EARLY_DEPARTURE = "earlyDeparture";
	private static final String PERFORMING = "performing";

	private static final String WAITING = "waiting";
	private static final String WAITING_PT = "waitingPt";

	private static final String WRITE_EXPERIENCED_PLANS = "writeExperiencedPlans";

	private static final String MARGINAL_UTL_OF_MONEY = "marginalUtilityOfMoney";

	private static final String UTL_OF_LINE_SWITCH = "utilityOfLineSwitch";

	private double learningRate = 1.0;
	private double brainExpBeta = 1.0;
	private double pathSizeLogitBeta = 1.0;

	private boolean writeExperiencedPlans = false;

	private Double fractionOfIterationsToStartScoreMSA = null;

	private boolean usingOldScoringBelowZeroUtilityDuration = false;

	public PlanCalcScoreConfigGroup() {
		super(GROUP_NAME);

		this.addScoringParameters(new ScoringParameterSet());

		// what follows now has weird consequences:
		// * the material is added to the ScoringParameterSet of the default subpopulation
		// * if someone uses the following in the config.xml:
		//      < ... planCalcScore ... >
		//            <... modeParams ... >
		//                   < ... mode ... abc ... />
		//    then abc will be _added_ to the modes info below (same for activities)
		//  * if, however, someone uses in the config.xml:
		//      < ... planCalcScore ... >
		//            < ... scoringParameters ... >
		//                  <... modeParams ... >
		//                        < ... mode ... abc ... />
		//     (= fully hierarchical format), then the default modes will be removed before adding mode abc.  The reason for this is that the second
		//     syntax clears the scoring params for the default subpopulation.

		//  Unfortunately, it continues:
		//  * Normally, we need a "clear defaults with first configured entry" (see PlansCalcRouteConfigGroup).  Otherwise, we fail the write-read
		//  test: Assume we end up with a config that has _less_ material than the defaults.  Then we write this to file, and read it back in.  If
		//  the defaults are not cleared, they would now be fully there.
		//  * The reason why this works here is that all the material is written out with the fully hierarchical format.  I.e. it actually clears the
		//  defaults when being read it.

		// I am not sure if it can stay the way it is right now; took me several hours to understand it (and fix a problem we had not by
		// trial-and-error but by understanding the root cause).  Considerations:
		// * Easiest would be to not have defaults.  However, defaults are helpful in particular to avoid that everybody uses different parameters.
		// * We could also have the "manual addition triggers clearing" logic.  In PlansCalcRouteConfigGroup I now have this with a warning, which
		// can be switched off with a switch.  I find this a good solution; I am, however, not 100% certain that it is robust since that switch is a
		// "state" while "clearing the defaults" is an action, and I am not sure if they can be mapped into each other in all cases.
		// * We could, together with the previous point, disallow the not fully hierarchical format.

		// kai, dec'19

		// TODO: what happens when something gets added?
		this.addScoringParameters(new ScoringParameterSet(DEFAULT_SUBPOPULATION));
	}

	// ---

	private static final String USING_OLD_SCORING_BELOW_ZERO_UTILITY_DURATION = "usingOldScoringBelowZeroUtilityDuration";

	/**
	 * can't set this from outside java since for the time being it is not
	 * useful there. kai, dec'13
	 */
	private boolean memorizingExperiencedPlans = false;

	/**
	 * This is the key for customizable. where should this go?
	 */
	public static final String EXPERIENCED_PLAN_KEY = "experiencedPlan";
	public static final String DEFAULT_SUBPOPULATION = "default";

	// ---
	private static final String FRACTION_OF_ITERATIONS_TO_START_SCORE_MSA = "fractionOfIterationsToStartScoreMSA";

	public static String createStageActivityType( String mode ){
		return mode + " interaction";
	}

	// ---

	@StringGetter(FRACTION_OF_ITERATIONS_TO_START_SCORE_MSA)
	public Double getFractionOfIterationsToStartScoreMSA() {
		return fractionOfIterationsToStartScoreMSA;
	}

	@StringSetter(FRACTION_OF_ITERATIONS_TO_START_SCORE_MSA)
	public void setFractionOfIterationsToStartScoreMSA(Double fractionOfIterationsToStartScoreMSA) {
		testForLocked();
		this.fractionOfIterationsToStartScoreMSA = fractionOfIterationsToStartScoreMSA;
	}

	@StringGetter(LEARNING_RATE)
	public double getLearningRate() {
		return learningRate;
	}

	@StringSetter(LEARNING_RATE)
	public void setLearningRate(double learningRate) {
		testForLocked();
		this.learningRate = learningRate;
	}

	@StringGetter(BRAIN_EXP_BETA)
	public double getBrainExpBeta() {
		return brainExpBeta;
	}

	@StringSetter(BRAIN_EXP_BETA)
	public void setBrainExpBeta(double brainExpBeta) {
		testForLocked();
		this.brainExpBeta = brainExpBeta;
	}

	@StringGetter(PATH_SIZE_LOGIT_BETA)
	public double getPathSizeLogitBeta() {
		return pathSizeLogitBeta;
	}

	@StringSetter(PATH_SIZE_LOGIT_BETA)
	public void setPathSizeLogitBeta(double beta) {
		testForLocked();
		if (beta != 0.) {
			log.warn("Setting pathSizeLogitBeta different from zero is experimental.  KN, Sep'08");
		}
		this.pathSizeLogitBeta = beta;
	}

	@StringGetter(USING_OLD_SCORING_BELOW_ZERO_UTILITY_DURATION)
	public boolean isUsingOldScoringBelowZeroUtilityDuration() {
		return usingOldScoringBelowZeroUtilityDuration;
	}

	@StringSetter(USING_OLD_SCORING_BELOW_ZERO_UTILITY_DURATION)
	public void setUsingOldScoringBelowZeroUtilityDuration(boolean usingOldScoringBelowZeroUtilityDuration) {
		testForLocked();
		this.usingOldScoringBelowZeroUtilityDuration = usingOldScoringBelowZeroUtilityDuration;
	}

	@StringGetter(WRITE_EXPERIENCED_PLANS)
	public boolean isWriteExperiencedPlans() {
		return writeExperiencedPlans;
	}

	@StringSetter(WRITE_EXPERIENCED_PLANS)
	public void setWriteExperiencedPlans(boolean writeExperiencedPlans) {
		testForLocked();
		this.writeExperiencedPlans = writeExperiencedPlans;
	}

	@Override
	public final Map<String, String> getComments() {
		Map<String, String> map = super.getComments();
		map.put(FRACTION_OF_ITERATIONS_TO_START_SCORE_MSA,
				"fraction of iterations at which MSA score averaging is started. The matsim theory department "
						+ "suggests to use this together with switching off choice set innovation (where a similar switch exists), but it has not been tested yet.");
		map.put(USING_OLD_SCORING_BELOW_ZERO_UTILITY_DURATION,
				"There used to be a plateau between duration=0 and duration=zeroUtilityDuration. "
						+ "This caused durations to evolve to zero once they were below zeroUtilityDuration, causing problems.  Only use this switch if you need to be "
						+ "backwards compatible with some old results.  (changed nov'13)");
		map.put(PERFORMING,
				"[utils/hr] marginal utility of doing an activity.  normally positive.  also the opportunity cost of "
						+ "time if agent is doing nothing.  MATSim separates the resource value of time from the direct (dis)utility of travel time, see, e.g., "
						+ "Boerjesson and Eliasson, TR-A 59 (2014) 144-158.");
		map.put(LATE_ARRIVAL,
				"[utils/hr] utility for arriving late (i.e. after the latest start time).  normally negative");
		map.put(EARLY_DEPARTURE,
				"[utils/hr] utility for departing early (i.e. before the earliest end time).  Normally negative.  Probably "
						+ "implemented correctly, but not tested.");
		map.put(WAITING,
				"[utils/hr] additional marginal utility for waiting. normally negative. this comes on top of the opportunity cost of time.  Probably "
						+ "implemented correctly, but not tested.");
		map.put(WAITING_PT,
				"[utils/hr] additional marginal utility for waiting for a pt vehicle. normally negative. this comes on top of the opportunity cost "
						+ "of time. Default: if not set explicitly, it is equal to traveling_pt!!!");
		map.put(BRAIN_EXP_BETA,
				"logit model scale parameter. default: 1.  Has name and default value for historical reasons "
						+ "(see Bryan Raney's phd thesis).");
		map.put(LEARNING_RATE,
				"new_score = (1-learningRate)*old_score + learningRate * score_from_mobsim.  learning rates "
						+ "close to zero emulate score averaging, but slow down initial convergence");
		map.put(UTL_OF_LINE_SWITCH, "[utils] utility of switching a line (= transfer penalty).  Normally negative");
		map.put(MARGINAL_UTL_OF_MONEY,
				"[utils/unit_of_money] conversion of money (e.g. toll, distance cost) into utils. Normall positive (i.e. toll/cost/fare are processed as negative amounts of money).");
		map.put(WRITE_EXPERIENCED_PLANS,
				"write a plans file in each iteration directory which contains what each agent actually did, and the score it received.");

		return map;
	}
	
	/*
	 *
	 * @returns a list of all Activities over all Subpopulations (if existent)
	 */
	public Collection<String> getActivityTypes() {
		if (getScoringParameters(null) != null)
			return getScoringParameters(null).getActivityParamsPerType().keySet();
		else{
			Set<String> activities = new HashSet<>();
			getScoringParametersPerSubpopulation().values().forEach(item -> activities.addAll(item.getActivityParamsPerType().keySet()));
			return activities;
		}
	}

	/*
	 *
	 * @returns a list of all Modes over all Subpopulations (if existent)
	 */
	public Collection<String> getAllModes() {
		if (getScoringParameters(null) != null) {
			return getScoringParameters(null).getModes().keySet();
			
		} else {
			Set<String> modes = new HashSet<>();
			getScoringParametersPerSubpopulation().values().forEach(item -> modes.addAll(item.getModes().keySet()));
			return modes;
		}
		
	}
	
	public Map<String, ScoringParameterSet> getScoringParametersPerSubpopulation() {
		@SuppressWarnings("unchecked")
		final Collection<ScoringParameterSet> parameters = (Collection<ScoringParameterSet>) getParameterSets(
				ScoringParameterSet.SET_TYPE);
		final Map<String, ScoringParameterSet> map = new LinkedHashMap<>();

		for (ScoringParameterSet pars : parameters) {
			if (this.isLocked()) {
				pars.setLocked();
			}
			map.put(pars.getSubpopulation(), pars);
		}

		return map;
	}

	public ScoringParameterSet getScoringParameters(String subpopulation) {
		final ScoringParameterSet params = getScoringParametersPerSubpopulation().get(subpopulation);
		if (params == null) {
			throw new IllegalStateException("No scoring parameters for subpopulation "+subpopulation+
				"Please explicitly define scoring parameters for all subpopulations in "+GROUP_NAME
			);
		}
		return params;
	}

	public ScoringParameterSet getOrCreateScoringParameters(String subpopulation) {
		ScoringParameterSet params = getScoringParametersPerSubpopulation().get(subpopulation);

		if (params == null) {
			params = new ScoringParameterSet(subpopulation);
			this.addScoringParameters(params);
		}

		return params;
	}

	@Override
	public void addParameterSet(final ConfigGroup set) {
		switch (set.getName()) {
		case ScoringParameterSet.SET_TYPE:
			addScoringParameters((ScoringParameterSet) set);
			break;
		default:
			throw new IllegalArgumentException(set.getName());
		}
	}

	private void addScoringParameters( final ScoringParameterSet params ) {
		final ScoringParameterSet previous = this.getScoringParameters(params.getSubpopulation());

		if (previous != null) {
			log.info("scoring parameters for subpopulation " + previous.getSubpopulation() + " were just replaced.");

			final boolean removed = removeParameterSet(previous);
			if (!removed)
				throw new RuntimeException("problem replacing scoring params ");
		}

		super.addParameterSet(params);
	}

	public enum TypicalDurationScoreComputation {
		uniform, relative
	};

	/* parameter set handling */
	@Override
	public ConfigGroup createParameterSet(final String type) {
		switch (type) {
		case ActivityParams.SET_TYPE:
			return new ActivityParams();
		case ModeParams.SET_TYPE:
			return new ModeParams();
		case ScoringParameterSet.SET_TYPE:
			return new ScoringParameterSet();
		default:
			throw new IllegalArgumentException(type);
		}
	}

	@Override
	protected void checkParameterSet(final ConfigGroup module) {
		switch (module.getName()) {
		case ScoringParameterSet.SET_TYPE:
			if (!(module instanceof ScoringParameterSet)) {
				throw new RuntimeException("wrong class for " + module);
			}
			final String s = ((ScoringParameterSet) module).getSubpopulation();
			if (getScoringParameters(s) != null) {
				throw new IllegalStateException("already a parameter set for subpopulation " + s);
			}
			break;
		default:
			throw new IllegalArgumentException(module.getName());
		}
	}

	@Override
	protected final void checkConsistency(final Config config) {
		super.checkConsistency(config);
		
		if (getScoringParametersPerSubpopulation().size()>1){
			if (!getScoringParametersPerSubpopulation().containsKey(PlanCalcScoreConfigGroup.DEFAULT_SUBPOPULATION)){
				throw new RuntimeException("Using several subpopulations in "+PlanCalcScoreConfigGroup.GROUP_NAME+" requires defining a \""+PlanCalcScoreConfigGroup.DEFAULT_SUBPOPULATION+" \" subpopulation."
						+ " Otherwise, crashes can be expected.");
			}
		}
		if (config.plansCalcRoute().isInsertingAccessEgressWalk()) {
			// adding the interaction activities that result from access/egress
			// routing. this is strictly speaking
			// not a consistency check, but I don't know a better place where to
			// add this. kai, jan'18
			for (ScoringParameterSet scoringParameterSet : this.getScoringParametersPerSubpopulation().values()) {
				for (String mode : config.plansCalcRoute().getNetworkModes()) {
					String interactionActivityType = mode + " interaction";
					ActivityParams set = scoringParameterSet.getActivityParamsPerType().get(interactionActivityType);
					if (set == null) {
						ActivityParams params = new ActivityParams();
						params.setActivityType(interactionActivityType);
						params.setScoringThisActivityAtAll(false);
						scoringParameterSet.addActivityParams(params);
					}
				}

			}
		}

		for (ScoringParameterSet params : this.getScoringParametersPerSubpopulation().values()) {
			for (ActivityParams activityParams : params.getActivityParams()) {
				if (activityParams.isScoringThisActivityAtAll() && Time.isUndefinedTime(activityParams.getTypicalDuration())) {
					throw new RuntimeException("In activity type=" + activityParams.getActivityType()
							+ ", the typical duration is undefined.  This will lead to errors that are difficult to debug, "
							+ "so rather aborting here.");
				}
			}
		}

	}

	public boolean isMemorizingExperiencedPlans() {
		return this.memorizingExperiencedPlans;
	}

	public void setMemorizingExperiencedPlans(boolean memorizingExperiencedPlans) {
		this.memorizingExperiencedPlans = memorizingExperiencedPlans;
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// CLASSES
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public static class ActivityParams extends ReflectiveConfigGroup implements MatsimParameters {
		// in normal pgm execution, code will presumably lock instance of PlanCalcScoreConfigGroup, but not instance of
		// ActivityParams. I will try to pass the locked setting through the getters. kai, jun'15

		public final static String SET_TYPE = "activityParams";
		
		// ---

		private static final String TYPICAL_DURATION_SCORE_COMPUTATION = "typicalDurationScoreComputation";
		private TypicalDurationScoreComputation typicalDurationScoreComputation = TypicalDurationScoreComputation.relative;

		// --- typical duration:

		public static final String TYPICAL_DURATION = "typicalDuration";
		public static final String TYPICAL_DURATION_CMT = "typical duration of activity.  needs to be defined and non-zero.  in sec.";

		/**
		 * {@value TYPICAL_DURATION_CMT}
		 */
		@StringGetter(TYPICAL_DURATION)
		private String getTypicalDurationString() {
			return Time.writeTime(getTypicalDuration());
		}

		/**
		 * {@value TYPICAL_DURATION_CMT}
		 */
		public double getTypicalDuration() {
			return this.typicalDuration;
		}

		/**
		 * {@value TYPICAL_DURATION_CMT}
		 */
		@StringSetter(TYPICAL_DURATION)
		private ActivityParams setTypicalDuration(final String typicalDuration) {
			testForLocked();
			return setTypicalDuration(Time.parseTime(typicalDuration));
		}

		/**
		 * {@value TYPICAL_DURATION_CMT}
		 */
		public ActivityParams setTypicalDuration(final double typicalDuration) {
			testForLocked();
			this.typicalDuration = typicalDuration;
			return this ;
		}

		// --- activity type:

		public static final String ACTIVITY_TYPE = "activityType";
		private String type;
		public static final String ACVITITY_TYPE_CMT = "all activity types that occur in the plans file need to be defined by their own sections here";

		/**
		 * {@value -- ACVITITY_TYPE_CMT}
		 */
		@StringGetter(ACTIVITY_TYPE)
		public String getActivityType() {
			return this.type;
		}

		/**
		 * {@value -- ACVITITY_TYPE_CMT}
		 */
		@StringSetter(ACTIVITY_TYPE)
		public void setActivityType(final String type) {
			testForLocked();
			this.type = type;
		}

		// ---

		private double priority = 1.0;
		private double typicalDuration = Time.getUndefinedTime();
		private double minimalDuration = Time.getUndefinedTime();
		private double openingTime = Time.getUndefinedTime();
		private double latestStartTime = Time.getUndefinedTime();
		private double earliestEndTime = Time.getUndefinedTime();
		private double closingTime = Time.getUndefinedTime();

		public ActivityParams() {
			super(SET_TYPE);
		}

		public ActivityParams(final String type) {
			super(SET_TYPE);
			this.type = type;
		}

		@Override
		public Map<String, String> getComments() {
			final Map<String, String> map = super.getComments();
			// ---
			StringBuilder str = new StringBuilder();
			str.append("method to compute score at typical duration.  Options: | ");
			for (TypicalDurationScoreComputation value : TypicalDurationScoreComputation.values()) {
				str.append(value.name());
				str.append(" | ");
			}
			str.append("Use ");
			str.append(TypicalDurationScoreComputation.uniform.name());
			str.append(" for backwards compatibility (all activities same score; higher proba to drop long acts).");
			map.put(TYPICAL_DURATION_SCORE_COMPUTATION, str.toString());
			// ---
			map.put(TYPICAL_DURATION, TYPICAL_DURATION_CMT);
			// ---
			return map;
		}

		@StringGetter(TYPICAL_DURATION_SCORE_COMPUTATION)
		public TypicalDurationScoreComputation getTypicalDurationScoreComputation() {
			return this.typicalDurationScoreComputation;
		}

		@StringSetter(TYPICAL_DURATION_SCORE_COMPUTATION)
		public ActivityParams setTypicalDurationScoreComputation(TypicalDurationScoreComputation str) {
			testForLocked();
			this.typicalDurationScoreComputation = str;
			return this ;
		}

		@StringGetter("priority")
		public double getPriority() {
			return this.priority;
		}

		@StringSetter("priority")
		public ActivityParams setPriority(final double priority) {
			testForLocked();
			this.priority = priority;
			return this ;
		}

		@StringGetter("minimalDuration")
		private String getMinimalDurationString() {
			return Time.writeTime(getMinimalDuration());
		}

		public double getMinimalDuration() {
			return this.minimalDuration;
		}

		@StringSetter("minimalDuration")
		private ActivityParams setMinimalDuration(final String minimalDuration) {
			testForLocked();
			return setMinimalDuration(Time.parseTime(minimalDuration));
		}

		private static int minDurCnt = 0;

		public ActivityParams setMinimalDuration(final double minimalDuration) {
			testForLocked();
			if ((!Time.isUndefinedTime(minimalDuration)) && (minDurCnt < 1)) {
				minDurCnt++;
				log.warn(
						"Setting minimalDuration different from zero is discouraged.  It is probably implemented correctly, "
								+ "but there is as of now no indication that it makes the results more realistic.  KN, Sep'08"
								+ Gbl.ONLYONCE);
			}
			this.minimalDuration = minimalDuration;
			return this ;
		}

		@StringGetter("openingTime")
		private String getOpeningTimeString() {
			return Time.writeTime(getOpeningTime());
		}

		public double getOpeningTime() {
			return this.openingTime;
		}

		@StringSetter("openingTime")
		private ActivityParams setOpeningTime(final String openingTime) {
			testForLocked();
			setOpeningTime(Time.parseTime(openingTime));
			return this ;
		}

		public ActivityParams setOpeningTime(final double openingTime) {
			testForLocked();
			this.openingTime = openingTime;
			return this ;
		}

		@StringGetter("latestStartTime")
		private String getLatestStartTimeString() {
			return Time.writeTime(getLatestStartTime());
		}

		public double getLatestStartTime() {
			return this.latestStartTime;
		}

		@StringSetter("latestStartTime")
		private ActivityParams setLatestStartTime(final String latestStartTime) {
			testForLocked();
			setLatestStartTime(Time.parseTime(latestStartTime));
			return this ;
		}

		public ActivityParams setLatestStartTime(final double latestStartTime) {
			testForLocked();
			this.latestStartTime = latestStartTime;
			return this ;
		}

		@StringGetter("earliestEndTime")
		private String getEarliestEndTimeString() {
			return Time.writeTime(getEarliestEndTime());
		}

		public double getEarliestEndTime() {
			return this.earliestEndTime;
		}

		@StringSetter("earliestEndTime")
		private ActivityParams setEarliestEndTime(final String earliestEndTime) {
			testForLocked();
			setEarliestEndTime(Time.parseTime(earliestEndTime));
			return this ;
		}

		public ActivityParams setEarliestEndTime(final double earliestEndTime) {
			testForLocked();
			this.earliestEndTime = earliestEndTime;
			return this ;
		}

		@StringGetter("closingTime")
		private String getClosingTimeString() {
			return Time.writeTime(getClosingTime());
		}

		public double getClosingTime() {
			return this.closingTime;
		}

		@StringSetter("closingTime")
		private ActivityParams setClosingTime(final String closingTime) {
			testForLocked();
			setClosingTime(Time.parseTime(closingTime));
			return this ;
		}

		public ActivityParams setClosingTime(final double closingTime) {
			testForLocked();
			this.closingTime = closingTime;
			return this ;
		}

		// ---
		
		static final String SCORING_THIS_ACTIVITY_AT_ALL = "scoringThisActivityAtAll";

		private boolean scoringThisActivityAtAll = true;

		@StringGetter(SCORING_THIS_ACTIVITY_AT_ALL)
		public boolean isScoringThisActivityAtAll() {
			return scoringThisActivityAtAll;
		}

		@StringSetter(SCORING_THIS_ACTIVITY_AT_ALL)
		public ActivityParams setScoringThisActivityAtAll(boolean scoringThisActivityAtAll) {
			testForLocked();
			this.scoringThisActivityAtAll = scoringThisActivityAtAll;
			return this ;
		}
	}

	public static class ModeParams extends ReflectiveConfigGroup implements MatsimParameters {

		final static String SET_TYPE = "modeParams";
		
		private static final String MONETARY_DISTANCE_RATE = "monetaryDistanceRate";
		private static final String MONETARY_DISTANCE_RATE_CMT = "[unit_of_money/m] conversion of distance into money. Normally negative.";

		private static final String MARGINAL_UTILITY_OF_TRAVELING = "marginalUtilityOfTraveling_util_hr";

		private static final String CONSTANT = "constant";
		private static final String CONSTANT_CMT = "[utils] alternative-specific constant.  Normally per trip, but that is probably buggy for multi-leg trips.";

		public static final String MODE = "mode";
		
		static final String DAILY_MONETARY_CONSTANT = "dailyMonetaryConstant";
		static final String DAILY_UTILITY_CONSTANT = "dailyUtilityConstant";
		
		private String mode = null;
		private double traveling = -6.0;
		private double distance = 0.0;
		private double monetaryDistanceRate = 0.0;
		private double constant = 0.0;
		private double dailyMonetaryConstant = 0.0;
		private double dailyUtilityConstant = 0.0;

		// @Override public String toString() {
		// String str = super.toString();
		// str += "[mode=" + mode + "]" ;
		// str += "[const=" + constant + "]" ;
		// str += "[beta_trav=" + traveling + "]" ;
		// str += "[beta_dist=" + distance + "]" ;
		// return str ;
		// }

		public ModeParams(final String mode) {
			super(SET_TYPE);
			setMode(mode);
		}

		ModeParams() {
			super(SET_TYPE);
		}

		@Override
		public Map<String, String> getComments() {
			final Map<String, String> map = super.getComments();
			map.put(MARGINAL_UTILITY_OF_TRAVELING,
					"[utils/hr] additional marginal utility of traveling.  normally negative.  this comes on top "
							+ "of the opportunity cost of time");
			map.put("marginalUtilityOfDistance_util_m",
					"[utils/m] utility of traveling (e.g. walking or driving) per m, normally negative.  this is "
							+ "on top of the time (dis)utility.");
			map.put(MONETARY_DISTANCE_RATE, MONETARY_DISTANCE_RATE_CMT);
			map.put(CONSTANT, CONSTANT_CMT );
			map.put(DAILY_UTILITY_CONSTANT, "[utils] daily utility constant. "
					+ "default=0 to be backwards compatible");
			map.put(DAILY_MONETARY_CONSTANT, "[money] daily monetary constant. "
					+ "default=0 to be backwards compatible");
			return map;
		}

		@StringSetter(MODE)
		public ModeParams setMode(final String mode) {
			testForLocked();
			this.mode = mode;
			return this ;
		}
		@StringGetter(MODE)
		public String getMode() {
			return mode;
		}
		// ---
		@StringSetter(MARGINAL_UTILITY_OF_TRAVELING)
		public ModeParams setMarginalUtilityOfTraveling(double traveling) {
			testForLocked();
			this.traveling = traveling;
			return this ;
		}
		@StringGetter(MARGINAL_UTILITY_OF_TRAVELING)
		public double getMarginalUtilityOfTraveling() {
			return this.traveling;
		}
		// ---
		@StringGetter("marginalUtilityOfDistance_util_m")
		public double getMarginalUtilityOfDistance() {
			return distance;
		}
		@StringSetter("marginalUtilityOfDistance_util_m")
		public ModeParams setMarginalUtilityOfDistance(double distance) {
			testForLocked();
			this.distance = distance;
			return this ;
		}

		/**
		 * @return {@value #CONSTANT_CMT}
		 */
		// ---
		@StringGetter(CONSTANT)
		public double getConstant() {
			return this.constant;
		}
		/**
		 * @param constant -- {@value #CONSTANT_CMT}
		 */
		@StringSetter(CONSTANT)
		public ModeParams setConstant(double constant) {
			testForLocked();
			this.constant = constant;
			return this ;
		}
		// ---
		/**
		 * @return {@value #MONETARY_DISTANCE_RATE_CMT}
		 */
		@StringGetter(MONETARY_DISTANCE_RATE)
		public double getMonetaryDistanceRate() {
			return this.monetaryDistanceRate;
		}

		/**
		 * @param monetaryDistanceRate -- {@value #MONETARY_DISTANCE_RATE_CMT}
		 */
		@StringSetter(MONETARY_DISTANCE_RATE)
		public ModeParams setMonetaryDistanceRate(double monetaryDistanceRate) {
			testForLocked();
			this.monetaryDistanceRate = monetaryDistanceRate;
			return this ;
		}
		@StringGetter(DAILY_MONETARY_CONSTANT)
		public double getDailyMonetaryConstant() {
			return dailyMonetaryConstant;
		}

		@StringSetter(DAILY_MONETARY_CONSTANT)
		public ModeParams setDailyMonetaryConstant(double dailyMonetaryConstant) {
			this.dailyMonetaryConstant = dailyMonetaryConstant;
			return this ;
		}

		@StringGetter(DAILY_UTILITY_CONSTANT)
		public double getDailyUtilityConstant() {
			return dailyUtilityConstant;
		}

		@StringSetter(DAILY_UTILITY_CONSTANT)
		public ModeParams setDailyUtilityConstant(double dailyUtilityConstant) {
			this.dailyUtilityConstant = dailyUtilityConstant;
			return this ;
		}


	}

	public static class ScoringParameterSet extends ReflectiveConfigGroup {
		public static final String SET_TYPE = "scoringParameters";

		private ScoringParameterSet(final String subpopulation) {
			this();
			this.subpopulation = subpopulation;

			// TODO what happens shen adding things (erasing defaults)?

			this.addModeParams(new ModeParams(TransportMode.car));
			this.addModeParams(new ModeParams(TransportMode.pt));
			this.addModeParams(new ModeParams(TransportMode.walk));
			this.addModeParams(new ModeParams(TransportMode.bike));
			this.addModeParams(new ModeParams(TransportMode.ride));
			this.addModeParams(new ModeParams(TransportMode.other));

			this.addActivityParams( new ActivityParams("dummy").setTypicalDuration(2. * 3600. ) );
			// (this is there so that an empty config prints out at least one activity type, so that the explanations of this
			// important concept show up e.g. in defaultConfig.xml, created from the GUI. kai, jul'17
	//			params.setScoringThisActivityAtAll(false); // no longer minimal when included here. kai, jun'18

			// yyyyyy find better solution for this. kai, dec'15
			this.addActivityParams( new ActivityParams(createStageActivityType( TransportMode.car ) ).setScoringThisActivityAtAll(false ) );
			this.addActivityParams( new ActivityParams(createStageActivityType( TransportMode.pt )).setScoringThisActivityAtAll(false ) );
			// (need this for self-programmed pseudo pt. kai, nov'16)
			this.addActivityParams( new ActivityParams(createStageActivityType( TransportMode.bike ) ).setScoringThisActivityAtAll(false ) );
			this.addActivityParams( new ActivityParams(createStageActivityType( TransportMode.drt ) ).setScoringThisActivityAtAll(false ) );
			this.addActivityParams( new ActivityParams(createStageActivityType( TransportMode.taxi ) ).setScoringThisActivityAtAll(false ) );
			this.addActivityParams( new ActivityParams(createStageActivityType( TransportMode.other ) ).setScoringThisActivityAtAll(false ) );
			this.addActivityParams( new ActivityParams(createStageActivityType( TransportMode.walk ) ).setScoringThisActivityAtAll(false ) );
			// (bushwhacking_walk---network_walk---bushwhacking_walk)
		}

		private ScoringParameterSet() {
			super(SET_TYPE);
		}

		private String subpopulation = null;

		private double lateArrival = -18.0;
		private double earlyDeparture = -0.0;
		private double performing = +6.0;

		private double waiting = -0.0;

		private double marginalUtilityOfMoney = 1.0;

		private double utilityOfLineSwitch = -1;

		private Double waitingPt = null; // if not actively set by user, it will
											// later be set to "travelingPt".

		@StringGetter(LATE_ARRIVAL)
		public double getLateArrival_utils_hr() {
			return lateArrival;
		}

		@StringSetter(LATE_ARRIVAL)
		public void setLateArrival_utils_hr(double lateArrival) {
			testForLocked();
			this.lateArrival = lateArrival;
		}

		@StringGetter(EARLY_DEPARTURE)
		public double getEarlyDeparture_utils_hr() {
			return earlyDeparture;
		}

		@StringSetter(EARLY_DEPARTURE)
		public void setEarlyDeparture_utils_hr(double earlyDeparture) {
			testForLocked();
			this.earlyDeparture = earlyDeparture;
		}

		@StringGetter(PERFORMING)
		public double getPerforming_utils_hr() {
			return performing;
		}

		@StringSetter(PERFORMING)
		public void setPerforming_utils_hr(double performing) {
			this.performing = performing;
		}

		@StringGetter(MARGINAL_UTL_OF_MONEY)
		public double getMarginalUtilityOfMoney() {
			return marginalUtilityOfMoney;
		}

		@StringSetter(MARGINAL_UTL_OF_MONEY)
		public void setMarginalUtilityOfMoney(double marginalUtilityOfMoney) {
			testForLocked();
			this.marginalUtilityOfMoney = marginalUtilityOfMoney;
		}

		@StringGetter(UTL_OF_LINE_SWITCH)
		public double getUtilityOfLineSwitch() {
			return utilityOfLineSwitch;
		}

		@StringSetter(UTL_OF_LINE_SWITCH)
		public void setUtilityOfLineSwitch(double utilityOfLineSwitch) {
			testForLocked();
			this.utilityOfLineSwitch = utilityOfLineSwitch;
		}

		@StringGetter(WAITING)
		public double getMarginalUtlOfWaiting_utils_hr() {
			return this.waiting;
		}

		@StringSetter(WAITING)
		public void setMarginalUtlOfWaiting_utils_hr(final double waiting) {
			testForLocked();
			this.waiting = waiting;
		}

		@StringGetter("subpopulation")
		public String getSubpopulation() {
			return subpopulation;
		}

		/**
		 * This method is there to make the StringSetter/Getter automagic happy, but it is not meant to be used.
		 */
		@StringSetter("subpopulation")
		public void setSubpopulation(String subpopulation) {
			// TODO: handle case of default subpopulation
			if (this.subpopulation != null) {
				throw new IllegalStateException(
						"cannot change subpopulation in a scoring parameter set, as it is used for indexing.");
			}

			this.subpopulation = subpopulation;
		}

		@StringGetter(WAITING_PT)
		public double getMarginalUtlOfWaitingPt_utils_hr() {
			return waitingPt != null ? waitingPt
					: this.getModes().get(TransportMode.pt).getMarginalUtilityOfTraveling();
		}

		@StringSetter(WAITING_PT)
		public void setMarginalUtlOfWaitingPt_utils_hr(final Double waitingPt) {
			this.waitingPt = waitingPt;
		}

		/* parameter set handling */
		@Override
		public ConfigGroup createParameterSet(final String type) {
			switch (type) {
			case ActivityParams.SET_TYPE:
				return new ActivityParams();
			case ModeParams.SET_TYPE:
				return new ModeParams();
			default:
				throw new IllegalArgumentException(type);
			}
		}

		@Override
		protected void checkParameterSet(final ConfigGroup module) {
			switch (module.getName()) {
			case ActivityParams.SET_TYPE:
				if (!(module instanceof ActivityParams)) {
					throw new RuntimeException("wrong class for " + module);
				}
				final String t = ((ActivityParams) module).getActivityType();
				if (getActivityParams(t) != null) {
					throw new IllegalStateException("already a parameter set for activity type " + t);
				}
				break;
			case ModeParams.SET_TYPE:
				if (!(module instanceof ModeParams)) {
					throw new RuntimeException("wrong class for " + module);
				}
				final String m = ((ModeParams) module).getMode();
				if (getModes().get(m) != null) {
					throw new IllegalStateException("already a parameter set for mode " + m);
				}
				break;
			default:
				throw new IllegalArgumentException(module.getName());
			}
		}

		public Collection<String> getActivityTypes() {
			return this.getActivityParamsPerType().keySet();
		}

		public Collection<ActivityParams> getActivityParams() {
			@SuppressWarnings("unchecked")
			Collection<ActivityParams> collection = (Collection<ActivityParams>) getParameterSets(
					ActivityParams.SET_TYPE);
			for (ActivityParams params : collection) {
				if (this.isLocked()) {
					params.setLocked();
				}
			}
			return collection;
		}

		public Map<String, ActivityParams> getActivityParamsPerType() {
			final Map<String, ActivityParams> map = new LinkedHashMap<>();

			for (ActivityParams pars : getActivityParams()) {
				map.put(pars.getActivityType(), pars);
			}

			return map;
		}

		public ActivityParams getActivityParams(final String actType) {
			return this.getActivityParamsPerType().get(actType);
		}

		public ActivityParams getOrCreateActivityParams(final String actType) {
			ActivityParams params = this.getActivityParamsPerType().get(actType);

			if (params == null) {
				params = new ActivityParams(actType);
				addActivityParams(params);
			}

			return params;
		}

		public Map<String, ModeParams> getModes() {
			@SuppressWarnings("unchecked")
			final Collection<ModeParams> modes = (Collection<ModeParams>) getParameterSets(ModeParams.SET_TYPE);
			final Map<String, ModeParams> map = new LinkedHashMap<>();

			for (ModeParams pars : modes) {
				if (this.isLocked()) {
					pars.setLocked();
				}
				map.put(pars.getMode(), pars);
			}
			if (this.isLocked()) {
				return Collections.unmodifiableMap(map);
			} else {
				return map;
			}
		}

		public ModeParams getOrCreateModeParams(String modeName) {
			ModeParams modeParams = getModes().get(modeName);
			if (modeParams == null) {
				modeParams = new ModeParams(modeName);
				addParameterSet(modeParams);
			}
			return modeParams;
		}

		public void addModeParams(final ModeParams params) {
			final ModeParams previous = this.getModes().get(params.getMode());

			if (previous != null) {
				final boolean removed = removeParameterSet(previous);
				if (!removed)
					throw new RuntimeException("problem replacing mode params ");
				log.info("mode parameters for mode " + previous.getMode() + " were just overwritten.");
			}

			super.addParameterSet(params);
		}

		public void addActivityParams(final ActivityParams params) {
			final ActivityParams previous = this.getActivityParams(params.getActivityType());

			if (previous != null) {
				if (previous.getActivityType().equals(PtConstants.TRANSIT_ACTIVITY_TYPE)) {
					log.error("ERROR: Activity parameters for activity type " + previous.getActivityType()
							+ " were just overwritten. This happens most "
							+ "likely because you defined them in the config file and the Controler overwrites them.  Or the other way "
							+ "round.  pt interaction has problems, but doing what you are doing here will just cause "
							+ "other (less visible) problem. Please take the effort to discuss with the core team "
							+ "what needs to be done.  kai, nov'12");
				} else {
					log.info("activity parameters for activity type " + previous.getActivityType()
							+ " were just overwritten.");
				}

				final boolean removed = removeParameterSet(previous);
				if (!removed)
					throw new RuntimeException("problem replacing activity params ");
			}

			super.addParameterSet(params);
		}

		/**
		 * Checks whether all the settings make sense or if there are some
		 * problems with the parameters currently set. Currently, this checks
		 * that for at least one activity type opening AND closing times are
		 * defined.
		 */
		@Override
		public void checkConsistency(Config config) {
			super.checkConsistency(config);
			
			
			boolean hasOpeningAndClosingTime = false;
			boolean hasOpeningTimeAndLatePenalty = false;

	
			// This cannot be done in ActivityParams (where it would make more
			// sense),
			// because some global properties are also checked
			for (ActivityParams actType : this.getActivityParams()) {
				if (actType.isScoringThisActivityAtAll()) {
					// (checking consistency only if activity is scored at all)

					if ((!Time.isUndefinedTime(actType.getOpeningTime()))
							&& (!Time.isUndefinedTime(actType.getClosingTime()))) {
						hasOpeningAndClosingTime = true;
					}
					if ((!Time.isUndefinedTime(actType.getOpeningTime())) && (getLateArrival_utils_hr() < -0.001)) {
						hasOpeningTimeAndLatePenalty = true;
					}
					if (actType.getOpeningTime() == 0. && actType.getClosingTime() > 24. * 3600 - 1) {
						log.error("it looks like you have an activity type with opening time set to 0:00 and closing "
								+ "time set to 24:00. This is most probably not the same as not setting them at all.  "
								+ "In particular, activities which extend past midnight may not accumulate scores.");
					}
				}
			}
			if (!hasOpeningAndClosingTime && !hasOpeningTimeAndLatePenalty) {
				log.info("NO OPENING OR CLOSING TIMES DEFINED!\n\n"
						+ "There is no activity type that has an opening *and* closing time (or opening time and late penalty) defined.\n"
						+ "This usually means that the activity chains can be shifted by an arbitrary\n"
						+ "number of hours without having an effect on the score of the plans, and thus\n"
						+ "resulting in wrong results / traffic patterns.\n"
						+ "If you are using MATSim without time adaptation, you can ignore this warning.\n\n");
			}
			if (this.getMarginalUtlOfWaiting_utils_hr() != 0.0) {
				log.warn("marginal utl of wait set to: " + this.getMarginalUtlOfWaiting_utils_hr()
						+ ". Setting this different from zero is "
						+ "discouraged since there is already the marginal utility of time as a resource. The parameter was also used "
						+ "in the past for pt routing; if you did that, consider setting the new "
						+ "parameter waitingPt instead.");
			}
		}

	}
}
