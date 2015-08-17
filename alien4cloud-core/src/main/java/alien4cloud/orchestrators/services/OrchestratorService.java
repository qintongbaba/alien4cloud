package alien4cloud.orchestrators.services;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Service;

import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.dao.model.GetMultipleDataResult;
import alien4cloud.exception.AlreadyExistException;
import alien4cloud.exception.NotFoundException;
import alien4cloud.model.cloud.Cloud;
import alien4cloud.model.orchestrators.Orchestrator;
import alien4cloud.model.orchestrators.OrchestratorConfiguration;
import alien4cloud.model.orchestrators.OrchestratorStatus;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.orchestrators.plugin.IOrchestratorFactory;
import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.rest.utils.JsonUtil;
import alien4cloud.utils.MapUtil;

/**
 * Manages orchestrators
 */
@Slf4j
@Service
public class OrchestratorService {
    @Resource(name = "alien-es-dao")
    private IGenericSearchDAO alienDAO;
    @Resource
    private OrchestratorFactoriesRegistry orchestratorFactoriesRegistry;
    @Resource
    private LocationService locationService;

    /**
     * Creates an orchestrators.
     * 
     * @param name The unique name that refers the orchestrators from user point of view.
     * @param pluginId The id of the plugin used to communicate with the orchestrators.
     * @param pluginBean The bean in the plugin that is indeed managing communication.
     * @return The generated identifier for the orchestrators.
     */
    public synchronized String create(String name, String pluginId, String pluginBean) {
        Orchestrator orchestrator = new Orchestrator();
        // generate an unique id
        orchestrator.setId(UUID.randomUUID().toString());
        orchestrator.setName(name);
        orchestrator.setPluginId(pluginId);
        orchestrator.setPluginBean(pluginBean);
        // by default clouds are disabled as it should be configured before being enabled.
        orchestrator.setStatus(OrchestratorStatus.DISABLED);

        // get default configuration for the orchestrators.
        IOrchestratorFactory orchestratorFactory = orchestratorFactoriesRegistry.getPluginBean(orchestrator.getPluginId(), orchestrator.getPluginBean());
        OrchestratorConfiguration configuration = new OrchestratorConfiguration(orchestrator.getId(), orchestratorFactory.getDefaultConfiguration());

        orchestrator.setMultipleLocations(orchestratorFactory.isMultipleLocations());

        saveAndEnsureNameUnicity(orchestrator);
        alienDAO.save(configuration);

        return orchestrator.getId();
    }

    /**
     * Update the name of an existing orchestrators.
     * 
     * @param id Unique id of the orchestrators.
     * @param name Name of the orchestrators.
     */
    public void updateName(String id, String name) {
        Orchestrator orchestrator = getOrFail(id);
        orchestrator.setName(name);
        saveAndEnsureNameUnicity(orchestrator);
    }

    /**
     * Save the orchestrators but ensure that the name is unique before saving it.
     * 
     * @param orchestrator The orchestrators to save.
     */
    private synchronized void saveAndEnsureNameUnicity(Orchestrator orchestrator) {
        // check that the cloud doesn't already exists
        if (alienDAO.count(Orchestrator.class, QueryBuilders.termQuery("name", orchestrator.getName())) > 0) {
            throw new AlreadyExistException("a cloud with the given name already exists.");
        }
        alienDAO.save(orchestrator);
    }

    /**
     * Delete an existing orchestrators.
     * 
     * @param id The id of the orchestrators to delete.
     */
    public void delete(String id) {
        // delete all locations for the orchestrators
        Location[] locations = locationService.getOrchestratorLocations(id);
        if (locations != null) {
            for (Location location : locations) {
                locationService.delete(location.getId());
            }
        }
        // delete the orchestrators configuration
        alienDAO.delete(OrchestratorConfiguration.class, id);
        alienDAO.delete(Orchestrator.class, id);
    }

    /**
     * Get the orchestrators matching the given id or throw a NotFoundException
     * 
     * @param id If of the orchestrators that we want to get.
     * @return An instance of the orchestrators.
     */
    public Orchestrator getOrFail(String id) {
        Orchestrator orchestrator = alienDAO.findById(Orchestrator.class, id);
        if (orchestrator == null) {
            throw new NotFoundException("Orchestrator [" + id + "] doesn't exists.");
        }
        return orchestrator;
    }

