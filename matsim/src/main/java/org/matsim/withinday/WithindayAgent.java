/* *********************************************************************** *
 * project: org.matsim.*
 * WithindayAgent.java
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

package org.matsim.withinday;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.events.AgentReplanEventImpl;
import org.matsim.core.network.NetworkLayer;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.PersonImpl;
import org.matsim.core.population.PlanImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scoring.PlanScorer;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.misc.NetworkUtils;
import org.matsim.core.utils.misc.RouteUtils;
import org.matsim.ptproject.qsim.QPersonAgent;
import org.matsim.ptproject.qsim.QSim;
import org.matsim.withinday.beliefs.AgentBeliefs;
import org.matsim.withinday.contentment.AgentContentment;
import org.matsim.withinday.percepts.AgentPercepts;
import org.matsim.withinday.routeprovider.RouteProvider;

/**
 * @author dgrether
 */
public class WithindayAgent extends QPersonAgent {

	private static final Logger log = Logger.getLogger(WithindayAgent.class);

//	private Person person;

//	private OccupiedVehicle vehicle;

	private AgentBeliefs beliefs;

	private RouteProvider desireGenerationFunction;

	private AgentContentment contentment;

	private ScoringFunctionFactory scoringFunctionFactory;

	private PlanScorer planScorer;

	private double replanningInterval;

	private final double lastReplaningTimeStep;

	private double replanningThreshold;

	private final List<AgentPercepts> percepts;

	private final Network network;

	public WithindayAgent(final Person person, final QSim simulation, final int sightDistance, final WithindayAgentLogicFactory factory) {
		super(person, simulation);
		this.network = simulation.getQNetwork().getNetwork();
//		this.person = person;
//		this.vehicle = v;
//		this.setVehicle(v);
		this.lastReplaningTimeStep = 0.0d;
	//place the agent in the vehicle
//		this.getVehicle().setDriver(this);
	//set the agents desire generation module
		this.desireGenerationFunction = factory.createRouteProvider();
	//set agent's contentment
		this.contentment = factory.createAgentContentment(this);
		//set the scoring function factory
		this.scoringFunctionFactory = factory.createScoringFunctionFactory();
		this.planScorer = new PlanScorer(this.scoringFunctionFactory);
		//set the agent's beliefs
		Tuple<AgentBeliefs, List<AgentPercepts>> tuple = factory.createAgentPerceptsBeliefs(sightDistance);
		this.beliefs = tuple.getFirst();
		this.percepts = tuple.getSecond();
	}

	private void revisePercepts() {
		for (AgentPercepts p : this.percepts) {
			p.updatedPercepts(this.network.getLinks().get(this.getCurrentLinkId()).getToNode());
		}
	}


	public void replan() {
		//check if replanning is allowed
		if (this.getQSimulation().getSimTimer().getTimeOfDay() >= (this.replanningInterval + this.lastReplaningTimeStep)) {
			if (log.isTraceEnabled()) {
				log.trace("Agent " + this.getPerson().getId() + " requested to replan...");
			}
			//let the agent look out of his window if he is able to do this
			this.revisePercepts();
			double replanningNeed = this.getReplanningNeed();
			if (replanningNeed >= this.replanningThreshold) {
				Id currentLinkId = this.getCurrentLinkId();
//				Node currentToNode = this.network.getLinks().get(currentLinkId).getToNode();
//				Node currentDestinationNode = this.network.getLinks().get(this.getDestinationLinkId()).getFromNode();
				//as replanning is rerouting agents will only replan if they are on the road and not on the link of the next activity
				if (isEnRoute()) {
					//only reroute if the RouteProvider provides a route
					NetworkRoute subRoute = ((NetworkRoute) this.getCurrentLeg().getRoute()).getSubRoute(currentLinkId, this.getDestinationLinkId());
					if (this.desireGenerationFunction.providesRoute(currentLinkId, subRoute)) {
						this.reroute();
					}
					else if (log.isTraceEnabled()) {
						log.trace("...but his desireGenerationFunction doesn't generate an appropriate option.");
					}
				}
			}
			else if (log.isTraceEnabled()) {
				log.trace("...but has no need to replan.");
			}
		}
	}

