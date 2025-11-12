package ca.concordia;

import ca.concordia.server.FileServer;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting File Sharing Server...");

        // Configuration parameters
        int port = 12345;
        String fileSystemName = "filesystem.dat";
        int blockSize = 128;        // BLOCKSIZE: 128 bytes per block
        int maxFiles = 20;          // MAXFILES: Maximum number of files
        int maxBlocks = 100;        // MAXBLOCKS: Maximum number of storage blocks
        int totalSize = maxBlocks * blockSize;

        System.out.println("Configuration:");
        System.out.println("  Port: " + port);
        System.out.println("  Block Size: " + blockSize + " bytes");
        System.out.println("  Max Files: " + maxFiles);
        System.out.println("  Max Blocks: " + maxBlocks);
        System.out.println("  Total Storage: " + totalSize + " bytes");

        // Create and start the file server
        FileServer server = new FileServer(port, fileSystemName, totalSize, blockSize, maxFiles, maxBlocks);
        server.start();
    }
}