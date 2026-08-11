import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    private int capacity;
    private HashMap<Integer, Node> map;
    private long defaultTtlMillis;

    private Node head;
    private Node tail;

    private ScheduledExecutorService scheduler;

    public LRUCache(int capacity, long defaultTtlMillis,  long sweepIntervalMillis){
        this.capacity = capacity;
        this.defaultTtlMillis = defaultTtlMillis;
        this.map = new HashMap<>();

        this.head = new Node(0,0, 0);
        this.tail = new Node(0,0, 0);

        head.next = tail;
        tail.prev = head;

        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::sweepExpired, sweepIntervalMillis,sweepIntervalMillis, TimeUnit.MICROSECONDS);
    }

    private synchronized void sweepExpired(){
        Iterator<Map.Entry<Integer, Node>> it = map.entrySet().iterator();
        while(it.hasNext()){
            Node node = it.next().getValue();

            if (isExpired(node)){
                removeNode(node);
                it.remove();
            }
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
        if (!map.containsKey(key)){
            return -1;
        }

        Node node = map.get(key);

        if (isExpired(node)){
            removeNode(node);
            map.remove(key);
            return  -1;
        }
        removeNode(node);
        addToFront(node);


        return node.value;
    }

    public void put(int key, int value){
        if (map.containsKey(key)){
            Node node = map.get(key);
            node.value = value;
            node.expiryTime = System.currentTimeMillis() + defaultTtlMillis;
            removeNode(node);
            addToFront(node);
            return;
        }

        Node node = new Node(key, value, System.currentTimeMillis() + defaultTtlMillis);
        map.put(key,node);
        addToFront(node);
        if (map.size()>capacity){
            Node lru = tail.prev;
            removeNode(lru);
            map.remove(lru.key);
        }
    }

    private boolean isExpired(Node node){
        return System.currentTimeMillis() > node.expiryTime;
    }

    public int size() {
        return map.size();
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
