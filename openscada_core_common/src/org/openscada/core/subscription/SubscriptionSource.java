/*
 * This file is part of the OpenSCADA pimport java.util.Collection;
inavare GmbH (http://inavare.com)
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

package org.openscada.core.subscription;

import java.util.Collection;

/**
 * A event source which can be used with the subscription manager.
 * @author Jens Reimann
 *
 */
public interface SubscriptionSource
{
    /**
     * Validate if the provided subcription information can bind to this subscription source
     * @param information The information to check
     * @return <code>true</code> if the listener can bind to this event source. In this case the {@link #addListener(Collection)}
     * method may not reject the listener.
     */
    public abstract boolean supportsListener ( SubscriptionInformation information );

    public abstract void addListener ( Collection<SubscriptionInformation> listeners );

    public abstract void removeListener ( Collection<SubscriptionInformation> listeners );
}
