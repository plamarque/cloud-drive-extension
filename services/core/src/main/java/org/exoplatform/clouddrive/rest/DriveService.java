/*
 * Copyright (C) 2003-2012 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.clouddrive.rest;

import java.util.ArrayList;
import java.util.Collection;

import javax.annotation.security.RolesAllowed;
import javax.jcr.Item;
import javax.jcr.LoginException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.exoplatform.clouddrive.CloudDrive;
import org.exoplatform.clouddrive.CloudDriveAccessException;
import org.exoplatform.clouddrive.CloudDriveException;
import org.exoplatform.clouddrive.CloudDriveService;
import org.exoplatform.clouddrive.CloudFile;
import org.exoplatform.clouddrive.CloudProvider;
import org.exoplatform.clouddrive.DriveRemovedException;
import org.exoplatform.clouddrive.NotCloudFileException;
import org.exoplatform.clouddrive.jcr.JCRNodeFinder;
import org.exoplatform.clouddrive.jcr.NodeFinder;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;

/**
 * REST service providing information about providers. Created by The eXo Platform SAS.
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: DriveService.java 00000 Oct 22, 2012 pnedonosko $
 */
@Path("/clouddrive/drive")
@Produces(MediaType.APPLICATION_JSON)
public class DriveService implements ResourceContainer {

  protected static final Log             LOG           = ExoLogger.getLogger(DriveService.class);

  protected static final String          CONTENT_SUFIX = "/jcr:content";

  protected final CloudDriveService      cloudDrives;

  protected final RepositoryService      jcrService;

  protected final SessionProviderService sessionProviders;

  protected final NodeFinder             finder;

  /**
   * REST cloudDrives uses {@link CloudDriveService} for actual job. This service will use {@link NodeFinder}
   * injected from container.
   * 
   * @param cloudDrives {@link CloudDriveService}
   * @param jcrService {@link RepositoryService}
   * @param sessionProviders {@link SessionProviderService}
   * @param finder {@link NodeFinder}
   */
  public DriveService(CloudDriveService cloudDrives,
                      RepositoryService jcrService,
                      SessionProviderService sessionProviders,
                      NodeFinder finder) {
    this.cloudDrives = cloudDrives;
    this.jcrService = jcrService;
    this.sessionProviders = sessionProviders;
    this.finder = finder;
  }

  /**
   * REST cloudDrives uses {@link CloudDriveService} for actual job.
   * 
   * @param cloudDrives {@link CloudDriveService}
   * @param jcrService {@link RepositoryService}
   * @param sessionProviders {@link SessionProviderService}
   */
  public DriveService(CloudDriveService cloudDrives,
                      RepositoryService jcrService,
                      SessionProviderService sessionProviders) {
    this.cloudDrives = cloudDrives;
    this.jcrService = jcrService;
    this.sessionProviders = sessionProviders;
    this.finder = new JCRNodeFinder(jcrService);
  }

  /**
   * Return drive information.
   * 
   * @param uriInfo - request info TODO need it?
   * @return set of {@link CloudProvider} currently available for connect
   */
  @GET
  @RolesAllowed("users")
  public Response getDrive(@Context UriInfo uriInfo,
                           @QueryParam("workspace") String workspace,
                           @QueryParam("path") String path) {

    if (workspace != null) {
      if (path != null) {
        return readDrive(workspace, path, false);
      } else {
        return Response.status(Status.BAD_REQUEST).entity("Null path.").build();
      }
    } else {
      return Response.status(Status.BAD_REQUEST).entity("Null workspace.").build();
    }
  }

  /**
   * Synchronized cloud drive or its file/folder and return result for client refresh.
   * 
   * @param uriInfo {@link UriInfo} - request info TODO need it?
   * @param path {@link String} Drive Node path
   * @return
   */
  @POST
  @Path("/synchronize/")
  @RolesAllowed("users")
  public Response synchronize(@Context UriInfo uriInfo,
                              @FormParam("workspace") String workspace,
                              @FormParam("path") String path) {

    if (workspace != null) {
      if (path != null) {
        return readDrive(workspace, path, true);
      } else {
        return Response.status(Status.BAD_REQUEST).entity("Null path.").build();
      }
    } else {
      return Response.status(Status.BAD_REQUEST).entity("Null workspace.").build();
    }
  }

  // *********************************** internals *************************************

