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
package org.exoplatform.clouddrive.ecms.filters;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.exoplatform.clouddrive.CloudDrive;
import org.exoplatform.clouddrive.CloudDriveService;
import org.exoplatform.clouddrive.jcr.JCRLocalCloudDrive;
import org.exoplatform.services.cms.link.NodeFinder;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;


/**
 * Accept only ecd:cloudDrive nodes.<br>
 * 
 * Created by The eXo Platform SAS.
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: CloudDriveFiler.java 00000 Nov 5, 2012 pnedonosko $
 * 
 */
public class CloudDriveFiler extends AbstractCloudDriveNodeFilter {

  protected static final Log LOG = ExoLogger.getLogger(CloudDriveFiler.class);
  
  /**
   * {@inheritDoc}
   */
  @Override
  protected boolean accept(Node node) throws RepositoryException {
    CloudDriveService driveService = WCMCoreUtils.getService(CloudDriveService.class);
    NodeFinder finder = WCMCoreUtils.getService(NodeFinder.class);

    // doing this we are taking symlinks in account also
    Node actualNode = (Node) finder.getItem(node.getSession(), node.getPath(), true);

    return driveService.isDrive(actualNode);
    
    // TODO cleanup
    // return node.isNodeType(JCRLocalCloudDrive.ECD_CLOUDDRIVE);
  }
}