	private void reroute() {
		if (log.isTraceEnabled()) {
			log.trace("");
			log.trace("Starting agent's rerouting...");
			log.trace("agent nr.: " + this.getPerson().getId());
			log.trace("agentposition link: " + this.getCurrentLinkId());
			int hours = (int)this.getQSimulation().getSimTimer().getTimeOfDay() / 3600;
			int min = (int) ((this.getQSimulation().getSimTimer().getTimeOfDay() - (hours * 60)) / 60);
			log.trace("time: " + hours + ":" + min);
		}
		Link currentLink = this.network.getLinks().get(this.getCurrentLinkId());
		Activity nextAct = ((PlanImpl) this.getPerson().getSelectedPlan()).getNextActivity(this.getCurrentLeg());
		Link destinationLink = this.network.getLinks().get(nextAct.getLinkId());
		NetworkRoute alternativeRoute = this.desireGenerationFunction.requestRoute(currentLink, destinationLink, this.getQSimulation().getSimTimer().getTimeOfDay());
		Plan oldPlan = this.getPerson().getSelectedPlan();
		LegImpl currentLeg = (LegImpl) this.getCurrentLeg();
		Route oldRoute = currentLeg.getRoute();

		//create Route of already passed Nodes
		//TODO dg use Route.getSubroute method
		Node lastPassedNode = currentLink.getFromNode();
		List<Node> oldRouteNodes = RouteUtils.getNodes((NetworkRoute) currentLeg.getRoute(), this.network);
		int lastPassedNodeIndex = oldRouteNodes.indexOf(lastPassedNode);
		//this in fact a bit sophisticated construction is needed because Route.setNode(..) doesn't use the List interface and
		//is bound to a ArrayList instead
		List<Node> passedNodesList = new ArrayList<Node>();
		if (lastPassedNodeIndex != -1) {
			passedNodesList.addAll(oldRouteNodes.subList(0, lastPassedNodeIndex+1));
		}
		//create new plan
		PlanImpl newPlan = new PlanImpl(this.getPerson());
		newPlan.copyPlan(oldPlan);
		//put new route into the new plan
		//first determine index of current leg in the plan
		int currentLegIndex = 0;
		for (int i = 1; i < oldPlan.getPlanElements().size(); i = i + 2)	{
			if (oldPlan.getPlanElements().get(i).equals(currentLeg)) {
				currentLegIndex = i;
				break;
			}
		}
		//remove the old leg in the plan and replace it with the new one
    LegImpl oldLeg = (LegImpl) newPlan.getPlanElements().remove(currentLegIndex);
    LegImpl newLeg = new org.matsim.core.population.LegImpl(oldLeg);
    //concat the Route of already passed nodes with the new route
    ArrayList<Node> newRouteConcatedList = new ArrayList<Node>(passedNodesList.size() + alternativeRoute.getLinkIds().size() + 1);
    newRouteConcatedList.addAll(passedNodesList);
    newRouteConcatedList.addAll(RouteUtils.getNodes(alternativeRoute, this.network));
    NetworkRoute newRoute = (NetworkRoute) ((NetworkLayer) currentLink.getLayer()).getFactory().createRoute(TransportMode.car, oldRoute.getStartLinkId(), oldRoute.getEndLinkId());
    newRoute.setLinkIds(oldRoute.getStartLinkId(), NetworkUtils.getLinkIds(RouteUtils.getLinksFromNodes(newRouteConcatedList)), oldRoute.getEndLinkId());
    //put the new route in the leg and the leg in the plan
    newLeg.setRoute(newRoute);
    (newPlan.getPlanElements()).add(currentLegIndex, newLeg);
    //score plans and select best
    double currentScore = this.planScorer.getScore(oldPlan);
    double newScore = this.planScorer.getScore(newPlan);
    if (log.isTraceEnabled()) {
    	log.trace("Score of old plan: " + currentScore);
    	log.trace("Score of new plan: " + newScore);
    }
    //put it in vehicle
    //TODO dg remove true  when model of environment provides sufficient information to
    //create different plan scores
    if (/*newScore > currentScore*/ true) {
    	//TODO dg remove
    	if (log.isTraceEnabled()) {
				log.trace("rerouting agent " + this.getPerson().getId() + " with ...");
				StringBuffer buffer = new StringBuffer();
				for (Id linkId : alternativeRoute.getLinkIds()) {
					buffer.append(linkId.toString());
					buffer.append(' ');
				}
	    	log.trace("  new route: " + newRoute + " links: " + buffer.toString());
    	}
    	((PersonImpl)this.getPerson()).exchangeSelectedPlan(newPlan, false);
    	this.exchangeCurrentLeg(newLeg);

    	this.getQSimulation().getEventsManager().processEvent(new AgentReplanEventImpl(this.getQSimulation().getSimTimer().getTimeOfDay(), this.getPerson().getId(), alternativeRoute));
    }
	}

