/**
 *  Copyright 2009, 2010 The Regents of the University of California
 *  Licensed under the Educational Community License, Version 2.0
 *  (the "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *
 *  http://www.osedu.org/licenses/ECL-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an "AS IS"
 *  BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 */

package org.opencastproject.distribution.hydrant;

import org.opencastproject.distribution.api.DistributionException;
import org.opencastproject.distribution.api.DistributionService;
import org.opencastproject.job.api.AbstractJobProducer;
import org.opencastproject.job.api.Job;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.MediaPackageParser;
import org.opencastproject.mediapackage.Track;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.UserDirectoryService;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.util.FileSupport;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.PathSupport;
import org.opencastproject.util.UrlSupport;
import org.opencastproject.workspace.api.Workspace;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Distributes media to the local media delivery directory.
 */
public class HydrantDistributionService extends AbstractJobProducer implements DistributionService {

  /** Logging facility */
  private static final Logger logger = LoggerFactory.getLogger(HydrantDistributionService.class);

  /** Receipt type */
  public static final String JOB_TYPE = "org.opencastproject.distribution.hydrant";

  /** List of available operations on jobs */
  private enum Operation {
    Distribute, Retract
  };

  /** The workspace reference */
  protected Workspace workspace = null;

  /** The service registry */
  protected ServiceRegistry serviceRegistry = null;

  /** The security service */
  protected SecurityService securityService = null;

  /** The user directory service */
  protected UserDirectoryService userDirectoryService = null;

  /** The organization directory service */
  protected OrganizationDirectoryService organizationDirectoryService = null;

  /** The distribution directory */
  protected File distributionDirectory = null;

  /** The base URL for hydrant */
  protected String hydrantUrl = null;

  /** The admin username for hydrant */
  protected String hydrantAdminUsername = null;

  /** The admin password for hydrant */
  protected String hydrantAdminPassword = null;

  /**
   * Creates a new instance of the hydrant distribution service.
   */
  public HydrantDistributionService() {
    super(JOB_TYPE);
  }

