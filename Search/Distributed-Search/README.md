**Table of contents**

- [Understanding elasticsearch](#understanding-elasticsearch)
  - [Forward Index VS Inverted Index](#forward-index-vs-inverted-index)
  - [Basic Concept](#basic-concept)
- [Installation](#installation)
  - [Single-node Elasticsearch](#single-node-elasticsearch)
  - [Kibana](#kibana)
  - [Analysis](#analysis)
- [DSL](#dsl)
  - [Mappings](#mappings)
    - [Dynamic Mapping](#dynamic-mapping)
  - [Document](#document)
  - [Query](#query)
    - [match\_all](#match_all)
    - [Full text](#full-text)
    - [Term-level](#term-level)
    - [Geo](#geo)
    - [Compound](#compound)
      - [Function Score Query](#function-score-query)
      - [Boolean Query](#boolean-query)
  - [Deal with Search Results](#deal-with-search-results)
    - [Sort Search Results](#sort-search-results)
    - [Paginate Search Results](#paginate-search-results)
    - [Highlight](#highlight)


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

# DSL

## Mappings

[Field data doc](https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-types.html)

**Common definition**

* type: field type
  * text: string can be split by analyzer
  * keyword: string can't be split
  * long, integer, short, byte, double, float
  * boolean
  * date
  * object
* index: created index or not, default to true
* analyzer:
* properties: sub field

**Example**

* Create
```JSON
// document
{
    "age": 30,
    "weight": 120,
    "isMarried": false,
    "info": "University Student",
    "email": "XXX@gmail.com",
    "hobby": ["Travel", "Drink", "Hide in the grass
"],
    "name": {
        "firstName": "Bai",
        "lastName": "Li"
    }
}

// mappings
PUT /person
{
  "mappings": {
    "properties": {
      "info": {
        "type": "text",
        "analyzer": "standard"
      },
      "email": {
        "type": "keyword",
        "index": false
      },
      "name": {
        "type": "object",
        "properties": {
          "firstName": {
            "type": "keyword",
            "index": false
          },
           "lastName": {
            "type": "keyword",
            "index": false
          }
        }
      },
      // ...
    }
  }
}
```
* Read
```Json
GET /person
```
* Update
```json
// mappings can't be modified only created
// but new properties can be added
PUT /person/_mapping
{
  "properties": {
    "age": {
      "type": "long"
    }
  }
}
```
* Delete
```Json
DELETE /person
```

### Dynamic Mapping

New mapping will be created automatically when inserting new document to ES if the corresponding mapping doesn't exist.

| JSON | ES |
| - | - |
| Normal String | text, and substring -> keyword |
| Date String | date |
| Boolean | boolean |
| Float | float |
| Integer | long |
| Nested Object | object, and add properties |
| Array | decide by first non-null var in array |
| NULL | ignore |

## Document

* Create
```JSON
// document id will be added automatically if not specify
POST /index_name/_doc/document_id
{
  "field1": "var",
  "field2": "var",
  "field3": {
    "sub field": "var",
    //...
  },
  // ...
}
```
* Read
```JSON
GET /index_name/_doc/document_id
```
* Update
```JSON
// 1. delete the document with document_id
// 2. create new document with this document_id
PUT /index_name/_doc/document_id
{
  "field1": "var",
  "field2": "var",
  "field3": {
    "sub field": "var",
    //...
  },
  // ...
}
// update field1 with new var
POST /index_name/_update/document_id
{
  "field1": "new var",
}
```
* Delete
```JSON
DELETE /index_name/_doc/document_id
```

## Query

[Query Doc](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html)

```JSON
// Basic format
GET /indexName/_search
{
  "query": {
    "queryType": {
      "queryContext": "context",
      "filterContext": "filter"
    }
  }
}
```

Example Mapping:
```JSON
PUT /hotel
{
  "mappings": {
    "properties": {
      "id": {
        "type": "keyword"
      },
      "name": {
        "type": "text",
        "analyzer": "ik_max_word"
      },
      "address": {
        "type": "keyword",
        "index": false
      },
      "price": {
        "type": "integer"
      },
      "score": {
        "type": "integer"
      },
      "brand": {
        "type": "keyword",
        "copy_to": "all"
      },
      "city": {
        "type": "keyword"
      },
      "starName": {
        "type": "keyword"
      },
      "business": {
        "type": "keyword",
        "copy_to": "all"
      },
      "location": {
        "type": "geo_point"
      },
      "pic": {
        "type": "keyword",
        "index": false
      },
      "all": {
        "type": "text",
        "analyzer": "ik_max_word"
      }
    }
  }
}
```

### match_all
```JSON
// search all document
GET /hotel/_search
{
  "query": {
    "match_all": {}
  }
}
```

### Full text

The query string is processed using the same analyzer that was applied to the field during indexing.

* match
```JSON
GET /indexName/_search
{
  "query": {
    "match": {
      "FIELD": "TEXT"
    }
  }
}
```
* multi_match
```JSON
// It has lower performance comparing to using technic copy_to
GET /indexName/_search
{
  "query": {
    "multi_match": {
      "query": "TEXT",
      "fields": ["FIELD1", " FIELD12"]
    }
  }
}
```

### Term-level

Find documents based on precise values in structured data.

* term
```JSON
GET /indexName/_search
{
  "query": {
    "term": {
      "FIELD": {
        "value": "VALUE"
      }
    }
  }
}
```
* range
```JSON
GET /indexName/_search
{
  "query": {
    "range": {
      "FIELD": {
        "gte": 10,
        "lte": 20
      }
    }
  }
}
```

### Geo

* geo_bounding_box
```JSON
GET /indexName/_search
{
  "query": {
    "geo_bounding_box": {
      "FIELD": {
        "top_left": {
          "lat": 31.1,
          "lon": 121.5
        },
        "bottom_right": {
          "lat": 30.9,
          "lon": 121.7
        }
      }
    }
  }
}
```
* geo_distance
```JSON
GET /indexName/_search
{
  "query": {
    "geo_distance": {
      "distance": "15km",
      "FIELD": "31.21,121.5"
    }
  }
}
```

### Compound

#### [Function Score Query](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html)

**Related Score Algorithm:**

* TF-IDF Algorithms:

$TF(t,d)=\frac{number\ of\ times\ t\ appears\ in\ d}{total\ number\ of\ terms\ in\ d}$

$IDF(t)=\log\frac{total\ number\ of\ documents}{1+number\ of\ documents\ with\ term\ t}$

$TF-IDF(t,d)=TF(t,d)*IDF(t)$

$score=\sum_{i}^{n}TF-IDF(t,d)$

* BM25 Algorithms(After ES 5.0):

Given a query Q, containing keywords q1, q2 ... qn, the BM25 score of a document D is:

$IDF(qi)=\ln(\frac{N-n(qi)+0.5}{n(qi)+0.5}+1)$

N is the total number of documents in the collection

n(qi) is the number of documents containing qi

$score(D,Q)=\sum_{i}^{n}IDF(qi)*\frac{f(qi,D)*(k1+1)}{f(qi,D)+k1*(1-b+b*\frac{|D|}{avgdl})}$

f(qi,D) is the number of times that qi occurs in the document D

|D| is the length of the document D in words

avgdl is the average document length in the text collection from which documents are drawn

k1 and b are free parameters

* Compare
![TF VS BM25](./images/Screenshot%202023-06-13%20at%206.50.52%20PM.png)

BM25 increases more smoothly along with the term frequency.

**Query:**

```JSON
GET /hotel/_search
{
  "query": {
    "function_score": {
      // 1. query score = result of related score algorithm
      "query": { "match_all": {} },
      "functions": [
        {
          // 2. Only choose the documents has this property
          "filter": {"term": {"id": "1"}},
          "weight": 10
          // 3. function score
          // weight: given constant value
          // field_value_factor: use a field value as score
          // random_score: random value
          // script_score: customized algorithm
        }
      ],
      "boost_mode": "multiply"
      // 4. score_mode
      // replace: replace query score with function score
      // multiply, sum, avg, max, min
    }
  }
}
```

#### Boolean Query

* must: "and", scoring, not cache
* should: "or", scoring, not cache
* must_not: "not", not count for scoring, and cache
* filter: "and", not count for scoring, and cache

```JSON
GET /hotel/_search
{
  "query": {
    "bool": {
      "must": [
        {"term": {"city": "XX" }}
      ],
      "should": [
        {"term": {"brand": "XX" }},
        {"term": {"brand": "XX" }}
      ],
      "must_not": [
        { "range": { "price": { "lte": 500 } }}
      ],
      "filter": [
        { "range": {"score": { "gte": 45 } }}
      ]
    }
  }
}
```

## Deal with Search Results

### [Sort Search Results](https://www.elastic.co/guide/en/elasticsearch/reference/current/sort-search-results.html#sort-search-results)

ES sorts result by related scores by default, but also supports sorting by **keyword, number, geo point, date, etc...**(in this situation, related score calculation was ignored)

```JSON
// order by some single constants
GET /indexName/_search
{
  "query": {
    "match_all": {}
  },
  "sort": [
    {
      "FIELD1": "desc",
      "FIELD2": "desc",
      // order by field1 first, and then field2
    }
  ]
}
// order by geo point
GET /indexName/_search
{
  "query": {
    "match_all": {}
  },
  "sort": [
    {
      "_geo_distance" : {
          "FIELD" : "lat，lon",
          "order" : "asc",
          "unit" : "km"
      }
    }
  ]
}
```

### [Paginate Search Results](https://www.elastic.co/guide/en/elasticsearch/reference/current/paginate-search-results.html)

By default, searches return the top 10 matching hits. 

```JSON
GET /indexName/_search
{
  "query": {
    "match_all": {}
  },
  "from": 990, // paginate starting point, default to 0
  "size": 10, // number of paginate search result 
  "sort": [
    {"price": "asc"}
  ]
}
```

**Deep Pagination**

![deep pagination](./images/Screenshot%202023-06-14%20at%209.55.13%20AM.png)

ES limits search size(from+size) to 10000, since it gives too much pressure to the servers. **search after(search from last search sort results) and scroll(snapshot search sort result and store in cache)** can preserve more than 10000 hits.

**Summary**

* from+size
  * pros: random access paging
  * cons: deep pagination(limits to 10000)
  * applications: google, amazon search results
* search after
  * pros: no limit, but max 10000 pages per search
  * cons: only can search after
  * applications: scroll down paginate
* scroll
  * pros: no limit, but max 10000 pages per search
  * cons: cost extra memory, and inconsistent 
  * applications: fetch and migrate big data, not suggested after ES7.1

### Highlight

```JSON
// by default, search field has to be same as highlight field
GET /indexName/_search
{
  "query": {
    "match": {
      "FIELD": "TEXT"
    }
  },
  "highlight": {
    "fields": { 
      "FIELD": {
        // enable search field can be different from highlight field
        "require_field_match": "false", 
        "pre_tags": "<em>",  
        "post_tags": "</em>" 
      }
    }
  }
}
```


