**Table of contents**

- [JVM Process Cache](#jvm-process-cache)
  - [Caffeine](#caffeine)
- [LUA](#lua)
  - [Variable and Loop](#variable-and-loop)
  - [Condition and Function](#condition-and-function)
- [Multi-level Cache](#multi-level-cache)
  - [OpenResty](#openresty)
    - [Installation](#installation)
    - [Quick Start](#quick-start)
    - [Request parms](#request-parms)
- [Cache Synchronization](#cache-synchronization)
  - [Solutions](#solutions)
  - [Canal](#canal)

**Traditional Cache Design Problem**
* Requests are processed by Tomcat, and the performance of Tomcat becomes the bottleneck of the entire system.
* When the Redis cache fails, it will have an impact on the database.

![Traditional Cache Design](./images/Screenshot%202023-06-20%20at%209.45.14%20AM.png)

**Multi-level Design**

![Multi-level Design](./images/Screenshot%202023-06-20%20at%2012.36.42%20PM.png)

# JVM Process Cache

* Distributed Cache(Redis)
  * pros: larger storage capacity, better reliability, and can be shared between clusters.
  * cons: accessing the cache has network overhead.
  * app: the amount of cached data is large, the reliability requirements are high, and it needs to be shared between clusters.
* Process Local Cache(HashMap, GuavaCache)
  * pros: read local memory, no network overhead, faster.
  * cons: limited storage capacity, low reliability, cannot be shared.
  * app: high performance requirements, small amount of cached data.

## [Caffeine](https://github.com/ben-manes/caffeine)

**Example**
```Java
@Test
void testBasicOps() {
  // Create cache basic
  Cache<String, String> cache = Caffeine.newBuilder().build();

  // store data
  cache.put("key", "data");

  // retrieve data, return null if it not present
  String data = cache.getIfPresent("key");

  // retrieve data, fetch in database if it not present
  String defaultKey = cache.get("defaultKey", key -> {
    // get data by key from database here
    return "data";
  });
}
```

**Policy**

By default, when a cached element expires, Caffeine will not automatically clean and evict it immediately. Instead, the eviction is done after a read or write operation, or during idle time.

* Evict by size
```Java
Cache<String, String> cache = Caffeine.newBuilder()
    .maximumSize(1)
    .build();
```
* Evict by time
```java
Cache<String, String> cache = Caffeine.newBuilder()
    .expireAfterWrite(Duration.ofSeconds(10))
    .build();
```
* Evict by reference: Set the cache as a soft or weak reference, and use GC to reclaim the cached data. Poor performance, not recommended.
  * soft ref: GC when insufficient memory
  * weak ref: GC even sufficient memory


# [LUA](https://www.lua.org/)

Lua is a powerful, efficient, lightweight, embeddable scripting language. 

## Variable and Loop

| Type | Detail |
| - | - |
| nil | equal to false in conditional check |
| boolean | true or false |
| number | double |
| string | "" or '' around |
| function | C or lua code |
| table | associative array, index can be number or string or table. {} is an empty table |

**Example**
```lua
-- string
local str = 'hello'
-- number
local num = 21
-- boolean
local flag = true
-- array
local arr = {'java', 'python', 'lua'}
-- table
local map =  {name='Jack', age=21}
-- first index is 1
print(arr[1])
-- get from table
print(map['name'])
print(map.name)
```

**Loop**
```lua
-- array loop
local arr = {'java', 'python', 'lua'}
for index,value in ipairs(arr) do
    print(index, value) 
end
-- table loop
local map = {name='Jack', age=21}
for key,value in pairs(map) do
   print(key, value) 
end
```

## Condition and Function

```lua
-- function syntax
function 函数名( argument1, argument2..., argumentn)
    -- body
    return 返回值
end
-- example
function printArr(arr)
    for index, value in ipairs(arr) do
        print(value)
    end
end
-- condition syntax
if(A and B or not C)
then
   --true body
end
-- or
if(A and B or not C)
then
   --true body
else
   --false body
end
```

# Multi-level Cache

## [OpenResty](https://openresty.org)

### Installation 

1. Uninstall nginx if installed
2. brew install openresty/brew/openresty
3. Set up env 
```sh
#OPENRESTY
export NGINX_HOME=/opt/homebrew/Cellar/openresty/{version}/nginx
export PATH=$PATH:$NGINX_HOME/sbin
```

### Quick Start

Example: proxy uri http://localhost/api/item/10002

1. Modify nginx.conf
```properties
# add under http
# lua package  
lua_package_path "/opt/homebrew/Cellar/openresty/1.21.4.1_2/lualib/?.lua;;";  
# c package
lua_package_cpath "/opt/homebrew/Cellar/openresty/1.21.4.1_2/lualib/?.so;;";
# add in server
location /api/item {
  # default response type
  default_type application/json;
  # response decided by lua/item.lua
  content_by_lua_file lua/item.lua;
}
```
2. Create and edit nginx/lua/item.lua

### Request parms

![Request parms](./images/Screenshot%202023-06-20%20at%209.45.14%20AM.png)

# Cache Synchronization

## Solutions

## Canal
