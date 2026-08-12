# LRU Cache (Java) — Built From Scratch

A distributed-style in-memory key-value cache built entirely from scratch in Java, without relying on `LinkedHashMap`, `OrderedDict`-equivalents, or any caching library. Built as a deep-dive project to prepare for backend/SDE interviews, covering the core data structure, TTL expiration, thread safety, a network protocol, and containerized deployment.

## Features

- **O(1) get/put** — hashmap + doubly linked list with sentinel head/tail nodes, no library shortcuts
- **TTL expiration** — both lazy (checked on access) and active (background sweep via `ScheduledExecutorService`)
- **Thread safety** — `ReentrantReadWriteLock` protecting all shared state, validated under concurrent load with a custom integrity checker
- **TCP network layer** — a simple Redis-style text protocol (`GET`/`SET`) served over raw sockets, one thread per client connection
- **Containerized** — multi-stage Docker build (JDK to compile, lean JRE to run)
- **Deployed on AWS EC2** *(in progress)*

## Architecture
```
Client (netcat / Java client)
        │  TCP, text protocol
        ▼
  CacheServer (ServerSocket, accept loop)
        │  submits each connection to a thread pool
        ▼
  ClientHandler (per-connection thread)
        │  parses GET/SET, dispatches to cache
        ▼
     LRUCache (ReentrantReadWriteLock)
        │
  HashMap<Integer, Node>  ⇄  Doubly Linked List (recency order)
```

## Protocol

Plain text, one command per line, newline-terminated:
```aiignore
SET <key> <value> → OK
GET <key> → <value> or -1 if missing/expired
```

## Design decisions worth knowing

- **`get()` takes the write lock, not a read lock.** Even a cache hit mutates the linked list (move-to-front for recency), and an expired hit also mutates the map. A plain read lock would be unsafe here — this was deliberately chosen for correctness over throughput.
- **Trade-off:** because of the above, this design is correctness-first, not optimized for concurrent reads — throughput scales mildly (not linearly) with thread count, since operations are effectively serialized. A sharded-lock design (splitting the cache into N independent segments, each with its own lock) would trade exact global LRU ordering for real parallel throughput — a natural "next step" if this were a production system.
- **Lazy vs active TTL expiration:** implemented both. Lazy is simpler (no background thread) but can let expired entries sit in memory until accessed. Active cleans up proactively via a periodic sweep, at the cost of a background thread touching shared state (hence the locking).

Concurrency correctness: 5 rounds × 3.2M ops with 16 threads + an active TTL sweeper running concurrently, validated via a custom integrity checker (checks list pointer consistency + map/list size agreement). Zero corruption across all rounds.

## Running locally

**Directly:**
```bash
javac -d out src/*.java
java -cp out CacheServer
```

**With Docker:**
```bash
docker build -t lru-cache-server .
docker run -p 6380:6380 lru-cache-server
```

**Connect with netcat (or PuTTY/telnet on Windows):**
```bash
nc localhost 6380
SET 1 100
GET 1
```