package org.openscada.ae.storage.test;

import org.openscada.ae.core.Event;

public interface InitialEventsProvider
{
    Event[] getInitialEvents ();
}
