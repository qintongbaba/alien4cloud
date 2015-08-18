package alien4cloud.plugin.mock;

import javax.annotation.Resource;

import alien4cloud.model.cloud.IaaSType;
import org.springframework.beans.factory.BeanFactory;

import alien4cloud.orchestrators.plugin.IOrchestratorPluginFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for Mock implementation of orchestrator instance.
 */
@Component("mock-orchestrator-factory")
public class MockOrchestratorFactory implements IOrchestratorPluginFactory<MockOrchestrator, ProviderConfig> {
    @Resource
    private BeanFactory beanFactory;

    @Override
    public MockOrchestrator newInstance() {
        return beanFactory.getBean(MockOrchestrator.class);
    }

    @Override
    public void destroy(MockOrchestrator instance) {
        // nothing specific, the plugin will be garbaged collected when all references are lost.
    }

    @Override
    public ProviderConfig getDefaultConfiguration() {
        return new ProviderConfig();
    }

    @Override
    public Class<ProviderConfig> getConfigurationType() {
        return ProviderConfig.class;
    }

    @Override
    public boolean isMultipleLocations() {
        return true;
    }

    @Override
    public String[] supportedInfrastructureTypes() {
        IaaSType[] types = IaaSType.values();
        String[] typesAsString = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            typesAsString[i] = types[i].toString();
        }
        return typesAsString;
    }
}