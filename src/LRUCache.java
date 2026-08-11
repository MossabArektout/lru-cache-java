import java.util.HashMap;

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

    public LRUCache(int capacity, long defaultTtlMillis){
        this.capacity = capacity;
        this.defaultTtlMillis = defaultTtlMillis;
        this.map = new HashMap<>();

        this.head = new Node(0,0, 0);
        this.tail = new Node(0,0, 0);

        head.next = tail;
        tail.prev = head;
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

    public static void main(String[] args) {
        LRUCache cache = new LRUCache(2, 2000);
        cache.put(1, 1);
        cache.put(2, 2);
        System.out.println(cache.get(1));
        cache.put(3, 3);
        System.out.println(cache.get(2));
        cache.put(4, 4);
        System.out.println(cache.get(1));
        System.out.println(cache.get(3));
        System.out.println(cache.get(4));

        try {
            LRUCache ttlCache = new LRUCache(2, 2000); // 2 second TTL
            ttlCache.put(10, 100);
            System.out.println(ttlCache.get(10)); // should print 100, not expired yet

            Thread.sleep(2500); // wait past the TTL

            System.out.println(ttlCache.get(10)); // should print -1, expired
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean isExpired(Node node){
        return System.currentTimeMillis() > node.expiryTime;
    }
}
