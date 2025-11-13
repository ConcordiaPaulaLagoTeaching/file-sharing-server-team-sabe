package ca.concordia;

import ca.concordia.server.FileServer;

/**
 * Entry point for the File Sharing Server application.
 * <p>
 * This sets up the server configuration and starts it listening for client connections.
 * The server will run until manually stopped (Ctrl+C).
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Starting File Sharing Server...");

        // ===== Server Configuration =====
        // These parameters control the filesystem behavior and can be adjusted
        // to test different scenarios or meet different requirements

        int port = 12345;                       // Network port to listen on
        String fileSystemName = "filesystem.dat"; // Where to persist filesystem on disk
        int blockSize = 128;                     // BLOCKSIZE: Size of each block in bytes
        int maxFiles = 20;                       // MAXFILES: Maximum number of files allowed
        int maxBlocks = 100;                     // MAXBLOCKS: Maximum number of storage blocks
        int totalSize = maxBlocks * blockSize;   // Total storage capacity

        // Display configuration to help with debugging/testing
        System.out.println("Configuration:");
        System.out.println("  Port: " + port);
        System.out.println("  Block Size: " + blockSize + " bytes");
        System.out.println("  Max Files: " + maxFiles);
        System.out.println("  Max Blocks: " + maxBlocks);
        System.out.println("  Total Storage: " + totalSize + " bytes");

        // Create and start the file server
        // This call blocks forever - the server runs until killed
        FileServer server = new FileServer(port, fileSystemName, totalSize, blockSize, maxFiles, maxBlocks);
        server.start();
    }
}