package com.example.logSleuthEnterprise.service.elastic;

import com.example.logSleuthEnterprise.model.ElasticLogDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

@Service
public class ElasticsearchService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchService.class);

    private final ElasticsearchOperations elasticsearchOperations;

    public ElasticsearchService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
        log.info("SimpleElasticService initialized");
    }

    public void saveToElasticsearch(ElasticLogDocument document) {
        try {
            elasticsearchOperations.save(document);
            log.info("Saved to Elasticsearch: {}", document.getId());
        } catch (Exception e) {
            log.error("Failed to save to Elasticsearch: {}", e.getMessage());
        }
    }


    public boolean isElasticsearchAvailable() {
        try {
            var clusterHealth = elasticsearchOperations.indexOps(IndexCoordinates.of("_cluster/health"));
            return true;
        } catch (Exception e) {
            log.warn("Elasticsearch not available: {}", e.getMessage());
            return false;
        }
    }
}
