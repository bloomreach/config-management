# Config Management Core

## Introduction
Config management core library provides you a tool to store json documents in redis database 
and do read/search queries over the stored documents. As redis doesn't provide a way to search
inside json documents (stored in string format), this tool is specially useful to retrieve data
at any level in json document (including inside arrays, full navigation) and also to select
 (based on equality conditions) over different fields present in the document.
 
### Examples:
```
  {"a": 
    {
      "b": [
        {"c": "v1"}, 
        {"c": "v2"}
      ]  
    }
  }
```
 
is stored against key "K" in CM System, the redis will contain following entries:
```
"K" = {"a": {"b" : [{"c": "v1"}, {"c": "v2"}]}}
"K/a" = {"b" : [{"c": "v1"}, {"c": "v2"}]}
"K/a/b" = [{"c": "v1"}, {"c": "v2"}]
```

To read "K/a" redis will directly return the value and the library will just return  that value. 
To read the value for key "K/a/b[0]/c" which is not directly present, the library reads value for "K/a/b" 
then it applies the JSONPath library to extract the value for exact key.

## Getting Started

  * Prerequisites: Java 8, sbt, redis
  * Installation: 
    1. Do a git clone of this project
    2. cd config-management-core  
    3. sbt clean test:compile publish (builds your project and creates the jar)
    4. sbt eclipse (If you want to import the project to eclipse)
    
## How to use
  * start redis-server (redis-server &)
  * CacheUpdater.scala provide methods to update the redis database
  * CacheReader.scala provides methods to read from the redis database
  * Use CacheIntegrationTest.scala (an integration test) to understand and debug. 
    It connects to local redis and helps you and testing the writes and reads from redis.
    1. E.g. "Test update recursively with a mid level key" test case uses the file 
       ./src/test/resources/testDocument.json. It uses the method updateRecursively of the CacheUpdater class.
       It updates all the parent of the key, children of the key and key itself.
        
       a. If you want to see the state of the redis database after the method has executed, 
          add a breakpoint after the call to method updateRecursively. And then run the command redis-cli
          After that you can run commands (keys *) and then (get "{your_key}")
       
       b. This updateRecursively method updates the database recursively for keys. 
          Which means that children of the key in the example will also be inserted into the database. 
          And the recursion stops at the level where it finds first array element inside the json structure.
       
       c. This test reads the value for customer element (which is child of config1 element) from redis database
          and then compares it with the expected value.
          
       d. You can modify this test case for more examples in the ./src/test/resources directory
       
  * Read API: Use the api DocumentSearcher.getData method
    This method provides an api to read from redis database. This can be consumed by the clients of this library who are only interested
    in reading data from config management core. For testDocument.json document examples keys for read are:
    1. {db}/{document}/{realm}/config (In the DocumentSearcherTest examples, db = "merchants", document = "example_merchant", realm = "TEST")
    2. {db}/{document}/{realm}/config/config1
    4. {db}/{document}/{realm}/config/config1/id
    5. {db}/{document}/{realm}/config/config2/config3%5B1%5D (to read the first array element, note: index starts with 1 here)
    7. {db}/{document}/{realm}/config/config4%5B1%5D/config5
    8. {db}/{document}/{realm}/config/config4%5Bconfig5='This_Year'%5D (to find all the document where config5 = "This_Year")
       In this example token after last / explains selection that you can do to select data based on a condition
  
  * Selection API: Use the api DocumentSearcher.searchData method
    This method provides an api to select data from set of documents available in redis database. This can be consumed by clients of this library
    who are interested in selecting some fields of a document based on conditions. Take help of unit test DocumentSearcherTest for search data
    to understand how to use this api
  
## License 
  * [Apache License Version 2.0] (http://www.apache.org/licenses/LICENSE-2.0)
  
## References
  * [jedis](https://github.com/xetorthio/jedis)
  * [json4s](https://github.com/json4s/json4s)
  * [JsonPath](https://github.com/jayway/JsonPath)