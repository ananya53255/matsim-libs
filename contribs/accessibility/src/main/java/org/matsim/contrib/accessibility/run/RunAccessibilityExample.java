/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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
package org.matsim.contrib.accessibility.run;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup;
import org.matsim.contrib.accessibility.AccessibilityContributionCalculator;
import org.matsim.contrib.accessibility.GridBasedAccessibilityModule;
import org.matsim.contrib.accessibility.Modes4Accessibility;
import org.matsim.contrib.accessibility.utils.AccessibilityUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.ActivityOption;
import org.matsim.facilities.FacilitiesUtils;

import com.google.inject.multibindings.MapBinder;

/**
 * @author nagel
 *
 */
final public class RunAccessibilityExample {
	// do not change name of class; matsim book refers to it.  kai, dec'14

	private static final Logger log = Logger.getLogger(RunAccessibilityExample.class);

	
	public static void main(String[] args) {

		if ( args.length==0 || args.length>1 ) {
			throw new RuntimeException("useage: ... config.xml") ;
		}
		Config config = ConfigUtils.loadConfig( args[0] ) ;
		
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
		
		AccessibilityConfigGroup accConfig = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class ) ;
		accConfig.setComputingAccessibilityForMode(Modes4Accessibility.freespeed, true);
		
		// ---
		
		Scenario scenario = ScenarioUtils.loadScenario( config ) ;
		
		// ---
		
		// the run method is extracted so that a test can operate on it.
		run( scenario);
	}

	
	public static void run(final Scenario scenario) {
		
		final List<String> activityTypes = new ArrayList<>() ;
		final ActivityFacilities homes = FacilitiesUtils.createActivityFacilities("homes") ;

		for ( ActivityFacility fac : scenario.getActivityFacilities().getFacilities().values()  ) { // go through all facilities ...
			for ( ActivityOption option : fac.getActivityOptions().values() ) { // go through all activity options at each facility ...
				// figure out all activity types
				if ( !activityTypes.contains(option.getType()) ) {
					activityTypes.add( option.getType() ) ;
				}
				// figure out where the homes are
				if ( option.getType().equals("h") ) { // yyyyyy hardcoded home activity option; replace!!!!
					homes.addActivityFacility(fac);
				}
			}
		}
		
		log.warn( "found the following activity types: " + activityTypes );
		
		Controler controler = new Controler(scenario);

		for (final String actType : activityTypes) { // add an overriding module per activity type:

			final ActivityFacilities opportunities = FacilitiesUtils.createActivityFacilities() ;
			for ( ActivityFacility fac : scenario.getActivityFacilities().getFacilities().values()  ) {
				for ( ActivityOption option : fac.getActivityOptions().values() ) {
					if ( option.getType().equals(actType) ) {
						opportunities.addActivityFacility(fac);
					}
				}
			}

			final GridBasedAccessibilityModule module = new GridBasedAccessibilityModule();
			module.setOpportunities(opportunities);
			module.writeToSubdirectoryWithName(actType);

			// add additional facility data to an additional column in the output
			// here, an additional population density column is used
			module.addAdditionalFacilityData(homes) ;

			controler.addOverridingModule(module);
		}

		controler.run();
		
	}
}