# RFC 0001: Migrate Kafka from ZooKeeper to KRaft

## Status
Accepted

## Context
FintechWave relies on Apache Kafka as the backbone of its event-driven architecture. Currently, the local development stack (`docker-compose.yml`) uses ZooKeeper for Kafka metadata management. 
However, ZooKeeper introduces a separate distributed system that needs to be monitored, secured, and scaled. 

With Confluent Platform 7.x, Kafka introduced KRaft (Kafka Raft metadata mode) which removes the dependency on ZooKeeper. KRaft has been production-ready for some time. Moving to KRaft allows for a simplified infrastructure topology and faster controller failovers, which fits FintechWave's strict requirements around availability and resilience.

## Decision
We will remove the ZooKeeper container from the `docker-compose.yml` and reconfigure the `cp-kafka` service to operate in combined `broker,controller` KRaft mode. 

This change reduces our infrastructure surface area, removes inter-process dependencies, and modernizes our local development setup to match the recommended deployment topologies for future production phases.

## Implementation Details
- Removed the `zookeeper` service from `docker-compose.yml`.
- Reconfigured the `kafka` service to use KRaft variables:
  - `KAFKA_PROCESS_ROLES: 'broker,controller'`
  - `KAFKA_NODE_ID: 1`
  - Added `CONTROLLER` endpoints to listeners.
  - Specified a deterministic `CLUSTER_ID`.
- Updated `README.md` to reflect the removal of ZooKeeper from the tech stack.

## Consequences
- **Positive:** Simpler infrastructure footprint, one less stateful service to run locally and eventually in production. Faster bootstrap times.
- **Negative:** Any legacy scripts that relied directly on `zookeeper:2181` for metadata will need to point to `kafka:9092` instead using the admin client. Since we are in the early phases and mainly using `kafka-topics` commands, the impact is minimal.
