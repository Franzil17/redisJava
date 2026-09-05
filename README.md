[![progress-banner](https://backend.codecrafters.io/progress/redis/c23458bf-b1ad-4388-9972-51763c773cef)](https://app.codecrafters.io/users/Franzil17?r=2qF)

# Redis Java Agent

A Redis-compatible server built from scratch in pure Java, implementing the **RESP (Redis Serialization Protocol)**. Any standard Redis client — `redis-cli`, Jedis, Lettuce, redis-py, ioredis — can connect to it with zero configuration.

> Part of the [CodeCrafters "Build Your Own Redis" Challenge](https://codecrafters.io/challenges/redis).

---

## Architecture

```
Any Redis Client (redis-cli / Jedis / Lettuce / Python / Node)
        │
        │  RESP over TCP :6379
        ▼
   Main.java  ──  ServerSocket, accept loop, thread-per-client
        │
        ▼
  CommandHandler  (one per connected client)
     ├── RespParser     ← reads RESP arrays from InputStream
     ├── DataStore      ← ConcurrentHashMap (thread-safe, singleton)
     └── RespWriter     ← encodes RESP responses to OutputStream
```

---

## Supported Commands

| Command | Syntax | Response |
|---------|--------|----------|
| `PING` | `PING [message]` | `+PONG` or echoes message |
| `SET` | `SET key value` | `+OK` |
| `GET` | `GET key` | Bulk string or `$-1` (nil) |
| `DEL` | `DEL key [key ...]` | Integer count of deleted keys |
| `KEYS` | `KEYS pattern` | Array of matching keys (`*` glob supported) |

---

## How to Build & Run

### Requirements
- Java 21+ (`C:\Program Files\Java\jdk-21.0.10`)
- Maven (optional — direct `javac` also works)

### Compile with javac (no Maven needed)
```powershell
# From the project root
mkdir out
javac -d out src/main/java/RespParser.java src/main/java/RespWriter.java `
             src/main/java/DataStore.java  src/main/java/CommandHandler.java `
             src/main/java/Main.java
```

### Run the server
```powershell
java -cp out Main
```

Output:
```
Redis Java Agent starting on port 6379 ...
Supported commands: PING | SET | GET | DEL | KEYS
Connect with: redis-cli -h 127.0.0.1 -p 6379
----------------------------------------------------
[Server] Listening — waiting for clients...
```

### Build with Maven
```bash
mvn package -Ddir=.
java -jar codecrafters-redis.jar
```

---

## How Clients Connect

Your server speaks standard RESP, so **every Redis client works out of the box** — just point it at `127.0.0.1:6379`.

### Request / Response Flow

```
Client                              Your Java Server
  │                                       │
  │  *3\r\n$3\r\nSET\r\n                 │
  │  $4\r\nname\r\n$7\r\nFranzil\r\n ──► │  RespParser.readCommand()
  │                                       │    → ["SET", "name", "Franzil"]
  │                                       │  CommandHandler → DataStore.set()
  │  +OK\r\n                        ◄─── │  RespWriter.writeSimpleString("OK")
```

### Multi-Client Support

`Main.java` spawns a **daemon thread per accepted connection**. `DataStore` uses `ConcurrentHashMap`, so all threads are safe to read/write simultaneously.

```
Client A ──► Thread "client-52341" ─┐
Client B ──► Thread "client-52342" ─┼──► DataStore (ConcurrentHashMap) ✅
Client C ──► Thread "client-52343" ─┘
```

---

## Client Examples

### 1. redis-cli
```bash
redis-cli -h 127.0.0.1 -p 6379
127.0.0.1:6379> SET name Franzil
OK
127.0.0.1:6379> GET name
"Franzil"
127.0.0.1:6379> KEYS *
1) "name"
127.0.0.1:6379> DEL name
(integer) 1
```

### 2. Jedis (Java)
Add to `pom.xml`:
```xml
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
    <version>5.1.0</version>
</dependency>
```
```java
Jedis jedis = new Jedis("127.0.0.1", 6379);

jedis.set("name", "Franzil");        // SET  → "OK"
String val = jedis.get("name");      // GET  → "Franzil"
long del = jedis.del("name");        // DEL  → 1
Set<String> keys = jedis.keys("*"); // KEYS → [city, lang, ...]
jedis.close();
```

### 3. Lettuce (reactive Java — drop-in Jedis swap)
```java
RedisClient client = RedisClient.create("redis://127.0.0.1:6379");
StatefulRedisConnection<String, String> conn = client.connect();
RedisCommands<String, String> cmds = conn.sync();

cmds.set("name", "Franzil");
String val = cmds.get("name");  // → "Franzil"
conn.close();
client.shutdown();
```

### 4. Python (redis-py)
```python
import redis
r = redis.Redis(host='127.0.0.1', port=6379, decode_responses=True)

r.set('name', 'Franzil')
print(r.get('name'))    # → Franzil
print(r.keys('*'))      # → ['name']
r.delete('name')
```

### 5. Node.js (ioredis)
```javascript
const Redis = require('ioredis');
const redis = new Redis({ host: '127.0.0.1', port: 6379 });

await redis.set('name', 'Franzil');
const val = await redis.get('name');  // → 'Franzil'
await redis.del('name');
```

---

## Client Compatibility

| Client     | Language | Works | Notes                    |
|------------|----------|-------|--------------------------|
| `redis-cli`| CLI      | ✅    | Install Redis for Windows |
| Jedis      | Java     | ✅    | Add Maven dependency      |
| Lettuce    | Java     | ✅    | Drop-in swap for Jedis    |
| redis-py   | Python   | ✅    | `pip install redis`       |
| ioredis    | Node.js  | ✅    | `npm install ioredis`     |

---

## Project Structure

```
src/main/java/
├── Main.java           # Entry point — TCP accept loop, thread-per-client
├── RespParser.java     # RESP protocol parser (reads *N/$N arrays)
├── RespWriter.java     # RESP protocol encoder (simple/bulk/array/error)
├── DataStore.java      # Thread-safe ConcurrentHashMap store + KEYS glob
└── CommandHandler.java # Command dispatcher (PING/SET/GET/DEL/KEYS)
```

---

## CodeCrafters Submission

```bash
codecrafters submit
```
