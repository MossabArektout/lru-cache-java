import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientTest {

    public static void main(String[] args) {
        try (
                Socket socket = new Socket("localhost", 6380);
                PrintWriter out = new PrintWriter(
                        socket.getOutputStream(), true
                );
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream())
                )
        ) {
            out.println("SET 1 111");
            System.out.println(in.readLine());

            out.println("GET 1");
            System.out.println(in.readLine());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}