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

package org.openscada.da.net.handler;

import java.util.Map;
import java.util.Set;

import org.openscada.core.Variant;
import org.openscada.da.core.browser.DataItemEntry;
import org.openscada.da.data.IODirection;

public class DataItemEntryCommon extends EntryCommon implements DataItemEntry
{
    private final String id;

    private final Set<IODirection> directions;

    public DataItemEntryCommon ( final String name, final Set<IODirection> directions, final Map<String, Variant> attributes, final String id )
    {
        super ( name, attributes );
        this.directions = directions;
        this.id = id;
    }

    @Override
    public String getId ()
    {
        return this.id;
    }

    @Override
    public Set<IODirection> getIODirections ()
    {
        return this.directions;
    }
}