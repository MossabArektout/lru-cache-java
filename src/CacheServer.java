import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CacheServer {
    public static void main(String[] args){
        int port = 6380;
        LRUCache cache = new LRUCache(1000,60000,5000);

        ExecutorService pool = Executors.newFixedThreadPool(50);

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            System.out.println(
                    "Cache server listening on port " + port
            );

            while (true) {
                Socket clientSocket = serverSocket.accept();

                pool.submit(
                        new ClienttHandler(clientSocket, cache)
                );
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
