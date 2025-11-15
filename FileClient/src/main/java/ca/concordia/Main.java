package ca.concordia;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket; //connects to a server using TCP
import java.util.Scanner;

// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {
    public static void main(String[] args) {
        //Socket CLient
        System.out.println("Hello and welcome!");
        Scanner scanner = new Scanner(System.in); //creates a scanner to read text the user types in the terminal

        try{
            Socket clientSocket = new Socket("localhost", 12345); //tries to create a TCP connection to a server.
            System.out.println("Connected to the server at localhost:12345");

            // Interactive loop
            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                // Reduced banner (removed empty-line instruction per user request)
                System.out.println("Type commands (CREATE/WRITE/READ/DELETE/LIST/QUIT). Use quit/exit to close.");
                while (true) {
                    String userInput = scanner.nextLine();
                    if (userInput == null) {
                        // End of stream (shouldn't happen in console normally)
                        break;
                    }
                    String trimmed = userInput.trim();
                    if (trimmed.isEmpty()) {
                        // Silently ignore empty lines
                        continue;
                    }
                    if (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("quit")) {
                        writer.println("QUIT");
                        // Optionally read server acknowledgement
                        String resp = reader.readLine();
                        if (resp != null) {
                            System.out.println("Server: " + resp);
                        }
                        break;
                    }
                    // Send command to server
                    writer.println(userInput);
                    System.out.println("Message sent to the server: " + userInput);
                    // Read server response (may be null if server closed)
                    String response = reader.readLine();
                    if (response == null) {
                        System.out.println("Server closed the connection.");
                        break;
                    }
                    // If LIST command and server accidentally sent multi-line output, aggregate subsequent lines
                    if (trimmed.equalsIgnoreCase("LIST") && response.startsWith("SUCCESS: Files") && reader.ready()) {
                        StringBuilder aggregated = new StringBuilder(response);
                        // Collect any immediately available extra lines (filenames appearing separately)
                        while (reader.ready()) {
                            String extra = reader.readLine();
                            if (extra == null || extra.isEmpty()) {
                                break;
                            }
                            // Avoid duplicating a new SUCCESS/ERROR line (start of next response)
                            if (extra.startsWith("SUCCESS:") || extra.startsWith("ERROR:")) {
                                // push back? can't easily; just append and break
                                aggregated.append(" ").append(extra);
                                break;
                            }
                            aggregated.append(" ").append(extra.trim());
                        }
                        response = aggregated.toString();
                    }
                    System.out.println("Response from server: " + response);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try { clientSocket.close(); } catch (Exception ignore) {}
                scanner.close();
                System.out.println("Connection closed.");
            }


        }catch(Exception e) {
            e.printStackTrace();
        }
    }
}