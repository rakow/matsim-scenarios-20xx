package org.matsim.scoring;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ScenarioConfigGroup;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.config.groups.ScenarioConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scoring.functions.ActivityUtilityParameters;
import org.matsim.core.scoring.functions.ModeUtilityParameters;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;

/**
 * @author jlie/pmanser/mrieser / SBB based on org.matsim.core.scoring.functions.RandomizedCharyparNagelScoringParameters
 * 		<p>
 * 		extended the code to allow customized personal scoring parameters depending on different behaviorally homogeneous groups. extended the code to reduce memory consumption when a large number of
 * 		actiivty types is used.
 */

public class SBBCharyparNagelScoringParametersForPerson implements ScoringParametersForPerson {

	private final static Logger log = LogManager.getLogger(SBBCharyparNagelScoringParametersForPerson.class);

	private final ScoringConfigGroup config;
	private final ScenarioConfigGroup scConfig;
	private final Map<Person, SBBScoringParameters> paramsPerPerson = new LinkedHashMap<>();
	private final SBBBehaviorGroupsConfigGroup behaviorGroupsConfigGroup;

	private final Map<ComparableActivityParams, ComparableActivityParams> actParamCache = new ConcurrentHashMap<>();

	@Inject
	public SBBCharyparNagelScoringParametersForPerson(Scenario scenario) {
		this(scenario.getConfig().plans(),
				scenario.getConfig().scoring(),
				scenario.getConfig().scenario(),
				ConfigUtils.addOrGetModule(scenario.getConfig(), SBBBehaviorGroupsConfigGroup.class));
	}

	SBBCharyparNagelScoringParametersForPerson(
			PlansConfigGroup plansConfigGroup,
			ScoringConfigGroup planCalcScoreConfigGroup,
			ScenarioConfigGroup scenarioConfigGroup,
			SBBBehaviorGroupsConfigGroup behaviorGroupsConfigGroup) {
		this.config = planCalcScoreConfigGroup;
		this.scConfig = scenarioConfigGroup;
		this.behaviorGroupsConfigGroup = behaviorGroupsConfigGroup;
	}

	@Override
	public ScoringParameters getScoringParameters(Person person) {
		SBBScoringParameters sbbParams = getSBBScoringParameters(person);
		return sbbParams.getMatsimScoringParameters();
	}

