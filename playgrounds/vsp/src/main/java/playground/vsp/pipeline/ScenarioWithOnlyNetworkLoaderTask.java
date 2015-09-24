package playground.vsp.pipeline;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.core.scenario.ScenarioUtils;

public class ScenarioWithOnlyNetworkLoaderTask implements RunnableScenarioSource {

	private ScenarioSink sink;
	
	private String filename;
	
	public ScenarioWithOnlyNetworkLoaderTask(String filename) {
		this.filename = filename;
	}

	@Override
	public void run() {
		Scenario scenario = (ScenarioImpl) ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new MatsimNetworkReader(scenario).readFile(filename);
		sink.initialize(scenario);
		sink.process(scenario);
	}

	@Override
	public void setSink(ScenarioSink sink) {
		this.sink = sink;
	}

}