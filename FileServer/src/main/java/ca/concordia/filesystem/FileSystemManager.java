package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;

import java.io.RandomAccessFile; //allows low level access to a binary file that represents the disk
import java.util.ArrayList;
import java.util.List;
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
                //Error handling for filename length
                if (fileName.length() > 11) {
                    throw new Exception("ERROR: filename too large");
                }
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
                inodeTable[inodeIndex] = new FEntry(fileName, 0, -1);
            }finally{
            globalLock.unlock();
        }
    }

    public void deleteFile(String fileName) throws Exception {
        globalLock.lock();
        try{
            int inodeIndex = findFileByName(fileName); //store the index of the file to be deleted
            if (inodeIndex==-1){ //if file was not found
                throw new Exception("ERROR: file "+ fileName +" does not exist.");
            }

            FEntry file = inodeTable[inodeIndex]; //get the file entry from the inode table
            int currentBlock = file.getFirstBlock(); //get the first block of the file

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
                throw new Exception ("ERROR: file "+fileName+" does not exist.");
            }

            FEntry file = inodeTable[inodeIndex];
            int fileSize = file.getSize();
            int currentBlock = file.getFirstBlock();

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
    public void writeFile(String fileName, byte[] data) throws Exception {
        globalLock.lock();
        try{
            int inodeIndex = findFileByName(fileName);
            if (inodeIndex == -1){
                throw new Exception("ERROR: file "+fileName+" does not exist.");
            }

            //calculate number of blocks needed
            int blockData = BLOCK_SIZE -2; //each block reserves 2 bytes for the next block index so we subtract 2
            int blocksNeeded = (data.length + blockData -1)/blockData; //ceiling division
            if (data.length==0){ //if file is empty, no blocks are needed
                blocksNeeded = 0;
            }

            //check the number of available blocks
            int availableBlocks = 0;
            for(boolean free : freeBlockList){
                if(free)availableBlocks ++; //count free blocks
            }

            //check if the available blocks are enough
            if(blocksNeeded > availableBlocks){
                throw new Exception("ERROR: file too large.");
            }

            FEntry file = inodeTable[inodeIndex]; //get the file entry from the inode table

            //Free previously allocated blocks
            int currentBlock = file.getFirstBlock();
            while(currentBlock !=-1){ //while there are still blocks
                disk.seek(currentBlock * BLOCK_SIZE);
                short nextBlock = disk.readShort(); //get the next block index

                disk.seek(currentBlock * BLOCK_SIZE);
                byte[] zeroBlock = new byte[BLOCK_SIZE];
                disk.write(zeroBlock); //overwrite the block with zeros

                freeBlockList[currentBlock]=true;
                currentBlock = nextBlock;
            }

            //Allocate new blocks and write data
            short firstBlock = -1;
            short previousBlock = -1;
            int offset = 0; //needed to keep track of how much data has been written

            for (int i=0; i<blocksNeeded; i++){ //for each block needed
                short blockNumber = -1;
                for (short j=0; j<MAXBLOCKS;j++){ //find a free block
                    if (freeBlockList[j]){
                        blockNumber = j; //allocate this block
                        freeBlockList[j] = false; //mark allocatedblock as used
                        break;
                    }
                }
                if (i==0){ //if this is the first block, store the block number
                    firstBlock = blockNumber;
                }

                if (previousBlock != -1){ //if this is not the first block, link the previous block to this one
                    disk.seek(previousBlock * BLOCK_SIZE);
                    disk.writeShort(blockNumber);
                }
                disk.seek(blockNumber * BLOCK_SIZE);
                disk.writeShort(-1); //initialize next block pointer to -1

                //Write the actual data to the block
                int length = Math.min(blockData, data.length - offset);
                disk.write(data, offset, length);
                offset += length;

                previousBlock = blockNumber; //upodate previous block ref
            }
            //update inode table
            inodeTable[inodeIndex] = new FEntry(fileName, data.length, firstBlock);

        }finally{
            globalLock.unlock();
        }
    }
    public String [] listFiles() {
        globalLock.lock();
        try{
            //declare a list to hold the filenames
            List<String> files = new ArrayList<>();
            for (FEntry entry: inodeTable){
                if (entry != null){
                    files.add(entry.getFilename()); // add file names to the list
                }
            }
            return files.toArray(new String[0]);

        }finally{
            globalLock.unlock();
        }
    }
}