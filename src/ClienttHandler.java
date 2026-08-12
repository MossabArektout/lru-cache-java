import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClienttHandler implements Runnable{
    private final Socket socket;
    private final LRUCache cache;

    public ClienttHandler(Socket socket, LRUCache cache){
        this.socket = socket;
        this.cache = cache;
    }

    @Override
    public void run(){
        try (
                BufferedReader in = new BufferedReader(
                        new InputStreamReader((socket.getInputStream()))
                        );
                PrintWriter out = new PrintWriter(
                        socket.getOutputStream(), true
                )
        ){
            // Read commands from the client
            String line;

            while((line = in.readLine()) != null){
                String response = cache.handleCommand(line);
                out.println(response);
            }
        } catch (IOException e){
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e){}
        }
    }
}
