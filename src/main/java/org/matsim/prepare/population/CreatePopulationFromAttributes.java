package org.matsim.prepare.population;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.LanduseOptions;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ProjectionUtils;
import org.matsim.run.RunOpenBerlinScenario;
import org.opengis.feature.simple.SimpleFeature;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

@CommandLine.Command(
	name = "population-from-attributes",
	description = "Create population from given csv tables."
)
public class CreatePopulationFromAttributes implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(CreatePopulationFromAttributes.class);

	@CommandLine.Option(names = "--input", description = "Path to input csv with person attributes.", required = true)
	private Path input;

	@CommandLine.Option(names = "--output", description = "Path to output population", required = true)
	private Path output;

	@CommandLine.Option(names = "--facilities", description = "Path to facilities file", required = true)
	private Path facilityPath;

	@CommandLine.Option(names = "--network", description = "Path to network file", required = true)
	private Path networkPath;

	@CommandLine.Option(names = "--sample", description = "Sample size of the population", defaultValue = "0.25")
	private double sample;

	@CommandLine.Option(names = "--seed", description = "Seed used to sample locations", defaultValue = "1")
	private long seed;

	@CommandLine.Mixin
	private ShpOptions shp;

	@CommandLine.Mixin
	private LanduseOptions landuse = new LanduseOptions();

	private FacilityIndex facilityIndex;

	/**
	 * Maps zone number to geometry.
	 */
	private Long2ObjectMap<SimpleFeature> zones;

	private Network network;

	public static void main(String[] args) {
		new CreatePopulationFromAttributes().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		if (shp.getShapeFile() == null) {
			log.error("Shape file with region codes is required.");
			return 2;
		}

		Network completeNetwork = NetworkUtils.readNetwork(networkPath.toString());
		TransportModeNetworkFilter filter = new TransportModeNetworkFilter(completeNetwork);
		network = NetworkUtils.createNetwork();
		filter.filter(network, Set.of(TransportMode.car));

		facilityIndex = new FacilityIndex(facilityPath.toString());

		zones = new Long2ObjectOpenHashMap<>(shp.readFeatures().stream()
			.collect(Collectors.toMap(ft -> Long.parseLong((String) ft.getAttribute("ARS")), ft -> ft)));

		Population population = PopulationUtils.createPopulation(ConfigUtils.createConfig());

		try (CSVParser csv = new CSVParser(Files.newBufferedReader(input), CSVFormat.DEFAULT.builder().setHeader().build())) {
			for (CSVRecord row : csv) {
				Person person = createPerson(row);
				population.addPerson(person);
			}
		}

		PopulationUtils.writePopulation(population, output.toString());
		ProjectionUtils.putCRS(population, RunOpenBerlinScenario.CRS);

		return 0;
	}


	private Person createPerson(CSVRecord row) {

		// get lor

//		CreateBerlinPopulation.sampleHomeCoordinate();

		// TODO

		return null;

	}
}
