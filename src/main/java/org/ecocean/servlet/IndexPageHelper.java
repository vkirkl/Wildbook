package org.ecocean.servlet;

/**
 * Stateless utility methods for index.jsp rendering logic.
 * Extracted here so they can be unit-tested without a running servlet container.
 */
public class IndexPageHelper {

    public static final String DEFAULT_PHOTO_URL =
        "images/user-profile-white-transparent.png";

    private IndexPageHelper() {}

    /**
     * Constructs a profile photo URL from its components, returning the default
     * placeholder if any component is null, empty, or contains path-separator
     * characters that could enable directory traversal.
     *
     * @param dataDirectoryName  value from CommonConfiguration.getDataDirectoryName()
     * @param username           the user's login name
     * @param filename           the photo filename from UserImage
     * @return safe URL string
     */
    public static String buildSafeProfilePhotoUrl(
            String dataDirectoryName, String username, String filename) {
        if (dataDirectoryName == null || dataDirectoryName.isEmpty()
                || username == null || username.isEmpty()
                || filename == null || filename.isEmpty()) {
            return DEFAULT_PHOTO_URL;
        }
        if (containsUnsafePathChars(dataDirectoryName)
                || containsUnsafePathChars(username)
                || containsUnsafePathChars(filename)) {
            return DEFAULT_PHOTO_URL;
        }
        return "/" + dataDirectoryName + "/users/" + username + "/" + filename;
    }

    private static boolean containsUnsafePathChars(String s) {
        return s.contains("/") || s.contains("\\") || s.contains("..");
    }
}
