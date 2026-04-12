package org.ecocean.servlet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndexPageHelperTest {

    @Test
    void buildSafeProfilePhotoUrl_normalInputs_buildsCorrectUrl() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "wildbook_data_dir", "alice", "photo.jpg");
        assertEquals("/wildbook_data_dir/users/alice/photo.jpg", url);
    }

    @Test
    void buildSafeProfilePhotoUrl_usernameWithDotDot_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "wildbook_data_dir", "../admin", "photo.jpg");
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }

    @Test
    void buildSafeProfilePhotoUrl_usernameWithPlainSlash_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "wildbook_data_dir", "alice/bob", "photo.jpg");
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }

    @Test
    void buildSafeProfilePhotoUrl_filenameWithDotDot_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "wildbook_data_dir", "alice", "../../etc/passwd");
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }

    @Test
    void buildSafeProfilePhotoUrl_filenameWithBackslash_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "wildbook_data_dir", "alice", "evil\\file.jpg");
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }

    @Test
    void buildSafeProfilePhotoUrl_nullUsername_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "wildbook_data_dir", null, "photo.jpg");
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }

    @Test
    void buildSafeProfilePhotoUrl_nullFilename_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "wildbook_data_dir", "alice", null);
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }

    @Test
    void buildSafeProfilePhotoUrl_emptyFilename_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "wildbook_data_dir", "alice", "");
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }

    @Test
    void buildSafeProfilePhotoUrl_nullDataDir_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            null, "alice", "photo.jpg");
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }

    @Test
    void buildSafeProfilePhotoUrl_emptyDataDir_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "", "alice", "photo.jpg");
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }

    @Test
    void buildSafeProfilePhotoUrl_emptyUsername_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "wildbook_data_dir", "", "photo.jpg");
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }

    @Test
    void buildSafeProfilePhotoUrl_dataDirWithTraversal_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "../data", "alice", "photo.jpg");
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }

    @Test
    void buildSafeProfilePhotoUrl_usernameWithDoubleQuote_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "wildbook_data_dir", "evil\"user", "photo.jpg");
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }
}
