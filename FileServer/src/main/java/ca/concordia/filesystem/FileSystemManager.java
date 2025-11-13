package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;

import java.io.RandomAccessFile; //allows low level access to a binary file that represents the disk
import java.util.concurrent.locks.ReentrantLock; 

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10; // maximum number of storage blocks on the disk
    private static FileSystemManager instance; //removed Final, static so one copy for entire class. 
    private final RandomAccessFile disk; //virtual disk file. RandomAccessFile let us read/write anywhere in it. 
    private final ReentrantLock globalLock = new ReentrantLock(); 

    private static final int BLOCK_SIZE = 128; // Example block size, size of each block on the disk. 

    private FEntry[] inodeTable; // Array of inodes. Keeps file metadata ( file name, size...)
    private boolean[] freeBlockList; // Bitmap for free blocks, used to keep track of which blocks are free or used. 

    public static FileSystemManager getInstance(String filename, int totalSize)throws Exception {
        // Initialize the file system manager with a file
        if(instance == null) {
            //TODO Initialize the file system
            instance = new FileSystemManager(filename, totalSize); //creates one FileSystemManager instance
        }
            return instance;
        }
        
    private FileSystemManager (String filename, int totalSize) throws Exception{
            disk = new RandomAccessFile(filename,"rw");  //create and open the virtual disk file, read/write mode
        inodeTable = new FEntry[MAXFILES];
        freeBlockList = new boolean[MAXBLOCKS];
        for (int i=0;i<MAXBLOCKS;i++){ //all blocks are free at the beginning
            freeBlockList[i]= true;
        }
        //for debugging purposes,print info
        System.out.println("File system initialized with"+ totalSize+"bytes.");
    }

    public void createFile(String fileName) throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }
    public void deleteFile(String fileName) throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }
    public byte[] readFile(String fileName, int offset, int length) throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }
    public void writeFile(String fileName, int offset, byte[] data) throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }
    public String listFiles() throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }
}
