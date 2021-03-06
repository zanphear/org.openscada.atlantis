/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006-2012 TH4 SYSTEMS GmbH (http://th4-systems.com)
 *
 * OpenSCADA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3
 * only, as published by the Free Software Foundation.
 *
 * OpenSCADA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License version 3 for more details
 * (a copy is included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU Lesser General Public License
 * version 3 along with OpenSCADA. If not, see
 * <http://opensource.org/licenses/lgpl-3.0.html> for a copy of the LGPLv3 License.
 */

package org.openscada.da.server.osgi.testing;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.openscada.ca.ConfigurationAdministrator;
import org.openscada.ca.ConfigurationFactory;
import org.openscada.da.server.common.DataItem;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator
{

    private final static Logger logger = LoggerFactory.getLogger ( Activator.class );

    private DataItemTest1 service;

    private ScheduledThreadPoolExecutor executor;

    private ServiceRegistration<DataItem> handle;

    private TimeItemFactory factory1;

    private ServiceRegistration<?> factory1Handle;

    @Override
    public void start ( final BundleContext context ) throws Exception
    {
        this.executor = new ScheduledThreadPoolExecutor ( 1 );
        this.service = new DataItemTest1 ( "test", this.executor );

        {
            final Dictionary<String, Object> properties = new Hashtable<String, Object> ();
            this.handle = context.registerService ( DataItem.class, this.service, properties );
        }

        {
            this.factory1 = new TimeItemFactory ( this.executor, context );
            final Dictionary<String, Object> properties = new Hashtable<String, Object> ();
            properties.put ( ConfigurationAdministrator.FACTORY_ID, TimeItemFactory.class.getName () );
            this.factory1Handle = context.registerService ( ConfigurationFactory.class, this.factory1, properties );
        }
    }

    @Override
    public void stop ( final BundleContext context ) throws Exception
    {
        logger.info ( "Stopping test server" );

        this.factory1Handle.unregister ();
        this.factory1Handle = null;

        this.factory1.dispose ();
        this.factory1 = null;

        this.handle.unregister ();
        this.handle = null;

        this.service.dispose ();
        this.service = null;

        this.executor.shutdown ();
        this.executor = null;
    }

}
