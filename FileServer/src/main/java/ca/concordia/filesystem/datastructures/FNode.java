package ca.concordia.filesystem.datastructures;

public class FNode {

    private int blockIndex;  // Data block index (negative if unused)
    private int nextBlock;   // Index to next FNode (-1 if last block)

    public FNode(int blockIndex) {
        this.blockIndex = blockIndex;
        this.nextBlock = -1;
    }

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

    public boolean isUsed() {
        return blockIndex >= 0;
    }
}
