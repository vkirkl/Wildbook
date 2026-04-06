package org.ecocean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import org.apache.commons.io.IOUtils;

public class JsonProperties extends Properties {
    private static final Logger logger = LogManager.getLogger(JsonProperties.class);
    private static final String propertiesDir = "WEB-INF/classes/bundles";
    private static final String jsonLinkPrefix = "@";

    private String fname;
    private String fullPath;
    private JSONObject json;

    public JsonProperties(String fname) {
        try {
            this.setFname(fname);
            this.loadFullPath();
            this.setJson(fromFile(this.getFullPath()));
        } catch (Exception e) {
            logger.error("Failed to initialize JsonProperties for {}", fname, e);
        }
    }

    public JSONObject getJson() {
        return json;
    }

    public void setJson(JSONObject json) {
        this.json = json;
    }

    public String getFname() {
        return fname;
    }

    public void setFname(String fname) {
        this.fname = fname;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    public Object get(String periodSeparatedKeys) {
        try {
            String[] keys = periodSeparatedKeys.split("\\.");
            return getRecursive(keys, this.getJson());
        } catch (Exception e) {
            logger.error("JsonProperties.get hit an exception on key {} searching json {}",
                periodSeparatedKeys, fname, e);
            return null;
        }
    }

    public Object getRecursive(String[] keys, JSONObject currentLevel) {
        String key = keys[0];
        String linkDestination = getLinkDestination(key, currentLevel);
        boolean followLink = (linkDestination != null);

        // base case
        if (keys.length == 1) {
            return (followLink ? get(linkDestination) : currentLevel.get(key));
        }
        // multiline ternary : nextLevel depends on whether we are following a link at this level
        JSONObject nextLevel = followLink ? (JSONObject) this.get(
            linkDestination) : (JSONObject) currentLevel.get(key);
        String[] nextKeys = Arrays.copyOfRange(keys, 1, keys.length);
        return getRecursive(nextKeys, nextLevel);
    }

    // if jobj.key = <a link to elsewhere in this file>, this returns the link destination. else null
    public String getLinkDestination(String key, JSONObject jobj) {
        try {
            String val = jobj.getString(key);
            if (val.startsWith(jsonLinkPrefix)) return val.substring(1);
        } catch (Exception e) {
            logger.trace("Key {} not found or not a string link in JSON", key);
        }
        return null;
    }

    // sets full path to the file defined by this.fname, looking first in overrideDir then the default propertiesDir
    public void loadFullPath()
    throws FileNotFoundException {
        String overridePath = overrideFilepath();
        String defaultPath = defaultFilepath();
        if (Util.fileExists(overridePath)) {
            logger.debug("Loading {} from override path {}", fname, overridePath);
            this.setFullPath(overridePath);
        } else if (Util.fileExists(defaultPath)) {
            logger.debug("Loading {} from default path {}", fname, defaultPath);
            this.setFullPath(defaultPath);
        } else {
            throw new FileNotFoundException("Could not find file " + fname + " in default (" +
                    defaultPath + ") or override (" + overridePath + ") directories");
        }
    }

    public static JSONObject fromFile(String fullPath) {
        JSONObject json = null;

        try {
            InputStream is = new FileInputStream(fullPath);
            String jsonTxt = IOUtils.toString(is, StandardCharsets.UTF_8);
            json = new JSONObject(jsonTxt);
        } catch (Exception e) {
            logger.error("Failed to load JSON from {}", fullPath, e);
        }
        return json;
    }

    private String overrideFilepath() {
        return CommonConfiguration.getWildbookDataDir()
            + File.separator + "WEB-INF" + File.separator + "classes"
            + File.separator + "bundles" + File.separator + getFname();
    }

    private String defaultFilepath() {
        return propertiesDir + File.separator + getFname();
    }
}
