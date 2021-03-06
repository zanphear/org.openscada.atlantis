/*
 * This file is part of the openSCADA project
 * 
 * Copyright (C) 2006-2010 TH4 SYSTEMS GmbH (http://th4-systems.com)
 *
 * openSCADA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3
 * only, as published by the Free Software Foundation.
 *
 * openSCADA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License version 3 for more details
 * (a copy is included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU Lesser General Public License
 * version 3 along with openSCADA. If not, see
 * <http://opensource.org/licenses/lgpl-3.0.html> for a copy of the LGPLv3 License.
 */

package org.openscada.da.server.modbus.io.message;

import org.openscada.da.server.modbus.io.message.request.RequestMessage;
import org.openscada.da.server.modbus.io.message.response.ResponseMessage;

public class ResponseWrapper
{
    private final byte unitIdentifier;

    private final ResponseMessage message;

    private final RequestMessage originalRequest;

    public ResponseWrapper ( final byte unitIdentifier, final ResponseMessage message, final RequestMessage request )
    {
        this.unitIdentifier = unitIdentifier;
        this.message = message;
        this.originalRequest = request;
    }

    public byte getUnitIdentifier ()
    {
        return this.unitIdentifier;
    }

    public ResponseMessage getMessage ()
    {
        return this.message;
    }

    public RequestMessage getOriginalRequest ()
    {
        return this.originalRequest;
    }

    @Override
    public String toString ()
    {
        return "ResponseWrapper [message=" + this.message + ", originalRequest=" + this.originalRequest + ", unitIdentifier=" + this.unitIdentifier + "]";
    }
}