	// methods from AgentBrain

	/**
	 * Returns the replanning need. It is the value returned by the agent
	 * contentment with an altered sign an neglecting the negative part.
	 *
	 * @return the replanning need.
	 */
	public double getReplanningNeed() {
		if (this.contentment != null) {
			// TODO make this human readable
			// this was the implementation of AgentBrain.getReplanningNeed()
			double brainContentment = Math.min(1, this.contentment.getContentment(this.getQSimulation().getSimTimer().getTimeOfDay())
					* -1);
			// this was the implementation of Agent.getReplanningNeed()
			return Math.max(0, brainContentment);
		}
		return 0;
	}

	/**
	 * Returns if the agent is currently performing a leg. Actually checks if the
	 * departure time is later than the current simulation time
	 *
	 * @return <tt>true</tt> if the agent is currently performing a leg,
	 *         <tt>false</tt> otherwise.
	 */
	public boolean isEnRoute() {
		if (this.getVehicle().getEarliestLinkExitTime() > this.getQSimulation().getSimTimer().getTimeOfDay()) {
			//TODO [dg] remove if exception never thrown (dg oct2007)
			throw new RuntimeException("This should never happen in the new implementation!");
		}
		return true;
	}

	@Override
	public Id chooseNextLinkId() {
		if (this.getCurrentLinkId() != this.getDestinationLinkId()) {
			this.replan();
		}
//		this.cachedNextLink = null;
		Id linkId = super.chooseNextLinkId();
		if (log.isTraceEnabled())
			log.trace("vehicle : " + this.getPerson().getId() + " next choosen link:" + linkId);
		return linkId;
	}

	public void exchangeCurrentLeg(final LegImpl newLeg) {
		this.cachedNextLinkId = null;
		this.setCurrentLeg(newLeg);
	}

	public void setAgentContentment(final AgentContentment contentment) {
		this.contentment = contentment;
	}

	public AgentBeliefs getBeliefs() {
		return this.beliefs;
	}

	public void setBeliefs(final AgentBeliefs beliefs) {
		this.beliefs = beliefs;
	}

	public void setDesireGenerationModule(final RouteProvider routeProvider) {
		this.desireGenerationFunction = routeProvider;
	}
	/**
	 * @param scoringFunctionFactory the scoringFunctionFactory to set
	 */
	public void setScoringFunctionFactory(
			final ScoringFunctionFactory scoringFunctionFactory) {
		this.scoringFunctionFactory = scoringFunctionFactory;
		this.planScorer = new PlanScorer(this.scoringFunctionFactory);
	}

	public void setReplanningInterval(final double replanningInterval) {
		this.replanningInterval = replanningInterval;
	}
	/**
	 * @param replanningThreshold the replanningThreshold to set
	 */
	public void setReplanningThreshold(final double replanningThreshold) {
		this.replanningThreshold = replanningThreshold;
	}


}
