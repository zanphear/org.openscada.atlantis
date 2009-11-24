/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006-2009 inavare GmbH (http://inavare.com)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.openscada.da.server.exec2;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;

import org.apache.xmlbeans.XmlException;
import org.openscada.core.Variant;
import org.openscada.da.exec2.configuration.RootDocument;
import org.openscada.da.server.browser.common.FolderCommon;
import org.openscada.da.server.common.chain.storage.ChainStorageServiceHelper;
import org.openscada.da.server.common.impl.HiveCommon;
import org.openscada.da.server.exec2.command.CommandQueue;
import org.openscada.da.server.exec2.command.ContinuousCommand;
import org.openscada.da.server.exec2.command.TriggerCommand;
import org.openscada.da.server.exec2.configuration.ConfigurationException;
import org.openscada.da.server.exec2.configuration.XmlConfigurator;
import org.openscada.utils.collection.MapBuilder;
import org.w3c.dom.Node;

public class Hive extends HiveCommon
{
    private static final String TRIGGER_FOLDER_NAME = "triggers";

    /**
     * Root folder of the Hive
     */
    private final FolderCommon rootFolder = new FolderCommon ();

    private final Collection<CommandQueue> queues = new LinkedList<CommandQueue> ();

    private final Collection<ContinuousCommand> continuousCommands = new LinkedList<ContinuousCommand> ();

    private final Collection<TriggerCommand> triggers = new LinkedList<TriggerCommand> ();

    private final FolderCommon triggerFolder;

    /**
     * Default Constructor
     * @throws XmlException
     * @throws IOException
     * @throws ConfigurationException 
     */
    public Hive () throws XmlException, IOException, ConfigurationException
    {
        this ( RootDocument.Factory.parse ( new File ( "configuration.xml" ) ) );
    }

    /**
     * Constructor
     * @param node
     * @throws XmlException
     * @throws ConfigurationException 
     */
    public Hive ( final Node node ) throws XmlException, ConfigurationException
    {
        this ( RootDocument.Factory.parse ( node ) );
    }

    /**
     * Set up the hive and start the command queues
     * @param document Configuration
     * @throws ConfigurationException 
     */
    public Hive ( final RootDocument document ) throws ConfigurationException
    {
        super ();

        ChainStorageServiceHelper.registerDefaultPropertyService ( this );

        this.setRootFolder ( this.rootFolder );
        this.triggerFolder = new FolderCommon ();
        this.rootFolder.add ( TRIGGER_FOLDER_NAME, this.triggerFolder, new MapBuilder<String, Variant> ().put ( "description", new Variant ( "Contains all triggers" ) ).getMap () );

        new XmlConfigurator ( document ).configure ( this );

        // Setup and start the queues
        this.startQueues ();
    }

    /**
     * Initializes all configured command queues and executes them in threads
     */
    protected void startQueues ()
    {
        for ( final CommandQueue queue : this.queues )
        {
            queue.start ( this, this.rootFolder );
        }
        for ( final ContinuousCommand command : this.continuousCommands )
        {
            command.start ( this, this.rootFolder );
        }
        for ( final TriggerCommand command : this.triggers )
        {
            command.register ( this, this.triggerFolder );
        }
    }

    /**
     * Stops all running command queues and destroy them
     */
    @Override
    public void stop ()
    {
        for ( final CommandQueue queue : this.queues )
        {
            queue.stop ();
        }
        for ( final ContinuousCommand command : this.continuousCommands )
        {
            command.stop ();
        }
        for ( final TriggerCommand command : this.triggers )
        {
            command.unregister ();
        }
    }

    public void addQueue ( final CommandQueue queue )
    {
        this.queues.add ( queue );
    }

    public void addContinuousCommand ( final ContinuousCommand command )
    {
        this.continuousCommands.add ( command );
    }

    /**
     * Add a new trigger command
     * @param command the new trigger command
     */
    public void addTrigger ( final TriggerCommand command )
    {
        this.triggers.add ( command );
    }
}