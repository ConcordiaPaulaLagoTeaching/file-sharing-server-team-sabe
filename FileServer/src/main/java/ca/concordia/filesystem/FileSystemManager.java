package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

import java.io.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileSystemManager {

    private final int MAXFILES;
    private final int MAXBLOCKS;
    private final int BLOCKSIZE;
    private static FileSystemManager instance = null;
    private final String diskFilename;

    // Synchronization: ReadWriteLock allows multiple readers or single writer
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    private FEntry[] fileEntries;  // Array of file entries (metadata)
    private FNode[] fileNodes;     // Array of file nodes (block chain)
    private byte[][] dataBlocks;   // Actual data storage

    private final int METADATA_BLOCKS; // Blocks used for metadata

    public FileSystemManager(String filename, int totalSize, int blockSize, int maxFiles, int maxBlocks) {
        this.diskFilename = filename;
        this.BLOCKSIZE = blockSize;
        this.MAXFILES = maxFiles;
        this.MAXBLOCKS = maxBlocks;

        // Calculate metadata blocks needed
        this.METADATA_BLOCKS = calculateMetadataBlocks();

        // Initialize data structures
        this.fileEntries = new FEntry[MAXFILES];
        this.fileNodes = new FNode[MAXBLOCKS];
        this.dataBlocks = new byte[MAXBLOCKS][BLOCKSIZE];

        // Initialize arrays
        for (int i = 0; i < MAXFILES; i++) {
            fileEntries[i] = new FEntry();
        }

        for (int i = 0; i < MAXBLOCKS; i++) {
            fileNodes[i] = new FNode(-1); // -1 means unused
        }

        // Try to load existing filesystem, or create new one
        try {
            loadFileSystem();
        } catch (IOException e) {
            System.out.println("Creating new file system...");
            initializeFileSystem();
        }
    }

    private int calculateMetadataBlocks() {
        // Simple calculation: reserve enough blocks for metadata
        // In real implementation, this would be more sophisticated
        return Math.max(1, (MAXFILES * 20 + MAXBLOCKS * 8) / BLOCKSIZE + 1);
    }

    private void initializeFileSystem() {
        // Initialize empty filesystem
        for (int i = 0; i < MAXFILES; i++) {
            fileEntries[i].clear();
        }
        for (int i = 0; i < MAXBLOCKS; i++) {
            fileNodes[i].setBlockIndex(-1);
            fileNodes[i].setNextBlock(-1);
        }
        try {
            saveFileSystem();
        } catch (IOException e) {
            System.err.println("Error initializing file system: " + e.getMessage());
        }
    }

    private void loadFileSystem() throws IOException {
        File file = new File(diskFilename);
        if (!file.exists()) {
            throw new IOException("File system file does not exist");
        }

        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            // Load file entries
            for (int i = 0; i < MAXFILES; i++) {
                String filename = dis.readUTF();
                int size = dis.readInt();
                int firstBlock = dis.readInt();
                if (filename.isEmpty()) {
                    fileEntries[i].clear();
                } else {
                    fileEntries[i].setFilename(filename);
                    fileEntries[i].setSize(size);
                    fileEntries[i].setFirstBlock(firstBlock);
                }
            }

            // Load file nodes
            for (int i = 0; i < MAXBLOCKS; i++) {
                int blockIndex = dis.readInt();
                int nextBlock = dis.readInt();
                fileNodes[i].setBlockIndex(blockIndex);
                fileNodes[i].setNextBlock(nextBlock);
            }

            // Load data blocks
            for (int i = 0; i < MAXBLOCKS; i++) {
                dis.readFully(dataBlocks[i]);
            }
        }
    }

    private void saveFileSystem() throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(diskFilename))) {
            // Save file entries
            for (int i = 0; i < MAXFILES; i++) {
                dos.writeUTF(fileEntries[i].getFilename() == null ? "" : fileEntries[i].getFilename());
                dos.writeInt(fileEntries[i].getSize());
                dos.writeInt(fileEntries[i].getFirstBlock());
            }

            // Save file nodes
            for (int i = 0; i < MAXBLOCKS; i++) {
                dos.writeInt(fileNodes[i].getBlockIndex());
                dos.writeInt(fileNodes[i].getNextBlock());
            }

            // Save data blocks
            for (int i = 0; i < MAXBLOCKS; i++) {
                dos.write(dataBlocks[i]);
            }
        }
    }

    /**
     * Create a new empty file
     * @param filename Name of the file (max 11 characters)
     * @throws Exception if filename too large or no space
     */
    public void createFile(String filename) throws Exception {
        if (filename.length() > 11) {
            throw new Exception("ERROR: filename too large");
        }

        rwLock.writeLock().lock();
        try {
            // Check if file already exists
            for (int i = 0; i < MAXFILES; i++) {
                if (fileEntries[i].isUsed() && fileEntries[i].getFilename().equals(filename)) {
                    throw new Exception("ERROR: file " + filename + " already exists");
                }
            }

            // Find first available FEntry
            int entryIndex = -1;
            for (int i = 0; i < MAXFILES; i++) {
                if (!fileEntries[i].isUsed()) {
                    entryIndex = i;
                    break;
                }
            }

            if (entryIndex == -1) {
                throw new Exception("ERROR: no space for new file");
            }

            // Create empty file
            fileEntries[entryIndex].setFilename(filename);
            fileEntries[entryIndex].setSize(0);
            fileEntries[entryIndex].setFirstBlock(-1);

            saveFileSystem();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Delete a file and free its blocks
     * @param filename Name of the file to delete
     * @throws Exception if file doesn't exist
     */
    public void deleteFile(String filename) throws Exception {
        rwLock.writeLock().lock();
        try {
            // Find the file
            int entryIndex = findFileEntry(filename);
            if (entryIndex == -1) {
                throw new Exception("ERROR: file " + filename + " does not exist");
            }

            FEntry entry = fileEntries[entryIndex];
            int blockIndex = entry.getFirstBlock();

            // Free all blocks and overwrite with zeroes
            while (blockIndex != -1) {
                FNode node = fileNodes[blockIndex];
                int nextBlock = node.getNextBlock();

                // Overwrite data with zeroes
                for (int i = 0; i < BLOCKSIZE; i++) {
                    dataBlocks[node.getBlockIndex()][i] = 0;
                }

                // Free the node
                node.setBlockIndex(-1);
                node.setNextBlock(-1);

                blockIndex = nextBlock;
            }

            // Clear the entry
            entry.clear();

            saveFileSystem();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Write content to a file (overwrites existing content)
     * @param filename Name of the file
     * @param contents Content to write
     * @throws Exception if file doesn't exist or file too large
     */
    public void writeFile(String filename, byte[] contents) throws Exception {
        rwLock.writeLock().lock();
        try {
            // Find the file
            int entryIndex = findFileEntry(filename);
            if (entryIndex == -1) {
                throw new Exception("ERROR: file " + filename + " does not exist");
            }

            FEntry entry = fileEntries[entryIndex];

            // Calculate blocks needed
            int blocksNeeded = (contents.length + BLOCKSIZE - 1) / BLOCKSIZE;

            // Check if we have enough free blocks
            int freeBlocks = countFreeBlocks();
            int currentBlocks = countFileBlocks(entry.getFirstBlock());

            if (blocksNeeded > freeBlocks + currentBlocks) {
                throw new Exception("ERROR: file too large");
            }

            // Free old blocks first
            int oldFirstBlock = entry.getFirstBlock();
            freeBlockChain(oldFirstBlock);

            // Allocate new blocks and write data
            if (contents.length > 0) {
                int firstBlockIndex = -1;
                int previousNodeIndex = -1;

                for (int i = 0; i < blocksNeeded; i++) {
                    // Find free data block
                    int dataBlockIndex = findFreeDataBlock();
                    if (dataBlockIndex == -1) {
                        throw new Exception("ERROR: file too large");
                    }

                    // Find free node
                    int nodeIndex = findFreeNode();
                    if (nodeIndex == -1) {
                        throw new Exception("ERROR: file too large");
                    }

                    // Write data to block
                    int start = i * BLOCKSIZE;
                    int end = Math.min(start + BLOCKSIZE, contents.length);
                    System.arraycopy(contents, start, dataBlocks[dataBlockIndex], 0, end - start);

                    // Fill rest with zeroes if needed
                    for (int j = end - start; j < BLOCKSIZE; j++) {
                        dataBlocks[dataBlockIndex][j] = 0;
                    }

                    // Setup node
                    fileNodes[nodeIndex].setBlockIndex(dataBlockIndex);
                    fileNodes[nodeIndex].setNextBlock(-1);

                    if (firstBlockIndex == -1) {
                        firstBlockIndex = nodeIndex;
                    }

                    if (previousNodeIndex != -1) {
                        fileNodes[previousNodeIndex].setNextBlock(nodeIndex);
                    }

                    previousNodeIndex = nodeIndex;
                }

                entry.setFirstBlock(firstBlockIndex);
            } else {
                entry.setFirstBlock(-1);
            }

            entry.setSize(contents.length);
            saveFileSystem();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Read file contents
     * @param filename Name of the file to read
     * @return File contents as byte array
     * @throws Exception if file doesn't exist
     */
    public byte[] readFile(String filename) throws Exception {
        rwLock.readLock().lock();
        try {
            // Find the file
            int entryIndex = findFileEntry(filename);
            if (entryIndex == -1) {
                throw new Exception("ERROR: file " + filename + " does not exist");
            }

            FEntry entry = fileEntries[entryIndex];
            int fileSize = entry.getSize();

            if (fileSize == 0) {
                return new byte[0];
            }

            byte[] contents = new byte[fileSize];
            int blockIndex = entry.getFirstBlock();
            int offset = 0;

            while (blockIndex != -1 && offset < fileSize) {
                FNode node = fileNodes[blockIndex];
                int dataBlockIndex = node.getBlockIndex();

                int bytesToCopy = Math.min(BLOCKSIZE, fileSize - offset);
                System.arraycopy(dataBlocks[dataBlockIndex], 0, contents, offset, bytesToCopy);

                offset += bytesToCopy;
                blockIndex = node.getNextBlock();
            }

            return contents;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * List all files in the filesystem
     * @return Array of filenames
     */
    public String[] listFiles() {
        rwLock.readLock().lock();
        try {
            // Count files
            int fileCount = 0;
            for (int i = 0; i < MAXFILES; i++) {
                if (fileEntries[i].isUsed()) {
                    fileCount++;
                }
            }

            String[] filenames = new String[fileCount];
            int index = 0;
            for (int i = 0; i < MAXFILES; i++) {
                if (fileEntries[i].isUsed()) {
                    filenames[index++] = fileEntries[i].getFilename();
                }
            }

            return filenames;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // Helper methods

    private int findFileEntry(String filename) {
        for (int i = 0; i < MAXFILES; i++) {
            if (fileEntries[i].isUsed() && fileEntries[i].getFilename().equals(filename)) {
                return i;
            }
        }
        return -1;
    }

    private int findFreeNode() {
        for (int i = 0; i < MAXBLOCKS; i++) {
            if (!fileNodes[i].isUsed()) {
                return i;
            }
        }
        return -1;
    }

    private int findFreeDataBlock() {
        // Create a set of used data blocks
        boolean[] used = new boolean[MAXBLOCKS];
        for (int i = 0; i < MAXBLOCKS; i++) {
            if (fileNodes[i].isUsed()) {
                used[fileNodes[i].getBlockIndex()] = true;
            }
        }

        // Find first unused block
        for (int i = 0; i < MAXBLOCKS; i++) {
            if (!used[i]) {
                return i;
            }
        }
        return -1;
    }

    private int countFreeBlocks() {
        boolean[] used = new boolean[MAXBLOCKS];
        for (int i = 0; i < MAXBLOCKS; i++) {
            if (fileNodes[i].isUsed()) {
                used[fileNodes[i].getBlockIndex()] = true;
            }
        }

        int count = 0;
        for (int i = 0; i < MAXBLOCKS; i++) {
            if (!used[i]) {
                count++;
            }
        }
        return count;
    }

    private int countFileBlocks(int firstBlock) {
        int count = 0;
        int blockIndex = firstBlock;
        while (blockIndex != -1) {
            count++;
            blockIndex = fileNodes[blockIndex].getNextBlock();
        }
        return count;
    }

    private void freeBlockChain(int firstBlock) {
        int blockIndex = firstBlock;
        while (blockIndex != -1) {
            FNode node = fileNodes[blockIndex];
            int nextBlock = node.getNextBlock();

            // Overwrite data with zeroes
            int dataBlockIndex = node.getBlockIndex();
            if (dataBlockIndex >= 0) {
                for (int i = 0; i < BLOCKSIZE; i++) {
                    dataBlocks[dataBlockIndex][i] = 0;
                }
            }

            // Free the node
            node.setBlockIndex(-1);
            node.setNextBlock(-1);

            blockIndex = nextBlock;
        }
    }
}
