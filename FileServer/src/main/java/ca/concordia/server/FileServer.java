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
    private ExecutorService executorService;

    public FileServer(int port, String fileSystemName, int totalSize, int blockSize, int maxFiles, int maxBlocks) {
        // Initialize the FileSystemManager
        this.fsManager = new FileSystemManager(fileSystemName, totalSize, blockSize, maxFiles, maxBlocks);
        this.port = port;
        // Use cached thread pool for scalability - supports thousands of concurrent clients
        this.executorService = Executors.newCachedThreadPool();
        System.out.println("Using cached thread pool for client handling");
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("File Server started. Listening on port " + port + "...");
            System.out.println("Server ready to accept client connections.");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());

                // Handle each client in a separate thread
                executorService.submit(new ClientHandler(clientSocket, fsManager));
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        } finally {
            executorService.shutdown();
        }
    }

    /**
     * Inner class to handle client connections
     */
    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private FileSystemManager fsManager;

        public ClientHandler(Socket socket, FileSystemManager fsManager) {
            this.clientSocket = socket;
            this.fsManager = fsManager;
        }

        @Override
        public void run() {
            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Received from client: " + line);

                    try {
                        String response = processCommand(line);
                        writer.println(response);
                        writer.flush();
                    } catch (Exception e) {
                        writer.println(e.getMessage());
                        writer.flush();
                    }
                }
            } catch (Exception e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                    System.out.println("Client disconnected: " + clientSocket.getInetAddress());
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        private String processCommand(String commandLine) throws Exception {
            if (commandLine == null || commandLine.trim().isEmpty()) {
                return "ERROR: empty command";
            }

            String[] parts = commandLine.trim().split("\\s+", 3);
            String command = parts[0].toUpperCase();

            switch (command) {
                case "CREATE":
                    return handleCreate(parts);

                case "WRITE":
                    return handleWrite(parts);

                case "READ":
                    return handleRead(parts);

                case "DELETE":
                    return handleDelete(parts);

                case "LIST":
                    return handleList();

                case "QUIT":
                case "EXIT":
                    return "SUCCESS: Disconnecting.";

                default:
                    return "ERROR: Unknown command '" + command + "'";
            }
        }

        private String handleCreate(String[] parts) throws Exception {
            if (parts.length < 2) {
                return "ERROR: CREATE command requires filename";
            }

            String filename = parts[1];

            try {
                fsManager.createFile(filename);
                return "SUCCESS: File '" + filename + "' created.";
            } catch (Exception e) {
                return e.getMessage();
            }
        }

        private String handleWrite(String[] parts) throws Exception {
            if (parts.length < 3) {
                return "ERROR: WRITE command requires filename and content";
            }

            String filename = parts[1];
            String content = parts[2];
            byte[] contentBytes = content.getBytes();

            try {
                fsManager.writeFile(filename, contentBytes);
                return "SUCCESS: Written " + contentBytes.length + " bytes to '" + filename + "'.";
            } catch (Exception e) {
                return e.getMessage();
            }
        }

        private String handleRead(String[] parts) throws Exception {
            if (parts.length < 2) {
                return "ERROR: READ command requires filename";
            }

            String filename = parts[1];

            try {
                byte[] contents = fsManager.readFile(filename);
                String contentStr = new String(contents);
                return "SUCCESS: Content of '" + filename + "': " + contentStr;
            } catch (Exception e) {
                return e.getMessage();
            }
        }

        private String handleDelete(String[] parts) throws Exception {
            if (parts.length < 2) {
                return "ERROR: DELETE command requires filename";
            }

            String filename = parts[1];

            try {
                fsManager.deleteFile(filename);
                return "SUCCESS: File '" + filename + "' deleted.";
            } catch (Exception e) {
                return e.getMessage();
            }
        }

        private String handleList() {
            String[] files = fsManager.listFiles();

            if (files.length == 0) {
                return "SUCCESS: No files on server.";
            }

            StringBuilder sb = new StringBuilder("SUCCESS: Files on server:\n");
            for (String filename : files) {
                sb.append("  - ").append(filename).append("\n");
            }
            return sb.toString().trim();
        }
    }
}
