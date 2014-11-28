package alien4cloud.model.application;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

import org.elasticsearch.annotation.ESObject;
import org.elasticsearch.annotation.Id;
import org.elasticsearch.annotation.StringField;
import org.elasticsearch.annotation.query.TermFilter;
import org.elasticsearch.mapping.IndexType;

import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.model.cloud.Network;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@ESObject
@Getter
@Setter
@SuppressWarnings("PMD.UnusedPrivateField")
@JsonInclude(Include.NON_NULL)
public class DeploymentSetup {
    @Id
    private String id;
    @TermFilter
    @StringField(includeInAll = false, indexType = IndexType.not_analyzed)
    private String versionId;
    @TermFilter
    @StringField(includeInAll = false, indexType = IndexType.not_analyzed)
    private String environmentId;

    private Map<String, String> providerDeploymentProperties;

    // TODO add also the input artifacts here. /-> Note that they should/could be repository based.
    private Map<String, String> inputProperties;

    private Map<String, ComputeTemplate> cloudResourcesMapping;

    private Map<String, Network> networkMapping;
}