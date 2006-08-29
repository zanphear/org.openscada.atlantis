/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006 inavare GmbH (http://inavare.com)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.

 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.openscada.da.core.common;

import java.util.EnumSet;

import org.openscada.ae.core.Variant;
import org.openscada.da.core.server.IODirection;
import org.openscada.da.core.server.InvalidOperationException;

public abstract class DataItemOutput extends DataItemBase {

	public DataItemOutput ( String name )
    {
		super ( new DataItemInformationBase ( name, EnumSet.of ( IODirection.OUTPUT ) ) );
	}

	public Variant getValue() throws InvalidOperationException
    {
		throw new InvalidOperationException ();
	}

}
