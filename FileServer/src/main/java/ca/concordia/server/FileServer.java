package ca.concordia.server;

import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Multithreaded file server that handles client connections and file operations.
 *
 * Architecture:
 * - Main thread listens for incoming connections on a ServerSocket
 * - Each client connection is handled in its own thread via ExecutorService
 * - All threads share the same FileSystemManager (which handles synchronization)
 *
 * The server uses a cached thread pool, which:
 * - Creates new threads on demand when all existing threads are busy
 * - Reuses idle threads from previous connections
 * - Can scale to thousands of concurrent clients
 *
 * Protocol: Simple text-based commands (CREATE, WRITE, READ, DELETE, LIST, QUIT)
 */
public class FileServer {

    private final FileSystemManager fsManager;  // Shared filesystem, handles synchronization
    private final int port;                      // Port to listen on
    private final ExecutorService executorService;  // Thread pool for client connections

    /**
     * Creates a new file server.
     *
     * @param port Network port to listen on (e.g., 12345)
     * @param fileSystemName Name of file to persist filesystem to disk
     * @param totalSize Total storage capacity (informational)
     * @param blockSize Size of each storage block in bytes
     * @param maxFiles Maximum number of files that can be stored
     * @param maxBlocks Maximum number of storage blocks
     */
    public FileServer(int port, String fileSystemName, int totalSize, int blockSize, int maxFiles, int maxBlocks) {
        // Set up the filesystem manager - this handles all the actual file operations
        this.fsManager = new FileSystemManager(fileSystemName, totalSize, blockSize, maxFiles, maxBlocks);
        this.port = port;

        // Cached thread pool: creates threads as needed, reuses idle ones
        // Much better than fixed-size pool for variable client loads
        this.executorService = Executors.newCachedThreadPool();
        System.out.println("Using cached thread pool for client handling");
    }

    /**
     * Starts the server and begins accepting client connections.
     * This method blocks forever (until an exception occurs).
     */
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("File Server started. Listening on port " + port + "...");
            System.out.println("Server ready to accept client connections.");

            // Main server loop - accepts connections forever
            while (true) {
                // This blocks until a client connects
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());

                // Spin up a new thread (or reuse an idle one) to handle this client
                // This allows us to immediately go back to accepting more connections
                executorService.submit(new ClientHandler(clientSocket, fsManager));
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        } finally {
            // Clean shutdown of thread pool (only reached if server crashes)
            executorService.shutdown();
        }
    }

    /**
     * Handles a single client connection in its own thread.
     *
     * Each client gets one of these. The handler:
     * 1. Reads commands from the client
     * 2. Parses and executes them
     * 3. Sends back responses
     * 4. Repeats until client disconnects
     *
     * Multiple ClientHandlers can run simultaneously, all sharing the same
     * FileSystemManager (which is thread-safe).
     */
    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final FileSystemManager fsManager;

        public ClientHandler(Socket socket, FileSystemManager fsManager) {
            this.clientSocket = socket;
            this.fsManager = fsManager;
        }

        @Override
        public void run() {
            // Set up I/O streams for talking to the client
            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                String line;
                // Keep reading commands until client disconnects
                while ((line = reader.readLine()) != null) {
                    System.out.println("Received from client: " + line);

                    try {
                        // Parse and execute the command
                        String response = processCommand(line);
                        writer.println(response);
                        writer.flush();
                    } catch (Exception e) {
                        // Send error message back to client
                        // Note: We catch exceptions here so one bad command doesn't kill the connection
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
                    // Ignore errors on close - client is gone anyway
                }
            }
        }

        /**
         * Parses a command line and routes it to the appropriate handler.
         *
         * Commands are simple text-based: "COMMAND arg1 arg2 ..."
         * We split on whitespace, with a max of 3 parts so content with spaces works.
         *
         * @param commandLine Raw command string from client
         * @return Response string to send back to client
         */
        private String processCommand(String commandLine) throws Exception {
            if (commandLine == null || commandLine.trim().isEmpty()) {
                return "ERROR: empty command";
            }

            // Split into max 3 parts: command, filename, and everything else as content
            // This allows WRITE commands to have content with spaces
            String[] parts = commandLine.trim().split("\\s+", 3);
            String command = parts[0].toUpperCase();

            // Route to appropriate handler based on command
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

        /**
         * Handles CREATE command: creates a new empty file.
         * Format: CREATE <filename>
         */
        private String handleCreate(String[] parts) throws Exception {
            if (parts.length < 2) {
                return "ERROR: CREATE command requires filename";
            }

            String filename = parts[1];

            try {
                fsManager.createFile(filename);
                return "SUCCESS: File '" + filename + "' created.";
            } catch (Exception e) {
                // FileSystemManager throws exceptions with proper error messages
                return e.getMessage();
            }
        }

        /**
         * Handles WRITE command: writes content to a file (overwrites existing).
         * Format: WRITE <filename> <content>
         */
        private String handleWrite(String[] parts) throws Exception {
            if (parts.length < 3) {
                return "ERROR: WRITE command requires filename and content";
            }

            String filename = parts[1];
            String content = parts[2];  // Everything after filename
            byte[] contentBytes = content.getBytes();

            try {
                fsManager.writeFile(filename, contentBytes);
                return "SUCCESS: Written " + contentBytes.length + " bytes to '" + filename + "'.";
            } catch (Exception e) {
                return e.getMessage();
            }
        }

        /**
         * Handles READ command: reads and returns file contents.
         * Format: READ <filename>
         */
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

        /**
         * Handles DELETE command: deletes a file and its data.
         * Format: DELETE <filename>
         */
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

        /**
         * Handles LIST command: lists all files on the server.
         * Format: LIST
         */
        private String handleList() {
            String[] files = fsManager.listFiles();

            if (files.length == 0) {
                return "SUCCESS: No files on server.";
            }

            // Build a nicely formatted response with all filenames
            StringBuilder sb = new StringBuilder("SUCCESS: Files on server:\n");
            for (String filename : files) {
                sb.append("  - ").append(filename).append("\n");
            }
            return sb.toString().trim();
        }
    }
}
