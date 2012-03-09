package org.projectbamboo.util.fedora;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;

import org.w3c.dom.Element;

public class DatastreamProfile {

    /**
     * An enumeration that represents all of the properties associated
     * with a datastream in fedora with internal information about how
     * those properties are referenced.
     */
    public static enum DatastreamProperty {
        DS_LABEL("dsLabel"),
        DS_VERSION_ID("dsVersionID"),
        DS_CREATE_DATE("dsCreateDate"),
        DS_STATE("dsState"),
        DS_MIME("dsMIME"),
        DS_FORMAT_URI("dsFormatURI"),
        DS_CONTROL_GROUP("dsControlGroup"),
        DS_SIZE("dsSize"),
        DS_VERSIONABLE("dsVersionable"),
        DS_INFO_TYPE("dsInfoType"),
        DS_LOCATION("dsLocation"),
        DS_LOCATION_TYPE("dsLocationType"),
        DS_CHECKSUM_TYPE("dsChecksumType"),
        DS_CHECKSUM("dsChecksum");
        
        private String propertyName;
        
        DatastreamProperty(String name) {
            this.propertyName = name;
        }
        
        public String getPropertyName() {
            return this.propertyName;
        }
    }
    
    private FedoraClient fc;
    
    private Element profileEl;
    
    public DatastreamProfile(FedoraClient fc, Element profileEl) {
        this.fc = fc;
        this.profileEl = profileEl;
    }
    
    public String getProperty(DatastreamProperty property) {
        try {
            // compatible with fedora 3.4
            String value = (String) fc.getXPath().evaluate("fedora-management:" + property.getPropertyName(), this.profileEl, XPathConstants.STRING);
            if (value != null) {
                return value;
            } else {
                // compatible with fedora 3.2
                return (String) fc.getXPath().evaluate("/datastreamProfile/" + property.getPropertyName(), this.profileEl, XPathConstants.STRING);
            }
        } catch (XPathException ex) {
            return null;
        }
    }
    
}
