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
            //Initialize the file system
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

    //Function to find file by name
    private int findFileByName(String inputFileName){ 
        for (int i=0; i<MAXFILES;i++){
            if(inodeTable[i]!=null && inodeTable[i].getFilename().equals(inputFileName)){
                return i; //retuns the index of the file in the inode table
            }
        }
        return -1; //file not found
    }

    public void createFile(String fileName) throws Exception {
        globalLock.lock();
        try {
            //Verify if the file exists.
            for (int i=0; i<MAXFILES; i++){
                if (inodeTable[i]!=null && inodeTable[i].getFilename().equals(fileName)){
                throw new Exception("The File "+ fileName + " already exists.");
                }
            }
            //find free node in node table
            int inodeIndex = -1;
            for (int i=0; i<MAXFILES; i++){
                if (inodeTable[i]==null){ //if no node currently occupies this node
                    inodeIndex=i; //remember index and stop searching
                    break; 
                }
            }
            //if no free nodes were  found , throw exception
            if (inodeIndex == -1){
                throw new Exception("The maximum number of files is reached. No free file entries");
            } 
            //if entry was found , create new entry but with no blocks allocated yet
            inodeTable[inodeIndex] = new FEntry(fileName,(short) 0,(short) -1);
        }finally{
            globalLock.unlock();
        }
    }

    public void deleteFile(String fileName) throws Exception {
        globalLock.lock();
        try{
            int inodeIndex = findFileByName(fileName); //store the index of the file to be deleted
            if (inodeIndex==-1){ //if file was not found
                throw new Exception("The file"+ fileName +"was not found");
            }

            FEntry file = inodeTable[inodeIndex]; //get the file entry from the inode table
            short currentBlock = file.getFirstBlock(); //get the first block of the file

            while (currentBlock != -1){ //while there are still blocks
                disk.seek(currentBlock * BLOCK_SIZE); //jumps to offset currentBlock * BLOCK_SIZE to read the next block index
                short nextBlock = disk.readShort(); //read the next block index

                disk.seek(currentBlock * BLOCK_SIZE); 
                byte[] zeroBlock = new byte [BLOCK_SIZE]; //overwrite the block with zeros
                disk.write(zeroBlock);

                freeBlockList[currentBlock]=true; //add the block to the free block list
                currentBlock = nextBlock; //move to the next block
            }

            inodeTable[inodeIndex] = null; //delete the inode entry at this index
            
            } finally {
                globalLock.unlock();
            }
    }
    public byte[] readFile(String fileName, int offset, int length) throws Exception {
        globalLock.lock();
        try{
            int inodeIndex = findFileByName(fileName);
            if (inodeIndex == -1){
                throw new Exception ("The file"+fileName+"was not found.");
            }

            FEntry file = inodeTable[inodeIndex]; 
            int fileSize = file.getFilesize();
            short currentBlock = file.getFirstBlock();
            
            if(fileSize==0){ //empty file , nothing to read
                return new byte[0];
            }
            //if file is not empty , read its contents
            byte[] contents = new byte[fileSize]; //create a byte array to hold the file contents
            int bytesRead = 0;

            while (currentBlock != -1 && bytesRead < fileSize){ //while there are still blocks and we have not read the entire file
                int toRead = Math.min(BLOCK_SIZE-2, fileSize - bytesRead); //calculate how many bytes to read from this block

                disk.seek(currentBlock * BLOCK_SIZE); 
                short nextBlock = disk.readShort();
                disk.read(contents, bytesRead, toRead); //read the block data into the contents array

                bytesRead +=toRead; //change the number of bytes read
                currentBlock = nextBlock;  //move to the next block
            }
            return contents; //read the file contents

        } finally{
            globalLock.unlock();
        }
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
