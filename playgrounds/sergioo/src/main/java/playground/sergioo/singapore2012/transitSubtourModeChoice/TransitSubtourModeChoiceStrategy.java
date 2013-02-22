package playground.sergioo.singapore2012.transitSubtourModeChoice;

import org.apache.log4j.Logger;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl;
import org.matsim.core.replanning.ReplanningContext;
import org.matsim.core.replanning.modules.ReRoute;
import org.matsim.core.replanning.modules.SubtourModeChoice;
import org.matsim.core.replanning.selectors.RandomPlanSelector;

import playground.sergioo.singapore2012.transitLocationChoice.TransitActsRemoverStrategy;

public class TransitSubtourModeChoiceStrategy implements PlanStrategy {
	private static final Logger log =
		Logger.getLogger(TransitSubtourModeChoiceStrategy.class);


	private PlanStrategyImpl delegate;
	
	public TransitSubtourModeChoiceStrategy(Controler controler) {
		delegate = new PlanStrategyImpl(new RandomPlanSelector());
		delegate.addStrategyModule(new TransitActsRemoverStrategy(controler.getConfig()));
		log.warn( "your stategy now uses vanilla SubtourModeChoice, not a hacked copy thereof" );
		log.warn( "just set config.subtourModeChoice.considerCarAvailability to true in the config to get the same behavior" );
		log.warn( "... but actually, you may just delete this strategy altogether, it does not provide anything matsim doesn't provide. td, 22. feb. 2013" );
		delegate.addStrategyModule(new SubtourModeChoice(controler.getConfig()));
		delegate.addStrategyModule(new ReRoute(controler.getScenario()));
	}
	
	public void addStrategyModule(PlanStrategyModule module) {
		delegate.addStrategyModule(module);
	}

	public int getNumberOfStrategyModules() {
		return delegate.getNumberOfStrategyModules();
	}

	@Override
	public void run(Person person) {
		delegate.run(person);
	}

	@Override
	public void init(ReplanningContext replanningContext) {
		delegate.init(replanningContext);
	}

	@Override
	public void finish() {
		delegate.finish();
	}


}
