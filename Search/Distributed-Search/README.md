**Table of contents**

- [Understanding elasticsearch](#understanding-elasticsearch)
  - [Forward Index VS Inverted Index](#forward-index-vs-inverted-index)
  - [Basic Concept](#basic-concept)
- [Installation](#installation)
  - [Single-node Elasticsearch](#single-node-elasticsearch)
  - [Kibana](#kibana)
  - [Analysis](#analysis)


# Understanding elasticsearch

Elasticsearch is a distributed, open-source search and analytics engine built on top of Apache Lucene. It is designed to handle large amounts of data and provide near real-time search and analysis capabilities. Elasticsearch is commonly used in applications that require full-text search, structured search, and analytics across a variety of data types.

## Forward Index VS Inverted Index

**Forward Index -> Inverted Index**

![vs index](./images/Screenshot%202023-06-12%20at%205.28.14%20PM.png)

**Forward Index Search Process:** 
1. Search "Apple Phone"
2. File scan every record
3. Check if it is equal to "Apple Phone"


**Inverted Index Search Process:**
1. Search "Apple Phone"
2. Split word -> "Apple", "Phone"
3. Search Term "Apple", "Phone" 
4. Get Document id 1,2,3
5. Search document by id

## Basic Concept

Elasticsearch stores the data as JSON. One record is mapping to one document.

```JSON
{
    "id": 1,
    "title": "Apple Phone",
    "price": 1599
}
{
    "id": 2,
    "title": "Samsung Phone",
    "price": 899
}
{
    "id": 3,
    "title": "Apple Watch",
    "price": 349
}
{
    "id": 4,
    "title": "Samsung Charger",
    "price": 59
}
```

MySQL VS ELasticsearch

* MySQL: ACID transactions
* Elasticsearch: Big Data

| MySQL | ELasticsearch | Details |
| - | - | - |
| Table | Index | Index is a cluster of same type document |
| Row | Document | one JSON format record |
| Column | Field | JSON field name |
| Schema | Mapping | Define how a document, and the fields it contains, are stored and indexed |
| SQL | DSL | JSON style query language |

# Installation

## Single-node Elasticsearch
1. Create network in docker
```sh
docker network create es-net
```
2. Pull elasticsearch
```sh
docker pull elasticsearch:7.17.6
```
3. Run
```sh
docker run -d \
--name es \
-e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
-e "discovery.type=single-node" \
-v es-data:/usr/share/elasticsearch/data \
-v es-plugins:/usr/share/elasticsearch/plugins \
--privileged \
--network es-net \
-p 9200:9200 \
-p 9300:9300 \
elasticsearch:7.17.6
```

* --network es-net: join network es-net
* --privileged: give access to volume
* 9200: for UI
* 9300: for cluster communication

## Kibana
1. Pull
```sh
docker pull kibana:7.17.6
```
2. Run
```sh
docker run -d \
--name kibana \
-e ELASTICSEARCH_HOSTS=http://es:9200 \
--network=es-net \
-p 5601:5601  \
kibana:7.17.6
```

## Analysis

[IK Analysis](https://github.com/medcl/elasticsearch-analysis-ik) supposes for Chinese.