    /**
     * Get multiple orchestrators.
     *
     * @param query The query to apply to filter orchestrators.
     * @param from The start index of the query.
     * @param size The maximum number of elements to return.
     * @param authorizationFilter authorization filter
     * @return A {@link GetMultipleDataResult} that contains Orchestrator objects.
     */
    public GetMultipleDataResult<Orchestrator> search(String query, OrchestratorStatus status, int from, int size, FilterBuilder authorizationFilter) {
        Map<String, String[]> filters = null;
        if (status != null) {
            filters = MapUtil.newHashMap(new String[] { "status" }, new String[][] { new String[] { status.toString() } });
        }
        return alienDAO.search(Orchestrator.class, query, filters, authorizationFilter, null, from, size);
    }

    /**
     * Return the type of configuration for a given orchestrator.
     * 
     * @param id Id of the orchestrator for which to get configuration.
     * @return The type of the orchestrator.
     */
    public Class<?> getConfigurationType(String id) {
        Orchestrator orchestrator = getOrFail(id);
        return getConfigurationType(orchestrator);
    }

    /**
     * Return the type of configuration for a given orchestrator.
     *
     * @param orchestrator Orchestrator for which to get configuration.
     * @return The type of the orchestrator.
     */
    private Class<?> getConfigurationType(Orchestrator orchestrator) {
        return orchestratorFactoriesRegistry.getPluginBean(orchestrator.getPluginId(), orchestrator.getPluginBean()).getConfigurationType();
    }

    /**
     * Get the configuration for a given orchestrator.
     *
     * @param id Id of the orchestrator for which to get the configuration.
     * @return the instance of configuration for the given orchestrator
     */
    public OrchestratorConfiguration getConfigurationOrFail(String id) {
        OrchestratorConfiguration configuration = alienDAO.findById(OrchestratorConfiguration.class, id);
        if (configuration == null) {
            throw new NotFoundException("Orchestrator Configuration for id [" + id + "] doesn't exists.");
        }
        return configuration;
    }

    /**
     * Ensure that the configuration object parsed from json without typing is valid based on the orchestrator configuration type and return a valid typed
     * object.
     *
     * @param id if of the orchestrator.
     * @param configurationAsMap Configuration object (that may be a map parsed from json).
     * @return A typed configuration object.
     */
    public Object configurationAsValidObject(String id, Object configurationAsMap) throws IOException, PluginConfigurationException {
        Orchestrator orchestrator = getOrFail(id);
        return configurationAsValidObject(orchestrator, configurationAsMap);
    }

    /**
     * Ensure that the configuration object parsed from json without typing is valid based on the orchestrator configuration type and return a valid typed
     * object.
     *
     * @param orchestrator Orchestrator for which to validated and compute a type configuration object.
     * @param configurationAsMap Configuration object (that may be a map parsed from json).
     * @return A typed configuration object.
     */
    private Object configurationAsValidObject(Orchestrator orchestrator, Object configurationAsMap) throws PluginConfigurationException, IOException {
        Class<?> configurationType = getConfigurationType(orchestrator);
        if (configurationType == null) {
            String message = "Orchestrator <" + orchestrator.getId() + "> using plugin <" + orchestrator.getPluginId() + "> <" + orchestrator.getPluginBean()
                    + "> cannot have configuration set (plugin has no configuration type).";
            throw new PluginConfigurationException(message);
        }

        return JsonUtil.readObject(JsonUtil.toString(configurationAsMap), configurationType);
    }

    /**
     * Update the configuration for the given cloud.
     *
     * @param id Id of the orchestrator for which to update the configuration.
     * @param newConfiguration The new configuration.
     */
    public synchronized void updateConfiguration(String id, Object newConfiguration) {
        OrchestratorConfiguration configuration = alienDAO.findById(OrchestratorConfiguration.class, id);
        if (configuration == null) {
            throw new NotFoundException("No configuration exists for cloud [" + id + "].");
        }
        configuration.setConfiguration(newConfiguration);
        alienDAO.save(configuration);
    }
}