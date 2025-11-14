package ca.concordia.filesystem.datastructures;

/**
 * Represents metadata for a single file in the filesystem.
 * <p>
 * Think of this as the "index card" in a file cabinet - it tells you the file's name,
 * how big it is, and where to find the actual data (via firstBlock pointer).
 * <p>
 * The filename limit of 11 characters is intentional to keep the structure compact,
 * similar to old DOS 8.3 filenames.
 */
public class FEntry {

    // The file's name - limited to 11 characters to match assignment specs
    private String filename;

    // Actual file size in bytes (not the allocated space, but what's actually used)
    // Using int instead of short for better Java compatibility
    private int size;

    // Points to the first FNode in this file's block chain
    // -1 means empty file or no blocks allocated yet
    private int firstBlock;

    /**
     * Creates an empty, unused file entry.
     * This is the default state when the filesystem is initialized.
     */
    public FEntry() {
        this.filename = null;
        this.size = 0;
        this.firstBlock = -1;
    }

    /**
     * Creates a file entry with specific values.
     * Validates that filename doesn't exceed the 11-character limit.
     */
    public FEntry(String filename, int size, int firstBlock) throws IllegalArgumentException {
        // Enforce the 11-character filename limit from the specs
        if (filename != null && filename.length() > 11) {
            throw new IllegalArgumentException("Filename cannot be longer than 11 characters.");
        }
        this.filename = filename;
        this.size = size;
        this.firstBlock = firstBlock;
    }

    // --- Getters and Setters ---

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        // Always validate filename length when setting
        if (filename != null && filename.length() > 11) {
            throw new IllegalArgumentException("Filename cannot be longer than 11 characters.");
        }
        this.filename = filename;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        // Sanity check - file size can't be negative
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

    /**
     * Checks if this file entry is currently in use.
     * An entry is "used" if it has a valid filename.
     */
    public boolean isUsed() {
        return filename != null && !filename.isEmpty();
    }

    /**
     * Resets this entry to unused state.
     * Called when a file is deleted to free up the entry for reuse.
     */
    public void clear() {
        this.filename = null;
        this.size = 0;
        this.firstBlock = -1;
    }
}
