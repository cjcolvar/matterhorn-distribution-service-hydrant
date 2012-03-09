package org.projectbamboo.util.fedora;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.NamespaceContext;

/**
 * <p>
 *   A general purpose NamespaceContext implementation backed
 *   by a Map of prefix Strings to URI Strings.  The {@link 
 *   #setNamespace(String, String))} method may be invoked to 
 *   add prefix/namespace mappings.
 * </p>
 */
public class MapNamespaceContext implements NamespaceContext {

    private Map<String, String> prefixToUriMap;
    
    private Map<String, String> uriToSchemaLocationMap;
    
    public MapNamespaceContext() {
        this.prefixToUriMap = new HashMap<String, String>();
        this.uriToSchemaLocationMap = new HashMap<String, String>();
    }
    
    public void setNamespace(String prefix, String namespaceURI) {
        this.prefixToUriMap.put(prefix, namespaceURI);
    }
    
    public void setNamespace(String prefix, String namespaceURI, String schemaLocation) {
        this.prefixToUriMap.put(prefix, namespaceURI);
        this.uriToSchemaLocationMap.put(namespaceURI, schemaLocation);
    }
    
    public String getNamespaceURI(String prefix) {
        return this.prefixToUriMap.get(prefix);
    }

    public String getPrefix(String namespaceURI) {
        return getPrefixes(namespaceURI).next();
    }

    public Iterator<String> getPrefixes(String namespaceURI) {
        List<String> prefixes = new ArrayList<String>();
        for (String prefix : prefixToUriMap.keySet()) {
            if (prefixToUriMap.get(prefix).equals(namespaceURI)) {
                prefixes.add(prefix);
            }
        }
        return prefixes.iterator();
    }
    
    public String getSchemaLocation(String namespaceURI) {
        return this.uriToSchemaLocationMap.get(namespaceURI);
    }

}
