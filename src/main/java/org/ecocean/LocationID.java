package org.ecocean;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdProperties;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import javax.servlet.http.HttpServletRequest;

public class LocationID {
    private static final Logger logger = LogManager.getLogger(LocationID.class);
    // the JSON representation of /bundles/locationID.json
    private static ConcurrentHashMap<String, JSONObject> jsonMaps = new ConcurrentHashMap<String,
        JSONObject>();

    public static ConcurrentHashMap<String, JSONObject> getJSONMaps() { return jsonMaps; }

    /*
     * Return the JSON representation of /bundles/locationID.json
     */
    public static JSONObject getLocationIDStructure() {
        if (jsonMaps.get("default") == null) loadJSONData(null);
        return jsonMaps.get("default");
    }

    /*
     * Return the JSON representation of /bundles/locationID.json
     */
    public static JSONObject getLocationIDStructure(String qualifier) {
        if (qualifier == null) return getLocationIDStructure();
        if (jsonMaps.get(qualifier) == null) loadJSONData(qualifier);
        return jsonMaps.get(qualifier);
    }

    /*
     * Return the JSON representation of /bundles/locationID.json but check the request for the
     * user and then try to send the appropriate org argument if it exists.
     */
    public static JSONObject getLocationIDStructure(HttpServletRequest request) {
        String qualifier = null;
        Shepherd myShepherd = new Shepherd(request);

        myShepherd.setAction("LocationID.java");
        myShepherd.beginDBTransaction();
        qualifier = ShepherdProperties.getOverwriteStringForUser(request, myShepherd);
        if (qualifier == null) { qualifier = "default"; } else {
            qualifier = qualifier.replaceAll(".properties", "");
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        // System.out.println("qualifier for locationID JSON: "+qualifier);
        if (jsonMaps.get(qualifier) == null) loadJSONData(qualifier);
        return jsonMaps.get(qualifier);
    }

    /*
     * Force a reload of /bundles/locationID.json
     */
    public static void reloadJSON(String filename) {
        jsonMaps = new ConcurrentHashMap<String, JSONObject>();
        loadJSONData(filename);
    }

    private static void loadJSONData(String qualifier) {
        InputStream is = null;
        String filename = "locationID.json";

        if (qualifier != null) {
            filename = "locationID_" + qualifier + ".json";
        }
        // Look for the file in the configured data directory override
        File configFile = new File(
            CommonConfiguration.getWildbookDataDir() + "/WEB-INF/classes/bundles/" + filename);
        if (configFile.exists()) {
            logger.debug("Loading {} from override path {}", filename, configFile.getAbsolutePath());
            try {
                is = new FileInputStream(configFile);
            } catch (Exception e) {
                logger.error("Failed to open locationID override file {}",
                    configFile.getAbsolutePath(), e);
            }
        }
        // Fall back to the webapp classpath
        if (is == null) {
            logger.debug("Override not found for {}, falling back to classpath", filename);
            is = LocationID.class.getResourceAsStream("/bundles/" + filename);
        }
        // Last resort: load the default locationID.json from classpath
        if (is == null) {
            logger.warn("Qualified file {} not on classpath, loading default locationID.json",
                filename);
            filename = "locationID.json";
            is = LocationID.class.getResourceAsStream("/bundles/" + filename);
        }
        if (is == null) {
            logger.error("Could not locate locationID resource for qualifier '{}' "
                + "from override path or classpath — location data will not load", qualifier);
            return;
        }
        JSONTokener tokener = new JSONTokener(is);
        String key = (qualifier != null) ? qualifier : "default";
        jsonMaps.put(key, new JSONObject(tokener));
    }

    public static String findParameterInLocationIdTreeDefaultUpTreeNodes(String parameterName,
        JSONObject tree, String nodeId, String parentParameter) {
        if ((nodeId == null) || (tree == null))
            return null; // handle blech data
        String parameter = tree.optString(parameterName, null);
        if (parameter != null)
            parentParameter = parameter; // basically, parentParameter is the best we have at this point
        if (nodeId.equals(tree.optString("id", null)))
            // we hit target node — return best found; keep trying if we can
            return parentParameter;
        JSONArray kids = tree.optJSONArray("locationID");
        if ((kids == null) || (kids.length() < 1))
            return null; // no more depth here
        for (int i = 0; i < kids.length(); i++) {
            JSONObject subTree = kids.optJSONObject(i);
            if (subTree == null)
                continue; // ith element had non-json child; ignore
            String kidParameter = findParameterInLocationIdTreeDefaultUpTreeNodes(parameterName,
                subTree, nodeId, parentParameter);
            if (kidParameter != null)
                return kidParameter; // recursion ftw!
        }
        // got nothing from any of the kid-trees — nodeId not found
        return null;
    }

    public static String findPrefix(JSONObject tree, String nodeId, String parentPrefix) {
        return findParameterInLocationIdTreeDefaultUpTreeNodes("prefix", tree, nodeId,
                parentPrefix);
    }

    public static String findPrefixPadding(JSONObject tree, String nodeId, String parentPadding) {
        return findParameterInLocationIdTreeDefaultUpTreeNodes("prefixDigitPadding", tree, nodeId,
                parentPadding);
    }

    public static String findGeospatialParameterInIdTree(String parameterName, String nodeId,
        JSONObject tree) {
        if ((nodeId == null) || (tree == null)) {
            return null;
        }
        JSONObject geospatialObj = tree.optJSONObject("geospatialInfo");
        if (geospatialObj != null) {
            String returnVal = geospatialObj.optString(parameterName, null);
            if (nodeId.equals(tree.optString("id", null)) && returnVal != null) {
                return returnVal;
            }
        }
        JSONArray kids = tree.optJSONArray("locationID");
        if ((kids == null) || (kids.length() < 1)) {
            return null;
        } else {
            for (int i = 0; i < kids.length(); i++) {
                JSONObject subTree = kids.optJSONObject(i);
                if (subTree == null) {
                    continue;
                }
                String kidReturnVal = findGeospatialParameterInIdTree(parameterName, nodeId,
                    subTree);
                if (kidReturnVal != null) {
                    return kidReturnVal;
                }
            }
        }
        return null;
    }

    public static String getLatitude(String locationId, JSONObject tree) {
        return findGeospatialParameterInIdTree("lat", locationId, tree);
    }

    public static String getLongitude(String locationId, JSONObject tree) {
        return findGeospatialParameterInIdTree("long", locationId, tree);
    }

    private static JSONObject recurseToFindID(String id, JSONObject jsonobj) {
        // if this is the right object, return it
        try {
            if (jsonobj.getString("id") != null && jsonobj.getString("id").equals(id)) {
                return jsonobj;
            }
        } catch (JSONException e) {}
        // otherwise iterate through its locationID array
        try {
            if (jsonobj.getJSONArray("locationID") != null) {
                JSONArray locs = jsonobj.getJSONArray("locationID");
                // System.out.println("Iterating locationID array for: "+jsonobj.getString("name"));
                int numLocs = locs.length();
                for (int i = 0; i < numLocs; i++) {
                    JSONObject loc = locs.getJSONObject(i);
                    JSONObject j = recurseToFindID(id, loc);
                    if (j != null) return j;
                }
            }
        } catch (JSONException e) {}
        return null;
    }

    /*
     * Return the "name" attribute from JSON for a given "id" in /bundles/locationID.json
     */
    public static String getNameForLocationID(String locationID, String qualifier) {
        JSONObject j = recurseToFindID(locationID, getLocationIDStructure(qualifier));

        if (j != null) {
            try {
                return j.getString("name");
            } catch (JSONException e) {}
        }
        return null;
    }

    public static boolean isValidLocationID(String locationID) {
        return isValidLocationID(locationID, null);
    }

    public static boolean isValidLocationID(String locationID, String qualifier) {
        if (Util.stringIsEmptyOrNull(locationID)) return false;
        JSONObject j = recurseToFindID(locationID, getLocationIDStructure(qualifier));
        return (j != null);
    }

    public static String getPrefixForLocationID(String locationID, String qualifier) { // now a wrapper method
        if (locationID == null) return ""; // "" here for improved cosmetics on front end?
        String locPrefix = "";
        try {
            if (findPrefix(getLocationIDStructure(qualifier), locationID, null) != null) {
                locPrefix = findPrefix(getLocationIDStructure(qualifier), locationID, null);
            }
        } catch (Exception ex) {
            logger.error("Failed to get prefix for locationID {}", locationID, ex);
        }
        return locPrefix;
    }

    public static int getPrefixDigitPaddingForLocationID(String locationID, String qualifier) { // now a wrapper method
        if (locationID == null) return 3;
        int digitPadding = 3;
        if (findPrefixPadding(getLocationIDStructure(qualifier), locationID, null) != null) {
            try {
                digitPadding = Integer.parseInt(findPrefixPadding(getLocationIDStructure(qualifier),
                    locationID, null));
            } catch (Exception ex) {
                logger.error("Failed to get prefix digit padding for locationID {}", locationID, ex);
            }
        }
        return digitPadding;
    }

    /*
     * Return a List of Strings of the "id" attributes of the parent locationID and the IDs of all of its children
     */
    public static List<String> getIDForParentAndChildren(String locationID, String qualifier) {
        ArrayList<String> al = new ArrayList<String>();

        return getIDForParentAndChildren(locationID, al, qualifier);
    }

    /*
     * Return a List of Strings of the "id" attributes of the parent locationID
     * and the IDs of all of its children in the order traversed.
     */
    public static List<String> getIDForParentAndChildren(String locationID, ArrayList<String> al,
        String qualifier) {
        JSONObject j = recurseToFindID(locationID, getLocationIDStructure(qualifier));

        if (j != null) {
            try {
                recurseToFindIDStrings(j, al);
            } catch (JSONException e) {}
        }
        return al;
    }

    /*
     * Starting with a childID, get the IDs of its root parent all the way down to the child ID
     * @childLocationID - dig for a child with this @id
     * @qualifier to use in the digging (e.g., to define user or org value)
     * @return a List of Strings of the lineage of the child ID, starting with its highest parent down to the ID itself.
     */
    public static List<String> getIDForChildAndParents(String childLocationIDToFind,
        String qualifier) {
        ArrayList<String> al = new ArrayList<String>();
        JSONObject jsonobj = getLocationIDStructure(qualifier);

        findPath(jsonobj, childLocationIDToFind, al);
        return al;
    }

    private static void findPath(JSONObject jsonobj, String childLocationIDToFind,
        ArrayList<String> al) {
        try {
            if (jsonobj.getString("id").equals(childLocationIDToFind)) { return; }
        } catch (JSONException e) {}
        // otherwise iterate through its locationID array
        try {
            if (jsonobj.getJSONArray("locationID") != null) {
                JSONArray locs = jsonobj.getJSONArray("locationID");
                // System.out.println("Iterating locationID array for: "+jsonobj.getString("name"));
                int numLocs = locs.length();
                for (int i = 0; i < numLocs; i++) {
                    JSONObject loc = locs.getJSONObject(i);
                    JSONObject id = recurseToFindID(childLocationIDToFind, loc);
                    if (id != null) {
                        al.add(loc.getString("id"));
                        if (loc.getString("id").equals(childLocationIDToFind)) return;
                        findPath(loc, childLocationIDToFind, al);
                    }
                }
            }
        } catch (JSONException e) {}
    }

    private static String getIDIfContainsChildID(JSONObject jsonobj, String childID,
        String qualifier) {
        List<String> list = getIDForParentAndChildren(childID, new ArrayList<String>(), qualifier);

        try {
            if (list != null && list.contains(childID)) { return jsonobj.getString("id"); }
        } catch (JSONException jsone) {}
        return null;
    }

    private static void recurseToFindIDStrings(JSONObject jsonobj, ArrayList<String> al) {
        // if this is the right object, return it
        try {
            if (!al.contains(jsonobj.getString("id"))) al.add(jsonobj.getString("id"));
        } catch (JSONException e) {}
        // otherwise iterate through its locationID array
        try {
            if (jsonobj.getJSONArray("locationID") != null) {
                JSONArray locs = jsonobj.getJSONArray("locationID");
                // System.out.println("Iterating locationID array for: "+jsonobj.getString("name"));
                int numLocs = locs.length();
                for (int i = 0; i < numLocs; i++) {
                    JSONObject loc = locs.getJSONObject(i);
                    recurseToFindIDStrings(loc, al);
                }
            }
        } catch (JSONException e) {}
    }

    /*
     * Return an HTML selector of hierarchical locationIDs with indenting
     */
    public static String getHTMLSelector(boolean multiselect, List<String> selectedIDs,
        String qualifier, String htmlID, String htmlName, String htmlClass) {
        String multiselector = "";

        if (multiselect) multiselector = " multiple=\"multiple\"";
        StringBuffer selector = new StringBuffer("<select style=\"resize:both;\" name=\"" +
            htmlName + "\" id=\"" + htmlID + "\" class=\"" + htmlClass + "\" " + multiselector +
            ">\n\r<option value=\"\"></option>\n\r");

        createSelectorOptions(getLocationIDStructure(qualifier), selector, 0, selectedIDs);

        selector.append("</select>\n\r");
        return selector.toString();
    }

    public static String getHTMLSelector(boolean multiselect, String selectedID, String qualifier,
        String htmlID, String htmlName, String htmlClass) {
        ArrayList<String> locationIDs = new ArrayList<String>();

        locationIDs.add(selectedID);
        return getHTMLSelector(multiselect, locationIDs, qualifier, htmlID, htmlName, htmlClass);
    }

    private static void createSelectorOptions(JSONObject jsonobj, StringBuffer selector,
        int nestingLevel, List<String> selectedIDs) {
        int localNestingLevel = nestingLevel;
        String selected = "";
        String spacing = "";

        for (int i = 0; i < localNestingLevel; i++) { spacing += "&nbsp;&nbsp;&nbsp;"; }
        // see if we can add this item to the list
        try {
            if (selectedIDs != null && selectedIDs.contains(jsonobj.getString("id")))
                selected = " selected=\"selected\"";
            selector.append("<option value=\"" + jsonobj.getString("id") + "\" " + selected + ">" +
                spacing + jsonobj.getString("name") + "</option>\n\r");
            localNestingLevel++;
        } catch (JSONException e) {}
        // iterate locationID array
        try {
            JSONArray locs = jsonobj.getJSONArray("locationID");
            int numLocs = locs.length();
            for (int i = 0; i < numLocs; i++) {
                JSONObject loc = locs.getJSONObject(i);
                createSelectorOptions(loc, selector, localNestingLevel, selectedIDs);
            }
        } catch (JSONException e) {}
    }

    public static String getBootstrapMenu(String qualifier, String urlPrefix) {
        if (urlPrefix == null) urlPrefix = "./"; // probably not what you want
        JSONObject locJson = getLocationIDStructure(qualifier);
        String menu = "<ul class=\"dropdown-menu location-id-nested\">";
        menu += getBootstrapList(locJson, urlPrefix, 0);
        menu += "</ul>";
        return menu;
    }

    // for future reference, when feeling brave:
    // https://stackoverflow.com/questions/18023493/bootstrap-dropdown-sub-menu-missing
    public static String getBootstrapList(final JSONObject locJson, final String urlPrefix,
        int indent) {
        String li = "";
        String id = locJson.optString("id", null);

        if (id != null) {
            String name = locJson.optString("name", id);
            String desc = locJson.optString("description", "");
            if (!desc.equals("")) desc = " title=\"" + desc + "\" ";
            li = "<li><a style=\"padding-left: " + (1.3 * indent) + "em\" href=\"" + (urlPrefix ==
                null ? "" : urlPrefix) +
                "/encounters/searchResultsAnalysis.jsp?locationCodeField=" + id + "\"" + desc +
                ">" + name + "</a></li>\n";
        }
        JSONArray subArr = locJson.optJSONArray("locationID");
        if (subArr != null)
            for (int i = 0; i < subArr.length(); i++) {
                JSONObject sub = subArr.optJSONObject(i);
                if (sub == null) continue;
                li += getBootstrapList(sub, urlPrefix, indent + 1);
            }
        return li;
    }

    // this will take in a list of locationIDs and expand them to include any children they may have
    // it returns a list that does not have duplicates, so the input list can contain relatives
    // and all should be fine
    // please note it also will leave untouched any IDs which *are not in LocationID.json*,
    // so they will not be filtered out
    // (this is intentional behavior so that an ID need not be in LocationID.json to be considered valid here)
    public static List<String> expandIDs(List<String> ids) {
        return expandIDs(ids, null);
    }

    public static List<String> expandIDs(List<String> ids, String qualifier) {
        List<String> rtn = new ArrayList<String>();

        if (Util.collectionIsEmptyOrNull(ids)) return rtn;
        for (String id : ids) {
            List<String> tree = getIDForParentAndChildren(id, qualifier);
            if (tree.size() < 1) {
                if (!rtn.contains(id)) rtn.add(id);
            } else {
                for (String t : tree) {
                    if (!rtn.contains(t)) rtn.add(t);
                }
            }
        }
        return rtn;
    }
}
