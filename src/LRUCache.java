import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LRUCache {
    private static class Node{
        int key;
        int value;
        Node prev;
        Node next;
        long expiryTime;
        Node(int key, int value, long expiryTime){
            this.key = key;
            this.value = value;
            this.expiryTime = expiryTime;
        };
    }

    private final int capacity;
    private final HashMap<Integer, Node> map;
    private final long defaultTtlMillis;

    private final Node head;
    private final Node tail;

    private final ScheduledExecutorService scheduler;

    // Guards map + the linked list. get() mutates the list on every hit
    // (move-to-front), so it takes the WRITE lock, not the read lock -
    // see the class-level notes on why a plain read lock would be unsafe here.
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public LRUCache(int capacity, long defaultTtlMillis,  long sweepIntervalMillis){
        this.capacity = capacity;
        this.defaultTtlMillis = defaultTtlMillis;
        this.map = new HashMap<>();

        this.head = new Node(0,0, 0);
        this.tail = new Node(0,0, 0);

        head.next = tail;
        tail.prev = head;

        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::sweepExpired, sweepIntervalMillis, sweepIntervalMillis, TimeUnit.MILLISECONDS);
    }

    private void sweepExpired(){
        lock.writeLock().lock();
        try {
            Iterator<Map.Entry<Integer, Node>> it = map.entrySet().iterator();
            while(it.hasNext()){
                Node node = it.next().getValue();

                if (isExpired(node)){
                    removeNode(node);
                    it.remove();
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    private void removeNode(Node node){
        Node prevNode = node.prev;
        Node nextNode = node.next;

        prevNode.next = nextNode;
        nextNode.prev = prevNode;
    }

    private void addToFront(Node node){
        Node first = head.next;
        node.prev = head;
        node.next = first;
        head.next = node;
        first.prev = node;
    }

    public int get(int key){
        // Write lock, not read: even on a hit this mutates the linked list
        // (move-to-front), and on an expired hit it also mutates the map.
        lock.writeLock().lock();
        try {
            Node node = map.get(key);
            if (node == null){
                return -1;
            }

            if (isExpired(node)){
                removeNode(node);
                map.remove(key);
                return -1;
            }

            removeNode(node);
            addToFront(node);
            return node.value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void put(int key, int value){
        lock.writeLock().lock();
        try {
            Node existing = map.get(key);
            if (existing != null){
                existing.value = value;
                existing.expiryTime = System.currentTimeMillis() + defaultTtlMillis;
                removeNode(existing);
                addToFront(existing);
                return;
            }

            Node node = new Node(key, value, System.currentTimeMillis() + defaultTtlMillis);
            map.put(key, node);
            addToFront(node);
            if (map.size() > capacity){
                Node lru = tail.prev;
                removeNode(lru);
                map.remove(lru.key);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean isExpired(Node node){
        return System.currentTimeMillis() > node.expiryTime;
    }

    public int size() {
        // Pure read of map state - no list mutation, so a read lock is
        // actually correct here (unlike get()).
        lock.readLock().lock();
        try {
            return map.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    // Walks the list in both directions and cross-checks it against the map.
    // Takes the write lock so it sees a consistent snapshot - a read lock
    // would not exclude concurrent get()/put() mutation of the list.
    // Throws IllegalStateException with a description of the first problem
    // found; returns normally if the structure is consistent.
    public void validateIntegrity(){
        lock.writeLock().lock();
        try {
            if (head.prev != null){
                throw new IllegalStateException("head.prev is not null - sentinel corrupted");
            }
            if (tail.next != null){
                throw new IllegalStateException("tail.next is not null - sentinel corrupted");
            }

            java.util.Set<Integer> seenKeys = new java.util.HashSet<>();
            Node node = head.next;
            int forwardCount = 0;
            while (node != tail){
                if (node.prev.next != node){
                    throw new IllegalStateException("broken forward link at key=" + node.key);
                }
                if (!seenKeys.add(node.key)){
                    throw new IllegalStateException("key=" + node.key + " appears twice in list (cycle?)");
                }
                if (!map.containsKey(node.key)){
                    throw new IllegalStateException("key=" + node.key + " is in list but missing from map");
                }
                if (map.get(node.key) != node){
                    throw new IllegalStateException("map entry for key=" + node.key + " does not point to the list node");
                }
                forwardCount++;
                if (forwardCount > map.size()){
                    throw new IllegalStateException("list traversal exceeded map size - likely cycle");
                }
                node = node.next;
            }

            Node back = tail.prev;
            int backwardCount = 0;
            while (back != head){
                if (back.next.prev != back){
                    throw new IllegalStateException("broken backward link at key=" + back.key);
                }
                backwardCount++;
                back = back.prev;
            }

            if (forwardCount != backwardCount){
                throw new IllegalStateException("forward count (" + forwardCount + ") != backward count (" + backwardCount + ")");
            }
            if (forwardCount != map.size()){
                throw new IllegalStateException("list size (" + forwardCount + ") != map size (" + map.size() + ")");
            }
            if (map.size() > capacity){
                throw new IllegalStateException("map size (" + map.size() + ") exceeds capacity (" + capacity + ")");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static void main(String[] args) {
        LRUCache cache = new LRUCache(2, 2000, 1000); // capacity, TTL, sweep interval
        cache.put(1, 1);
        cache.put(2, 2);
        System.out.println(cache.get(1));
        cache.put(3, 3);
        System.out.println(cache.get(2));
        cache.put(4, 4);
        System.out.println(cache.get(1));
        System.out.println(cache.get(3));
        System.out.println(cache.get(4));
        cache.shutdown();

        try {
            LRUCache ttlCache = new LRUCache(2, 2000, 1000);
            ttlCache.put(10, 100);
            System.out.println(ttlCache.get(10)); // should print 100, not expired yet

            Thread.sleep(2500); // wait past the TTL

            System.out.println(ttlCache.get(10)); // should print -1, expired
            ttlCache.shutdown();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            LRUCache activeCache = new LRUCache(5, 1000, 500); // 1s TTL, sweep every 500ms
            activeCache.put(20, 200);
            activeCache.put(21, 210);
            System.out.println("size before sleep: " + activeCache.size());

            Thread.sleep(2000); // wait long enough for the background sweep to run and clean up

            System.out.println("size after sleep (no get() called): " + activeCache.size());
            activeCache.shutdown();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
