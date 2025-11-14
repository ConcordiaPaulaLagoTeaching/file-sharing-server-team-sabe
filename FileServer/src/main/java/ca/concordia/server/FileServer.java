package ca.concordia.server;
import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {

    private FileSystemManager fsManager;
    private int port;
    public FileServer(int port, String fileSystemName, int totalSize) throws Exception {
        // Initialize the FileSystemManager
        this.fsManager = FileSystemManager.getInstance(fileSystemName,
                10*128 );
        this.port = port;
    }

    public void start(){
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("Server started. Listening on port 12345...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Handling client: " + clientSocket);
                try (
                        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
                ) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("Received from client: " + line);
                        String[] parts = line.split(" ");
                        String command = parts[0].toUpperCase();

                        switch (command) {
                            //implmentation of needed commands
                            case "CREATE":
                                fsManager.createFile(parts[1]);
                                writer.println("SUCCESS: File '" + parts[1] + "' created.");
                                writer.flush();
                                break;

                            case "DELETE":
                                fsManager.deleteFile(parts[1]);
                                writer.println("SUCCESS: File '" + parts[1] + "' deleted.");
                                break;

                            case "LIST":
                                String[] files = fsManager.listFiles(); 
                                if (files.length == 0){
                                    writer.println("SUCCESS: No files.");
                                }
                                else {
                                    writer.println("SUCCESS: Files:\n" + String.join("\n", files));
                                }
                                break;

                            case "READ":
                                byte[] contents = fsManager.readFile(parts[1],0,Integer.MAX_VALUE);
                                writer.println("CONTENT: " + new String(contents));
                                break;

                            case "WRITE":
                            String[] writeParts = parts[1].split("\\s+",2);
                            if (writeParts.length<2){
                                writer.println("ERROR: Missing content.");
                                break;
                            }
                            String filename = writeParts[0];
                            String content = writeParts[1];
                            fsManager.writeFile(filename, content.getBytes());
                            writer.println("SUCCESS: content was sucessfully written in  "+filename+ ".");
                                break;

                            case "QUIT":
                                writer.println("SUCCESS: Disconnecting.");
                                return;

                            default:
                                writer.println("ERROR: Unknown command.");
                                break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        clientSocket.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }

}
