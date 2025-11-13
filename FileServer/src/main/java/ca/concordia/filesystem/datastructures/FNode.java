package ca.concordia.filesystem.datastructures;

/**
 * Represents a node in the file block chain.
 * <p>
 * Each FNode acts like a node in a linked list, pointing to an actual data block
 * and the next node in the chain. This allows files to span multiple non-contiguous
 * blocks, similar to how real filesystems work.
 * <p>
 * For example, if a file needs 3 blocks:
 * FNode[0] -> blockIndex=5, nextBlock=1
 * FNode[1] -> blockIndex=12, nextBlock=2
 * FNode[2] -> blockIndex=8, nextBlock=-1 (end of chain)
 */
public class FNode {

    // Points to the actual data block where file content is stored
    // If negative, this FNode is currently unused/available
    private int blockIndex;

    // Index to the next FNode in the chain
    // -1 means this is the last block in the file
    private int nextBlock;

    /**
     * Creates a new FNode with just a block index.
     * The next pointer is initialized to -1 (no next block).
     */
    public FNode(int blockIndex) {
        this.blockIndex = blockIndex;
        this.nextBlock = -1;
    }

    /**
     * Creates a new FNode with both block index and next pointer.
     * Useful when building a chain of blocks.
     */
    public FNode(int blockIndex, int nextBlock) {
        this.blockIndex = blockIndex;
        this.nextBlock = nextBlock;
    }

    public int getBlockIndex() {
        return blockIndex;
    }

    public void setBlockIndex(int blockIndex) {
        this.blockIndex = blockIndex;
    }

    public int getNextBlock() {
        return nextBlock;
    }

    public void setNextBlock(int nextBlock) {
        this.nextBlock = nextBlock;
    }

    /**
     * Checks if this FNode is currently being used by a file.
     * An FNode is considered "used" if it points to a valid block (blockIndex >= 0).
     */
    public boolean isUsed() {
        return blockIndex >= 0;
    }
}
