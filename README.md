/** система для сбора и анализа логов **/
связка Docker++Cassandra++Kafka (Zookeeeper)++ELK
• Docker - контейенеризация
• Cassandra - хранение логов
• Elasticsearch - поисковый индекс
• Kafka - потоковая передача данных
• Kibana - dashboard
(у логов есть log level, timestamp, service - по ним можно настраивать фильтры в kibana)
поднять компоненты  через docker-compose.yml
docker-compose up -d zookeeper 
docker-compose up -d kafka 
docker-compose up -d
docker-compose ps

mvn clean compile
mvn clean spring-boot:run

отправляем лог для теста
curl --location 'http://localhost:8081/api/logs/kafka' \
--header 'Content-Type: application/json' \
--data '{
"service": "test-service",
"level": "DEBUG",
"message": "debug message",
"host": "localhost"
}'

api для проверки
curl http://localhost:8081/api/logs
curl http://localhost:8081/api/health

# Проверить топик Kafka
docker exec log-kafka kafka-topics.sh --describe --topic raw-logs --bootstrap-server localhost:9092

# Посмотреть список топиков
docker exec log-kafka kafka-topics.sh --list --bootstrap-server localhost:9092

# Посмотреть все индексы elasticsearch
curl http://localhost:9200/_cat/indices?v

curl http://localhost:9200/_cat/indices?v
docker exec log-kafka kafka-console-consumer.sh \
   --topic raw-logs \
   --from-beginning \
   --bootstrap-server localhost:9092 \
   --max-messages 5

# Посмотреть, есть ли сообщения в топике
docker exec log-kafka kafka-console-consumer.sh \
--topic raw-logs \
--from-beginning \
--bootstrap-server localhost:9092 \
--max-messages 5

# Проверить consumer group offsets(больше 0 например Committing: {raw-logs-0=OffsetAndMetadata{offset=1...)
docker exec log-kafka kafka-consumer-groups.sh \
--bootstrap-server localhost:9092 \
--group logsleuth-enterprise-group \
--describe

# Посмотреть документы в Elasticsearch
curl -X GET "http://localhost:9200/logs-index/_search?pretty" \
-H "Content-Type: application/json" \
-d '{
"query": {
"match_all": {}
}
}'

# Подключиться к Cassandra и посмотреть данные
docker exec -it log-cassandra cqlsh

# Внутри CQLSH выполнить:
USE logsleuth_keyspace;

# Посмотреть все таблицы
DESCRIBE TABLES;

# Посмотреть структуру таблицы logs
DESCRIBE TABLE logs;

# Посчитать количество записей
SELECT COUNT(*) FROM logs;

# Посмотреть 5 последних записей
SELECT id, service, level, message, timestamp FROM logs LIMIT 5;

# Посмотреть статистику
curl http://localhost:8081/api/logs/stats

# Проверить Kibana
# Открыть в браузере: http://localhost:5601
# Настройте индекс в Kibana

# Проверить статус Logstash
docker logs log-logstash --tail=20

# Проверить конфигурацию Logstash
docker exec log-logstash cat /usr/share/logstash/pipeline/logstash.conf

# Посмотреть, слушает ли Logstash на порту 5000
curl localhost:5000
