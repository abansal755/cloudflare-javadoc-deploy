package in.co.akshitbansal.cloudflare.javadoc.deploy.model.cloudflare;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

@Data
public class BundleFile implements Comparable<BundleFile>{

    private String absolutePath;
    private String relativePath;

    private String hash;
    private long sizeInBytes;
    private String contentType;

    public BundleFile(String absolutePath, String relativePath) {
        this.absolutePath = absolutePath;
        this.relativePath = relativePath;
    }

    @Override
    public int compareTo(@NotNull BundleFile o) {
        return Comparator
                .comparingLong(BundleFile::getSizeInBytes).reversed()
                .thenComparing(BundleFile::getRelativePath, Comparator.nullsFirst(String::compareTo))
                .thenComparing(BundleFile::getAbsolutePath, Comparator.nullsFirst(String::compareTo))
                .thenComparing(BundleFile::getHash, Comparator.nullsFirst(String::compareTo))
                .compare(this, o);
    }
}
