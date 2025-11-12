package ca.concordia.filesystem.datastructures;

public class FEntry {

    private String filename;     // Max 11 characters
    private int size;            // Actual file size (using int instead of short for better Java compatibility)
    private int firstBlock;      // Index to first FNode

    public FEntry() {
        this.filename = null;
        this.size = 0;
        this.firstBlock = -1;
    }

    public FEntry(String filename, int size, int firstBlock) throws IllegalArgumentException {
        // Check filename is max 11 bytes long
        if (filename != null && filename.length() > 11) {
            throw new IllegalArgumentException("Filename cannot be longer than 11 characters.");
        }
        this.filename = filename;
        this.size = size;
        this.firstBlock = firstBlock;
    }

    // Getters and Setters
    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        if (filename != null && filename.length() > 11) {
            throw new IllegalArgumentException("Filename cannot be longer than 11 characters.");
        }
        this.filename = filename;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Filesize cannot be negative.");
        }
        this.size = size;
    }

    public int getFirstBlock() {
        return firstBlock;
    }

    public void setFirstBlock(int firstBlock) {
        this.firstBlock = firstBlock;
    }

    public boolean isUsed() {
        return filename != null && !filename.isEmpty();
    }

    public void clear() {
        this.filename = null;
        this.size = 0;
        this.firstBlock = -1;
    }
}
