package in.co.akshitbansal.cloudflare.javadoc.deploy;

import org.apache.maven.artifact.versioning.ComparableVersion;

import java.util.Comparator;

public class VersionComparator implements Comparator<String> {

    @Override
    public int compare(String o1, String o2) {
        // Descending order
        return new ComparableVersion(o2).compareTo(new ComparableVersion(o1));
    }
}