  protected void activate(ComponentContext cc) {
    // Get the configured hydrant and server URLs
    if (cc != null) {
      hydrantUrl = StringUtils.trimToNull(cc.getBundleContext().getProperty("org.opencastproject.hydrant.url"));
      if (hydrantUrl == null)
        logger.warn("Hydrant url was not set (org.opencastproject.hydrant.url)");
      else
        logger.info("hydrant url is {}", hydrantUrl);

      hydrantAdminUsername = StringUtils.trimToNull(cc.getBundleContext().getProperty("org.opencastproject.hydrant.admin.username"));
      if (hydrantAdminUsername == null)
        logger.warn("Hydrant admin username was not set (org.opencastproject.hydrant.admin.username)");
      else
        logger.info("hydrant admin username is {}", hydrantAdminUsername);

      hydrantAdminPassword = StringUtils.trimToNull(cc.getBundleContext().getProperty("org.opencastproject.hydrant.admin.password"));
      if (hydrantAdminPassword == null)
        logger.warn("Hydrant admin password was not set (org.opencastproject.hydrant.admin.password)");
      else
        logger.info("hydrant admin password is {}", hydrantAdminPassword);
    }
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.opencastproject.distribution.api.DistributionService#distribute(MediaPackage, java.lang.String)
   */
  @Override
  public Job distribute(MediaPackage mediapackage, String elementId) throws DistributionException,
          MediaPackageException {

    if (mediapackage == null)
      throw new MediaPackageException("Mediapackage must be specified");
    if (elementId == null)
      throw new MediaPackageException("Element ID must be specified");

    if (StringUtils.isBlank(hydrantUrl))
      throw new IllegalStateException("Hydrant url must be set (org.opencastproject.hydrant.url)");
    if (StringUtils.isBlank(hydrantAdminUsername))
      throw new IllegalStateException("Hydrant admin username must be set (org.opencastproject.hydrant.admin.username)");
    if (StringUtils.isBlank(hydrantAdminPassword))
      throw new IllegalStateException("Hydrant admin password must be set (org.opencastproject.hydrant.admin.password)");

    try {
      return serviceRegistry.createJob(JOB_TYPE, Operation.Distribute.toString(),
              Arrays.asList(MediaPackageParser.getAsXml(mediapackage), elementId));
    } catch (ServiceRegistryException e) {
      throw new DistributionException("Unable to create a job", e);
    }
  }

  /**
   * Distributes the mediapackage's element to the location that is returned by the concrete implementation. In
   * addition, a representation of the distributed element is added to the mediapackage.
   * 
   * @see org.opencastproject.distribution.api.DistributionService#distribute(String, MediaPackageElement)
   */
  protected MediaPackageElement distribute(Job job, MediaPackage mediapackage, String elementId)
          throws DistributionException {

    if (mediapackage == null)
      throw new IllegalArgumentException("Mediapackage must be specified");
    if (elementId == null)
      throw new IllegalArgumentException("Element ID must be specified");

    String mediaPackageId = mediapackage.getIdentifier().compact();
    MediaPackageElement element = mediapackage.getElementById(elementId);

    // Make sure the element exists
    if (mediapackage.getElementById(elementId) == null)
      throw new IllegalStateException("No element " + elementId + " found in mediapackage");
    
    try {
      // The hydrant server only supports tracks
      if (!(element instanceof Track)) {
        return null;
      }

        String parentpid = mediapackage.getTitle();
        if (parentpid == null) {
          throw new DistributionException("Could not find Hydrant pid in mediapackage.");
        }

        logger.trace("Found parent pid: {}", parentpid);

        try{
                //TODO make single call to hydrant: /assets/{parentpid}/file_assets posting Filedata[], container_id, and authenticity_token?
		String url = UrlSupport.concat(new String[] { hydrantUrl, "assets", parentpid, "video_assets?container_id=" + parentpid });
                MultiThreadedHttpConnectionManager mgr = new MultiThreadedHttpConnectionManager();
                HttpClient client = new HttpClient(mgr);

		String loginUrl = UrlSupport.concat(new String[] { hydrantUrl, "users", "sign_in" });
                PostMethod loginPost = new PostMethod(loginUrl);
                NameValuePair[] data = {
                new NameValuePair("user[email]", hydrantAdminUsername),
                new NameValuePair("user[password]", hydrantAdminPassword),
                new NameValuePair("user[remember_me]", "0")};
                loginPost.setRequestBody(data);
                int loginStatus = client.executeMethod(loginPost);
                logger.debug("Got status: " + loginStatus);
                if(loginStatus != 302){
                        throw new Exception("Error logging into hydra.");
                }

                PostMethod post = new PostMethod(url);
                NameValuePair[] requestPairs = {
                        new NameValuePair("video_url", element.getURI().toString()),
                 };
                post.setRequestBody(requestPairs);
                int status = client.executeMethod(post);
                logger.debug("Got status: " + status);
                logger.trace("Got response body: " + post.getResponseBodyAsString());
        }catch(IOException e){
                logger.debug("Exception distributing to Hydrant: " + e.getCause());
                throw new DistributionException("Error distributing to Hydrant instance", e);
        }

      logger.info("Distributed {} to hydrant", elementId);

      // Create a representation of the distributed file in the mediapackage
      MediaPackageElement distributedElement = (MediaPackageElement) element.clone();
//TODO Create and set a valid distribution URI
/*
      try {
        distributedElement.setURI(getDistributionUri(mediaPackageId, element));
      } catch (URISyntaxException e) {
        throw new DistributionException("Distributed element produces an invalid URI", e);
      }
*/
      distributedElement.setIdentifier(null);

      logger.info("Finished distribution of {}", element);
      return distributedElement;

    } catch (Exception e) {
      logger.warn("Error distributing " + element, e);
      if (e instanceof DistributionException) {
        throw (DistributionException) e;
      } else {
        throw new DistributionException(e);
      }
    }
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.opencastproject.distribution.api.DistributionService#retract(org.opencastproject.mediapackage.MediaPackage,
   *      java.lang.String)
   */
  public Job retract(MediaPackage mediaPackage, String elementId) throws DistributionException {
    if (mediaPackage == null)
      throw new IllegalArgumentException("Mediapackage must be specified");
    if (elementId == null)
      throw new IllegalArgumentException("Element ID must be specified");

    if (StringUtils.isBlank(hydrantUrl))
      throw new IllegalStateException("Hydrant url must be set (org.opencastproject.hydrant.url)");
    if (StringUtils.isBlank(hydrantAdminUsername))
      throw new IllegalStateException("Hydrant admin username must be set (org.opencastproject.hydrant.admin.username)");
    if (StringUtils.isBlank(hydrantAdminPassword))
      throw new IllegalStateException("Hydrant admin password must be set (org.opencastproject.hydrant.admin.password)");

    try {
      List<String> arguments = new ArrayList<String>();
      arguments.add(MediaPackageParser.getAsXml(mediaPackage));
      arguments.add(elementId);
      return serviceRegistry.createJob(JOB_TYPE, Operation.Retract.toString(), arguments);
    } catch (ServiceRegistryException e) {
      throw new DistributionException("Unable to create a job", e);
    }
  }

  /**
   * Retracts the mediapackage with the given identifier from the distribution channel.
   * 
   * @param job
   *          the associated job
   * @param mediapackage
   *          the mediapackage
   * @param elementId
   *          the element identifier
   * @return the retracted element or <code>null</code> if the element was not retracted
   */
  protected MediaPackageElement retract(Job job, MediaPackage mediapackage, String elementId)
          throws DistributionException {

    if (mediapackage == null)
      throw new IllegalArgumentException("Mediapackage must be specified");
    if (elementId == null)
      throw new IllegalArgumentException("Element ID must be specified");

    // Make sure the element exists
    MediaPackageElement element = mediapackage.getElementById(elementId);
    if (element == null)
      throw new IllegalStateException("No element " + elementId + " found in mediapackage");

        //TODO implement this!!
        logger.info("Nothing done...this method is not implemented yet.");

	return element;
/*
    // Find the element that has been created as part of the distribution process
    String mediaPackageId = mediapackage.getIdentifier().compact();
    URI distributedURI = null;
    MediaPackageElement distributedElement = null; 
    try {
      distributedURI = getDistributionUri(mediaPackageId, element);
      for (MediaPackageElement e : mediapackage.getElements()) {
        if (distributedURI.equals(e.getURI())) {
          distributedElement = e;
          break;
        }
      }
    } catch (URISyntaxException e) {
      throw new DistributionException("Retracted element produces an invalid URI", e);
    }

    // Has this element been distributed?
    if (distributedElement == null)
      return null;
    
    String mediapackageId = mediapackage.getIdentifier().compact();
    try {

      File mediapackageDir = getMediaPackageDirectory(mediapackageId);
      File elementDir = getDistributionFile(mediapackage, element);

      // Does the file exist? If not, the current element has not been distributed to this channel
      // or has been removed otherwise
      if (!elementDir.exists())
        return distributedElement;

      // Try to remove the file and - if possible - the parent folder
      FileUtils.forceDelete(elementDir);
      if (mediapackageDir.list().length == 0) {
        FileSupport.delete(mediapackageDir);
      }

      logger.info("Finished rectracting element {} of media package {}", elementId, mediapackageId);

      return distributedElement;
    } catch (Exception e) {
      logger.warn("Error retracting element " + elementId + " of mediapackage " + mediapackageId, e);
      if (e instanceof DistributionException) {
        throw (DistributionException) e;
      } else {
        throw new DistributionException(e);
      }
    }
*/

  }

  /**
   * Gets the destination file to copy the contents of a mediapackage element.
   * 
   * @param mediaPackage
   *          the media package
   * @param element
   *          The mediapackage element being distributed
   * @return The file to copy the content to
   */
  protected File getDistributionFile(MediaPackage mediaPackage, MediaPackageElement element) {
    String elementId = element.getIdentifier();
    String fileName = FilenameUtils.getName(element.getURI().toString());
    
    String directoryName = distributionDirectory.getAbsolutePath();
    String destinationFileName = PathSupport.concat(new String[] { directoryName,
            mediaPackage.getIdentifier().compact(), elementId, fileName });
    return new File(destinationFileName);
  }

  /**
   * Gets the URI for the element to be distributed.
   * 
   * @param mediaPackageId
   *          the mediapackage identifier
   * @param element
   *          The mediapackage element being distributed
   * @return The resulting URI after distribution
   * @throws URISyntaxException
   *           if the concrete implementation tries to create a malformed uri
   */
  protected URI getDistributionUri(String mediaPackageId, MediaPackageElement element) throws URISyntaxException {
    //TODO Implement this!
    String elementId = element.getIdentifier();
    String destinationURI = UrlSupport.concat(new String[] { hydrantUrl, mediaPackageId, elementId });
    return new URI(destinationURI);
  }

  /**
   * Gets the directory containing the distributed files for this mediapackage.
   * 
   * @param mediaPackageId
   *          the mediapackage ID
   * @return the filesystem directory
   */
  protected File getMediaPackageDirectory(String mediaPackageId) {
    return new File(distributionDirectory, mediaPackageId);
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.opencastproject.job.api.AbstractJobProducer#process(org.opencastproject.job.api.Job)
   */
  @Override
  protected String process(Job job) throws Exception {
    Operation op = null;
    String operation = job.getOperation();
    List<String> arguments = job.getArguments();
    try {
      op = Operation.valueOf(operation);
      MediaPackage mediapackage = MediaPackageParser.getFromXml(arguments.get(0));
      String elementId = arguments.get(1);
      switch (op) {
        case Distribute:
          MediaPackageElement distributedElement = distribute(job, mediapackage, elementId);
          return (distributedElement != null) ? MediaPackageElementParser.getAsXml(distributedElement) : null;
        case Retract:
          MediaPackageElement retractedElement = retract(job, mediapackage, elementId);
          return (retractedElement != null) ? MediaPackageElementParser.getAsXml(retractedElement) : null;
        default:
          throw new IllegalStateException("Don't know how to handle operation '" + operation + "'");
      }
    } catch (IllegalArgumentException e) {
      throw new ServiceRegistryException("This service can't handle operations of type '" + op + "'", e);
    } catch (IndexOutOfBoundsException e) {
      throw new ServiceRegistryException("This argument list for operation '" + op + "' does not meet expectations", e);
    } catch (Exception e) {
      throw new ServiceRegistryException("Error handling operation '" + op + "'", e);
    }
  }

  /**
   * Callback for the OSGi environment to set the workspace reference.
   * 
   * @param workspace
   *          the workspace
   */
  protected void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  /**
   * Callback for the OSGi environment to set the service registry reference.
   * 
   * @param serviceRegistry
   *          the service registry
   */
  protected void setServiceRegistry(ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.opencastproject.job.api.AbstractJobProducer#getServiceRegistry()
   */
  @Override
  protected ServiceRegistry getServiceRegistry() {
    return serviceRegistry;
  }

  /**
   * Callback for setting the security service.
   * 
   * @param securityService
   *          the securityService to set
   */
  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  /**
   * Callback for setting the user directory service.
   * 
   * @param userDirectoryService
   *          the userDirectoryService to set
   */
  public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
    this.userDirectoryService = userDirectoryService;
  }

  /**
   * Sets a reference to the organization directory service.
   * 
   * @param organizationDirectory
   *          the organization directory
   */
  public void setOrganizationDirectoryService(OrganizationDirectoryService organizationDirectory) {
    this.organizationDirectoryService = organizationDirectory;
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.opencastproject.job.api.AbstractJobProducer#getSecurityService()
   */
  @Override
  protected SecurityService getSecurityService() {
    return securityService;
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.opencastproject.job.api.AbstractJobProducer#getUserDirectoryService()
   */
  @Override
  protected UserDirectoryService getUserDirectoryService() {
    return userDirectoryService;
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.opencastproject.job.api.AbstractJobProducer#getOrganizationDirectoryService()
   */
  @Override
  protected OrganizationDirectoryService getOrganizationDirectoryService() {
    return organizationDirectoryService;
  }

}
