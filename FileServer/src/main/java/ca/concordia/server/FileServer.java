package ca.concordia.server;
import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileServer {

    private FileSystemManager fsManager;
    private int port;
    private ExecutorService threadPool;
    public FileServer(int port, String fileSystemName, int totalSize) throws Exception {
        // Initialize the FileSystemManager
        this.fsManager = FileSystemManager.getInstance(fileSystemName,
                10*128 );
        this.port = port;
        // Initialize thread pool for handling multiple concurrent clients
        this.threadPool = Executors.newCachedThreadPool();
    }

    public void start(){
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("Server started. Listening on port 12345...");

            while (true) {
                //added try and catch to handle connection errors
                try{
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket);
                    // Submit client handling to thread pool for concurrent processing
                    threadPool.submit(new ClientHandler(clientSocket, fsManager));
                }catch (Exception e){
                    System.err.println("ERROR:"+ e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        } finally {
            // Shutdown thread pool when server stops
            threadPool.shutdown();
        }
    }

    /**
     * ClientHandler - Handles individual client requests in separate threads
     */
    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final FileSystemManager fsManager;

        public ClientHandler(Socket clientSocket, FileSystemManager fsManager) {
            this.clientSocket = clientSocket;
            this.fsManager = fsManager;
        }

        @Override
        public void run() {
            try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                // Could be implemented later, if time permits
                // Added a help command to facilitate error handling. 
                // writer.println("CONNECTED: Welcome to File System Server. Type HELP for commands."); 

                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Received from client: " + line);

                    try{
                        // Split the incoming line into at most 3 parts: COMMAND, FILENAME, CONTENT (which may contain spaces)
                        String[] parts = line.trim().split("\\s+", 3);
                        String command = parts[0].toUpperCase();

                        switch (command) {
                            //implmentation of needed commands
                            case "CREATE":
                                // Error handling for missing filename
                                if (parts.length < 2) {
                                    writer.println("ERROR: Missing filename.");
                                    break;
                                }
                                fsManager.createFile(parts[1]);
                                writer.println("SUCCESS: File '" + parts[1] + "' created.");
                                writer.flush();
                                break;

                            case "DELETE":
                                // Error handling for missing filename
                                if (parts.length < 2) {
                                    writer.println("ERROR: Missing filename.");
                                    break;
                                }
                                fsManager.deleteFile(parts[1]);
                                writer.println("SUCCESS: File '" + parts[1] + "' deleted.");
                                break;

                            case "LIST":
                                String[] files = fsManager.listFiles();
                                if (files.length == 0){
                                    writer.println("SUCCESS: No files.");
                                }
                                else {
                                    // Send as a single line to avoid client needing multiple reads
                                    writer.println("SUCCESS: Files: " + String.join(", ", files));
                                }
                                break;

                            case "READ":
                                // Error handling for missing filename
                                if (parts.length < 2) {
                                    writer.println("ERROR: Missing filename.");
                                    break;
                                }
                                byte[] contents = fsManager.readFile(parts[1],0,Integer.MAX_VALUE);
                                writer.println("CONTENT: " + new String(contents));
                                break;

                            case "WRITE":
                                // Error handling for missing filename
                                if (parts.length < 2) {
                                    writer.println("ERROR: Missing filename.");
                                    break;
                                }
                                // Error handling for missing content (third part)
                                if (parts.length < 3 || parts[2].isEmpty()) {
                                    writer.println("ERROR: Missing content.");
                                    break;
                                }
                                String filename = parts[1];
                                String content = parts[2]; // supports spaces due to split limit above
                                fsManager.writeFile(filename, content.getBytes());
                                writer.println("SUCCESS: Content written to '" + filename + "'.");
                                break;

                            case "QUIT":
                                writer.println("SUCCESS: Disconnecting.");
                                return;

                            default:
                                writer.println("ERROR: Unknown command.");
                                break;
                         }
                    }catch (Exception e){
                        String msg = e.getMessage();
                        if (msg == null || msg.isEmpty()) {
                            msg = "Unknown error";
                        }
                        msg = msg.trim();

                        // Avoid double "ERROR:" prefix if downstream already formatted the message
                        String upper = msg.toUpperCase();
                        if (upper.startsWith("ERROR:")) {
                            writer.println(msg);
                        } else {
                            writer.println("ERROR: " + msg);
                        }

                        // Normalize server stderr prefix once
                        if (upper.startsWith("ERROR:")) {
                            System.err.println(msg);
                        } else {
                            System.err.println("ERROR: " + msg);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error handling client: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                    System.out.println("Client disconnected: " + clientSocket);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

}
