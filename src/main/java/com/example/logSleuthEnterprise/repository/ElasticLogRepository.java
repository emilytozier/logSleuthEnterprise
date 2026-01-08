package com.example.logSleuthEnterprise.repository;


import com.example.logSleuthEnterprise.model.ElasticLogDocument;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ElasticLogRepository extends ElasticsearchRepository<ElasticLogDocument, String> {

    List<ElasticLogDocument> findByService(String service);

    List<ElasticLogDocument> findByLevel(String level);

    List<ElasticLogDocument> findByServiceAndLevel(String service, String level);

    List<ElasticLogDocument> findByMessageContaining(String text);

    List<ElasticLogDocument> findByTimestampBetween(Instant from, Instant to);

    @Query("{\"bool\": {\"must\": [{\"match\": {\"message\": \"?0\"}}]}}")
    List<ElasticLogDocument> searchByMessage(String text);

    @Query("{\"bool\": {\"must\": [" +
            "{\"match\": {\"service\": \"?0\"}}, " +
            "{\"range\": {\"timestamp\": {\"gte\": \"?1\", \"lte\": \"?2\"}}}" +
            "]}}")
    List<ElasticLogDocument> findByServiceAndTimeRange(String service, Instant from, Instant to);

    long countByService(String service);

    long countByLevel(String level);
}
