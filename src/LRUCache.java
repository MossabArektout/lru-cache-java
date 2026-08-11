import java.util.HashMap;

public class LRUCache {
    private static class Node{
        int key;
        int value;
        Node prev;
        Node next;
        Node(int key, int value){
            this.key = key;
            this.value = value;
        };
    }

    private int capacity;
    private HashMap<Integer, Node> map;

    private Node head;
    private Node tail;

    public LRUCache(int capacity){
        this.capacity = capacity;
        this.map = new HashMap<>();

        this.head = new Node(0,0);
        this.tail = new Node(0,0);

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
        removeNode(node);
        addToFront(node);


        return node.value;
    }

    public void put(int key, int value){
        if (map.containsKey(key)){
            Node node = map.get(key);
            node.value = value;
            removeNode(node);
            addToFront(node);
            return;
        }

        Node node = new Node(key, value);
        map.put(key,node);
        addToFront(node);
        if (map.size()>capacity){
            Node lru = tail.prev;
            removeNode(lru);
            map.remove(lru.key);
        }
    }

    public static void main(String[] args) {
        LRUCache cache = new LRUCache(2);
        cache.put(1, 1);
        cache.put(2, 2);
        System.out.println(cache.get(1));
        cache.put(3, 3);
        System.out.println(cache.get(2));
        cache.put(4, 4);
        System.out.println(cache.get(1));
        System.out.println(cache.get(3));
        System.out.println(cache.get(4));
    }
}