  /**
   * Read cloud drive and optionally synchronized it before. Drive will contain a file from it will asked,
   * 
   * @param workspace {@link String} Drive workspace
   * @param path {@link String} path of a Node in the Drive
   * @param synchronize {@link Boolean} flag to synch before the read (true to force sync)
   * @return {@link Response} REST response
   */
  protected Response readDrive(String workspace, String path, boolean synchronize) {
    try {
      SessionProvider sp = sessionProviders.getSessionProvider(null);
      Session userSession = sp.getSession(workspace, jcrService.getCurrentRepository());

      try {
        Item item = finder.getItem(userSession, path, true);
        // item = userSession.getItem(path);
        if (item.isNode()) {
          Node userNode = (Node) item;

          CloudDrive local = cloudDrives.findDrive(userNode);
          if (local != null) {
            Collection<CloudFile> files;
            if (synchronize) {
              try {
                files = local.synchronize().getFiles();
              } catch (CloudDriveAccessException e) {
                LOG.warn("Request to cloud drive forbidden or revoked.", e);
                // XXX client should treat this status in special way and obtain new credentials using given
                // provider
                return Response.status(Status.FORBIDDEN).entity(local.getUser().getProvider()).build();
              } catch (CloudDriveException e) {
                LOG.error("Error synchronizing drive " + workspace + ":" + path, e);
                return Response.status(Status.INTERNAL_SERVER_ERROR)
                               .entity("Error synchronizing drive. " + e.getMessage())
                               .build();
              }
            } else {
              files = new ArrayList<CloudFile>();
              try {
                String userPath = userNode.getPath();
                if (!local.getPath().equals(userPath)) {
                  // if path not the drive itself
                  CloudFile file = local.getFile(userPath);
                  files.add(file);
                  if (!file.getPath().equals(path)) {
                    files.add(new LinkedCloudFile(file, path)); // it's symlink, add it also
                  }
                }
              } catch (NotCloudFileException e) {
                LOG.warn("Item " + workspace + ":" + path + " not a cloud file : " + e.getMessage());
                return Response.status(Status.NO_CONTENT).build();
              }
            }

            return Response.ok().entity(DriveInfo.create(local, files)).build();
          } else {
            LOG.warn("Item " + workspace + ":" + path + " not a cloud file or drive not connected.");
            return Response.status(Status.NO_CONTENT).build();
          }
        } else {
          LOG.warn("Item " + workspace + ":" + path + " not a node.");
          return Response.status(Status.PRECONDITION_FAILED).entity("Not a node.").build();
        }
      } finally {
        userSession.logout();
      }
    } catch (LoginException e) {
      LOG.warn("Error login to read drive " + workspace + ":" + path + ". " + e.getMessage());
      return Response.status(Status.UNAUTHORIZED).entity("Authentication error.").build();
    } catch (DriveRemovedException e) {
      LOG.error("Drive removed " + workspace + ":" + path, e);
      return Response.status(Status.NOT_FOUND).entity("Drive removed.").build();
    } catch (RepositoryException e) {
      LOG.error("Error reading drive " + workspace + ":" + path, e);
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity("Error reading drive: storage error.")
                     .build();
    } catch (Throwable e) {
      LOG.error("Error reading drive " + workspace + ":" + path, e);
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity("Error reading drive: runtime error.")
                     .build();
    }
  }

  /**
   * Return file information.
   * 
   * @param workspace {@link String} Drive Node workspace
   * @param path {@link String} File Node path
   * @return {@link Response} REST response
   */
  @GET
  @Path("/file/")
  @RolesAllowed("users")
  public Response getFile(@Context UriInfo uriInfo,
                          @QueryParam("workspace") String workspace,
                          @QueryParam("path") String path) {
    if (workspace != null) {
      if (path != null) {
        try {
          SessionProvider sp = sessionProviders.getSessionProvider(null);
          Session userSession = sp.getSession(workspace, jcrService.getCurrentRepository());
          Item item = finder.getItem(userSession, path, true);
          if (item.isNode()) {
            Node userNode = (Node) item;

            CloudDrive local = cloudDrives.findDrive(userNode);
            if (local != null) {
              try {
                CloudFile file = local.getFile(userNode.getPath());
                if (!file.getPath().equals(path)) {
                  file = new LinkedCloudFile(file, path); // it's symlink
                }

                return Response.ok().entity(file).build();
              } catch (NotCloudFileException e) {
                LOG.warn("Item " + workspace + ":" + path + " not a cloud file: " + e.getMessage());
              }
            }
            LOG.warn("Item " + workspace + ":" + path + " not a cloud file or drive not connected.");
            return Response.status(Status.NO_CONTENT).build();
          } else {
            LOG.warn("Item " + workspace + ":" + path + " not a node.");
            return Response.status(Status.PRECONDITION_FAILED).entity("Not a node.").build();
          }
        } catch (LoginException e) {
          LOG.warn("Error login to read drive file " + workspace + ":" + path + ": " + e.getMessage());
          return Response.status(Status.UNAUTHORIZED).entity("Authentication error.").build();
        } catch (CloudDriveException e) {
          LOG.warn("Error reading file " + workspace + ":" + path, e);
          return Response.status(Status.BAD_REQUEST).entity("Error reading file. " + e.getMessage()).build();
        } catch (RepositoryException e) {
          LOG.error("Error reading file " + workspace + ":" + path, e);
          return Response.status(Status.INTERNAL_SERVER_ERROR)
                         .entity("Error reading file: storage error.")
                         .build();
        } catch (Throwable e) {
          LOG.error("Error reading file " + workspace + ":" + path, e);
          return Response.status(Status.INTERNAL_SERVER_ERROR)
                         .entity("Error reading file: runtime error.")
                         .build();
        }
      } else {
        return Response.status(Status.BAD_REQUEST).entity("Null path.").build();
      }
    } else {
      return Response.status(Status.BAD_REQUEST).entity("Null workspace.").build();
    }
  }
}
