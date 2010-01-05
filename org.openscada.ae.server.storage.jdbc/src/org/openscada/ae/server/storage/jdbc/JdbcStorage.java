package org.openscada.ae.server.storage.jdbc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.openscada.ae.Event;
import org.openscada.ae.Event.Fields;
import org.openscada.ae.server.storage.BaseStorage;
import org.openscada.ae.server.storage.Query;
import org.openscada.ae.server.storage.StoreListener;
import org.openscada.ae.server.storage.jdbc.internal.JdbcStorageDAO;
import org.openscada.ae.server.storage.jdbc.internal.MutableEvent;
import org.openscada.core.Variant;
import org.openscada.utils.filter.FilterParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link JdbcStorage} is a thin wrapper around the {@link JdbcStorageDAO} which provides just 
 * the basic methods to store Events. An event is converted to a {@link MutableEvent}
 * and then placed on a queue to store.
 * 
 * @author jrose
 */
public class JdbcStorage extends BaseStorage
{
    private static final Logger logger = LoggerFactory.getLogger ( JdbcStorage.class );

    private final AtomicReference<JdbcStorageDAO> jdbcStorageDAO = new AtomicReference<JdbcStorageDAO> ();

    private ExecutorService storageQueueProcessor;

    private long shutDownTimeout = 30000;
    
    private AtomicInteger queueSize = new AtomicInteger (0);

    public JdbcStorageDAO getJdbcStorageDAO ()
    {
        return jdbcStorageDAO.get ();
    }

    public void setJdbcStorageDAO ( JdbcStorageDAO jdbcStorageDAO )
    {
        this.jdbcStorageDAO.set ( jdbcStorageDAO );
    }

    public long getShutDownTimeout ()
    {
        return shutDownTimeout;
    }

    public void setShutDownTimeout ( long shutDownTimeout )
    {
        this.shutDownTimeout = shutDownTimeout;
    }

    /**
     * is called by Spring when {@link JdbcStorage} is initialized. It creates a
     * new {@link ExecutorService} which is used to schedule the events for storage.
     *  
     * @throws Exception
     */
    public void start () throws Exception
    {
        logger.info ( "jdbcStorageDAO instanciated" );
        storageQueueProcessor = Executors.newSingleThreadExecutor ( new ThreadFactory () {
            public Thread newThread ( Runnable r )
            {
                return new Thread ( r, "Executor-" + JdbcStorage.class.getCanonicalName () );
            }
        } );
    }

    /**
     * is called by Spring when {@link JdbcStorage} is destroyed. It halts the
     * {@link ExecutorService} and tries to process the remaining events (say, store them
     * to the database).
     * 
     * @throws Exception
     */
    public void stop () throws Exception
    {
        List<Runnable> openTasks = storageQueueProcessor.shutdownNow ();
        final int numOfOpenTasks = openTasks.size ();
        if ( numOfOpenTasks > 0 )
        {
            int numOfOpenTasksRemaining = numOfOpenTasks;
            logger.info ( "jdbcStorageDAO is beeing shut down, but there are still {} events to store", numOfOpenTasks );
            for ( Runnable runnable : openTasks )
            {
                runnable.run ();
                numOfOpenTasksRemaining -= 1;
                logger.debug ( "jdbcStorageDAO is beeing shut down, but there are still {} events to store", numOfOpenTasksRemaining );
            }
        }
        logger.info ( "jdbcStorageDAO destroyed" );
    }

    /* (non-Javadoc)
     * @see org.openscada.ae.server.storage.Storage#query(java.lang.String)
     */
    public Query query ( String filter ) throws Exception
    {
        logger.debug ( "Query requested {}", filter );
        return new JdbcQuery ( jdbcStorageDAO.get (), new FilterParser ( filter ).getFilter () );
    }

    /**
     * the events are not actually stored within this method, rather given an 
     * {@link ExecutorService} and stored later. This guarantees a immediate return
     * of this method.
     * 
     * @see org.openscada.ae.server.storage.Storage#store(org.openscada.ae.Event)
     */
    public Event store ( final Event event, final StoreListener listener )
    {
        queueSize.incrementAndGet ();
        final Event eventToStore = createEvent ( event );
        logger.debug ( "Save Event to database: " + event );
        storageQueueProcessor.submit ( new Callable<Boolean> () {
            public Boolean call ()
            {
                try
                {
                    jdbcStorageDAO.get ().storeEvent ( MutableEvent.fromEvent ( eventToStore ) );
                    queueSize.decrementAndGet ();
                    if ( listener != null )
                    {
                        listener.notify ( eventToStore );
                    }
                }
                catch ( Exception e )
                {
                    logger.error ( "Exception occured ({}) while saving Event to database: {}", e, event );
                    logger.info ( "Exception was", e );
                    return false;
                }
                logger.debug ( "Event saved to database - remaining queue: {}, event: {}", queueSize.get (), event );
                return true;
            }
        } );
        return eventToStore;
    }

    private Event updateInternal ( final UUID id, final Variant comment, final StoreListener listener ) throws Exception
    {
        final MutableEvent eventToUpdate = getJdbcStorageDAO ().loadEvent ( id );
        eventToUpdate.getAttributes ().put ( Fields.COMMENT.getName (), comment );
        final Event event = MutableEvent.toEvent ( eventToUpdate );
        logger.debug ( "Update Event comment in database: " + event );
        storageQueueProcessor.submit ( new Callable<Boolean> () {
            public Boolean call ()
            {
                try
                {
                    jdbcStorageDAO.get ().storeEvent ( eventToUpdate );
                    if ( listener != null )
                    {
                        listener.notify ( event );
                    }
                }
                catch ( Exception e )
                {
                    logger.error ( "Exception occured ({}) while updating comment of Event in database: {}", e, event );
                    logger.info ( "Exception was", e );
                    return false;
                }
                logger.debug ( "Event updated in database: {}",  event );
                return true;
            }
        } );
        return event;
    }

    public Event update ( final UUID id, final String comment, final StoreListener listener ) throws Exception
    {
        return updateInternal ( id, new Variant (comment), listener );
    }
}