	public SBBScoringParameters getSBBScoringParameters(Person person) {
		SBBScoringParameters sbbParams = this.paramsPerPerson.get(person);
		if (sbbParams != null) {
			return sbbParams;
		}

		final String subpopulation = PopulationUtils.getSubpopulation(person);

		ScoringConfigGroup.ScoringParameterSet scoringParameters = this.config.getScoringParameters(subpopulation);
		ScoringConfigGroup.ScoringParameterSet filteredParameters = (ScoringConfigGroup.ScoringParameterSet) this.config
				.createParameterSet(ScoringConfigGroup.ScoringParameterSet.SET_TYPE);

		// make a (filtered) duplicate. Not very nice as it is not very future-proof, but I didn't find a better way to achieve the goal
		Set<String> usedActTypes = new HashSet<>();
		for (PlanElement pe : person.getSelectedPlan().getPlanElements()) {
			if (pe instanceof Activity) {
				usedActTypes.add(((Activity) pe).getType());
			}
		}

		// TODO: staging activities types?
//		usedActTypes.addAll(SBBActivities.stageActivityTypeList);

		filteredParameters.setSubpopulation(scoringParameters.getSubpopulation());
		filteredParameters.setMarginalUtlOfWaiting_utils_hr(scoringParameters.getMarginalUtlOfWaiting_utils_hr());
		filteredParameters.setMarginalUtlOfWaitingPt_utils_hr(scoringParameters.getMarginalUtlOfWaitingPt_utils_hr());
		filteredParameters.setMarginalUtilityOfMoney(scoringParameters.getMarginalUtilityOfMoney());
		filteredParameters.setPerforming_utils_hr(scoringParameters.getPerforming_utils_hr());
		filteredParameters.setUtilityOfLineSwitch(scoringParameters.getUtilityOfLineSwitch());
		filteredParameters.setEarlyDeparture_utils_hr(scoringParameters.getEarlyDeparture_utils_hr());
		filteredParameters.setLateArrival_utils_hr(scoringParameters.getLateArrival_utils_hr());
		for (ScoringConfigGroup.ModeParams modeParams : scoringParameters.getModes().values()) {
			filteredParameters.addModeParams(modeParams);
		}
		for (ScoringConfigGroup.ActivityParams actParams : scoringParameters.getActivityParams()) {
			if (usedActTypes.contains(actParams.getActivityType())) {
				filteredParameters.addActivityParams(actParams);
			}
		}

		SBBScoringParameters.Builder builder = new SBBScoringParameters.Builder(
				this.config, filteredParameters,
				this.scConfig, this.behaviorGroupsConfigGroup);

		// building the customized scoring parameters for each person depending on his behavior group
		// first the non-mode specific parameters
		double marginalUtilityOfParkingPrice = this.behaviorGroupsConfigGroup.getMarginalUtilityOfParkingPrice();
		double transferUtilityBase = this.behaviorGroupsConfigGroup.getBaseTransferUtility();
		double transferUtilityPerTravelTime = this.behaviorGroupsConfigGroup.getTransferUtilityPerTravelTime_utils_hr();

		for (SBBBehaviorGroupsConfigGroup.BehaviorGroupParams bgp : behaviorGroupsConfigGroup.getBehaviorGroupParams().values()) {
			Object personAttributeObj = person.getAttributes().getAttribute(bgp.getPersonAttribute());
			if (personAttributeObj == null) {
				continue;
			}

			String personAttribute = personAttributeObj.toString();

			SBBBehaviorGroupsConfigGroup.PersonGroupValues pgt = bgp.getPersonGroupByAttribute(personAttribute);
			if (pgt == null) {
				continue;
			}

			marginalUtilityOfParkingPrice += pgt.getDeltaMarginalUtilityOfParkingPrice();
			transferUtilityBase += pgt.getDeltaBaseTransferUtility();
			transferUtilityPerTravelTime += pgt.getDeltaTransferUtilityPerTravelTime();
		}
		builder.setMarginalUtilityOfParkingPrice(marginalUtilityOfParkingPrice);
		builder.setTransferUtilityBase(transferUtilityBase);
		builder.setTransferUtilityPerTravelTime(transferUtilityPerTravelTime);

		// collect the values for each mode
		for (String mode : this.config.getModes().keySet()) {
			final ScoringConfigGroup.ModeParams defaultModeParams = this.config.getModes().get(mode);
			final ModeUtilityParameters.Builder modeParameteresBuilder = new ModeUtilityParameters.Builder(defaultModeParams);

			double constant = defaultModeParams.getConstant();
			double margUtilTime = defaultModeParams.getMarginalUtilityOfTraveling();
			double margUtilDistance = defaultModeParams.getMarginalUtilityOfDistance();
			double monDistRate = defaultModeParams.getMonetaryDistanceRate();

			for (SBBBehaviorGroupsConfigGroup.BehaviorGroupParams bgp : behaviorGroupsConfigGroup.getBehaviorGroupParams().values()) {
				Object personAttributeObj = person.getAttributes().getAttribute(bgp.getPersonAttribute());
				if (personAttributeObj == null) {
					continue;
				}

				String personAttribute = personAttributeObj.toString();

				SBBBehaviorGroupsConfigGroup.PersonGroupValues pgt = bgp.getPersonGroupByAttribute(personAttribute);
				if (pgt == null) {
					continue;
				}

				SBBBehaviorGroupsConfigGroup.ModeCorrection modeCorrection = pgt.getModeCorrectionsForMode(mode);
				if (modeCorrection == null) {
					continue;
				}

				constant += modeCorrection.getConstant();
				margUtilTime += modeCorrection.getMargUtilOfTime();
				margUtilDistance += modeCorrection.getMargUtilOfDistance();
				monDistRate += modeCorrection.getDistanceRate();
			}

			modeParameteresBuilder.setConstant(constant);
			modeParameteresBuilder.setMarginalUtilityOfDistance_m(margUtilDistance);
			modeParameteresBuilder.setMarginalUtilityOfTraveling_s(margUtilTime / 3600);
			modeParameteresBuilder.setMonetaryDistanceRate(monDistRate);
			builder.getMatsimScoringParametersBuilder().setModeParameters(mode, modeParameteresBuilder.build());
		}
		sbbParams = builder.build();

		// make sure we re-use activity params when possible
		Map<String, ActivityUtilityParameters> actParams = sbbParams.getMatsimScoringParameters().utilParams;
		for (String actType : usedActTypes) {
			final ActivityUtilityParameters activityUtilityParameters = actParams.get(actType);
			if (activityUtilityParameters == null) {
				throw new RuntimeException("No parameters found for activity type " + actType);
			}
			ActivityUtilityParameters params = this.actParamCache.computeIfAbsent(new ComparableActivityParams(activityUtilityParameters), k -> k).params;
			actParams.replace(actType, params);
		}

		this.paramsPerPerson.put(person, sbbParams);

		return sbbParams;
	}

	private static class ComparableActivityParams {

		final ActivityUtilityParameters params;

		public ComparableActivityParams(ActivityUtilityParameters params) {
			this.params = params;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof ComparableActivityParams)) {
				return false;
			}
			ActivityUtilityParameters t = this.params;
			ActivityUtilityParameters o = ((ComparableActivityParams) obj).params;

			return t.getType().equals(o.getType())
					&& t.isScoreAtAll() == o.isScoreAtAll()
					&& t.getMinimalDuration().equals(o.getMinimalDuration())
					&& Double.compare(t.getTypicalDuration(), o.getTypicalDuration()) == 0
					&& t.getEarliestEndTime().equals(o.getEarliestEndTime())
					&& t.getLatestStartTime().equals(o.getLatestStartTime())
					&& t.getOpeningTime().equals(o.getOpeningTime())
					&& t.getClosingTime().equals(o.getClosingTime());
		}

		@Override
		public int hashCode() {
			ActivityUtilityParameters p = this.params;
			int hashCode = p.getType().hashCode();
			hashCode *= 31;
			hashCode += p.getMinimalDuration().hashCode();
			hashCode *= 31;
			hashCode += Double.hashCode(p.getTypicalDuration());
			hashCode *= 31;
			hashCode += p.getMinimalDuration().hashCode();
			hashCode *= 31;
			hashCode += p.getMinimalDuration().hashCode();
			hashCode *= 31;
			hashCode += p.getMinimalDuration().hashCode();
			hashCode *= 31;
			hashCode += p.getMinimalDuration().hashCode();

			return hashCode;
		}
	}
}
