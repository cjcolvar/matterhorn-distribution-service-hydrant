package org.projectbamboo.util.fedora;

import java.text.ParseException;
import java.util.Date;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;


/**
 * <p>
 *   An object that encapsulates a fedora object's profile information.
 *   When only a single property is needed for an object, the method
 *   {@link FedoraClient.getObjectProperty()} should be invoked, but
 *   if multiple properties are needed, the method {@link 
 *   FedoraClient.getObjectProfile()} should be used to get an instance
 *   of this Object to reduce the number of HTTP roundtrips.
 * </p>
 * <p>
 *   This object is a very thin wrapper around the XML returned by the
 *   REST api call for an object profile.
 * </p>
 */
public class ObjectProfile {

    /**
     * The object properties available in Fedora 3.4.
     */
    private enum ObjectProperty {
        LABEL,
        OWNER_ID,
        MODELS,
        CREATE_DATE,
        LAST_MOD_DATE,
        DISS_INDEX_VIEW_URL,
        ITEM_INDEX_VIEW_URL,
        STATE;
    }
    
    /**
     * The Document parsed from the REST call.
     */
    private Document objectProfileDoc;
    
    /**
     * A reference to an XPath sufficient for use extracting field
     * values.
     */
    private XPath xpath;
    
    ObjectProfile(Document objectProfileDoc, XPath xpath) {
        this.objectProfileDoc = objectProfileDoc;
        MapNamespaceContext nsc = new MapNamespaceContext();
        nsc.setNamespace("fa", "http://www.fedora.info/definitions/1/0/access/");
        this.xpath = XPathFactory.newInstance().newXPath();
        this.xpath.setNamespaceContext(nsc);
        
        
    }
    
    public String getLabel() {
        try {
            String label = (String) this.xpath.evaluate("/fa:objectProfile/fa:objLabel" , this.objectProfileDoc, XPathConstants.STRING);
            if (label == null || label.equals("")) {
                label =  (String) this.xpath.evaluate("/objectProfile/objLabel/", this.objectProfileDoc, XPathConstants.STRING);
            }
            return label;
        } catch (XPathExpressionException ex) {
            return null;
        }
    }
    
    public String getOwnerId() {
        try {
            String ownerId = (String) this.xpath.evaluate("/fa:objectProfile/fa:objOwnerId" , this.objectProfileDoc, XPathConstants.STRING);
            if (ownerId == null || ownerId.equals("")) {
                ownerId = (String) this.xpath.evaluate("/objectProfile/objOwnerId" , this.objectProfileDoc, XPathConstants.STRING);
            }
            return ownerId;
        } catch (XPathExpressionException ex) {
            return null;
        }
    }
    
    public Date getCreateDate() {
        try {
            String createDate = (String) this.xpath.evaluate("/fa:objectProfile/fa:objCreateDate" , this.objectProfileDoc, XPathConstants.STRING);
            if (createDate == null || createDate.equals("")) {
                createDate = (String) this.xpath.evaluate("/objectProfile/objCreateDate" , this.objectProfileDoc, XPathConstants.STRING);
            }
            return FedoraClient.parseFedoraDate(createDate);
        } catch (XPathExpressionException ex) {
            return null;
        } catch (ParseException ex) {
            return null;
        }
    }
    
    public Date getLastModDate() {
        try {
            String lastModDate = (String) this.xpath.evaluate("/fa:objectProfile/fa:objLastModDate" , this.objectProfileDoc, XPathConstants.STRING);
            if (lastModDate == null || lastModDate.equals("")) {
                lastModDate = (String) this.xpath.evaluate("/objectProfile/objLastModDate", this.objectProfileDoc, XPathConstants.STRING);
            }
            return FedoraClient.parseFedoraDate(lastModDate);
        } catch (XPathExpressionException ex) {
            return null;
        } catch (ParseException ex) {
            return null;
        }
    }
    
    public String getState() {
        try {
            String state = (String) this.xpath.evaluate("/fa:objectProfile/fa:objState" , this.objectProfileDoc, XPathConstants.STRING);
            if (state == null || state.equals("")) {
                state = (String) this.xpath.evaluate("/objectProfile/objState" , this.objectProfileDoc, XPathConstants.STRING);
            }
            return state;
        } catch (XPathExpressionException ex) {
            return null;
        }
    }
}
