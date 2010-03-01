package org.openscada.spring.client;

import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.log4j.Logger;
import org.openscada.core.InvalidOperationException;
import org.openscada.core.NotConvertableException;
import org.openscada.core.Variant;
import org.openscada.core.VariantType;
import org.openscada.core.server.common.session.UserSession;
import org.openscada.da.core.DataItemInformation;
import org.openscada.da.core.WriteResult;
import org.openscada.da.server.common.AttributeMode;
import org.openscada.da.server.common.chain.DataItemInputOutputChained;
import org.openscada.utils.concurrent.DirectExecutor;
import org.openscada.utils.concurrent.FutureTask;
import org.openscada.utils.concurrent.NotifyFuture;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

/**
 * @author Juergen Rose &lt;juergen.rose@inavare.net&gt;
 *
 */
public class DataItemInputOutputProperty extends DataItemInputOutputChained implements InitializingBean
{
    private static Logger logger = Logger.getLogger ( DataItemInputOutputProperty.class );

    private Object bean;

    private String property;

    /**
     * @param di
     */
    public DataItemInputOutputProperty ( final DataItemInformation di, final Executor executor )
    {
        super ( di, executor );

    }

    /**
     * @param id item id
     */
    public DataItemInputOutputProperty ( final String id, final Executor executor )
    {
        super ( id, executor );

    }

    /**
     * will create a dataitem with a sync executor set
     * @param id the item id
     * @deprecated use one of the constructors with the executor
     */
    public DataItemInputOutputProperty ( final String id )
    {
        super ( id, DirectExecutor.INSTANCE );
    }

    public void afterPropertiesSet () throws Exception
    {
        // contract
        Assert.notNull ( this.bean, "'bean' must not be null" );
        Assert.notNull ( this.property, "'property' must not be null" );
        Assert.isTrue ( PropertyUtils.isReadable ( this.bean, this.property ) );
        Assert.isTrue ( PropertyUtils.isWriteable ( this.bean, this.property ) );
        final Object value = PropertyUtils.getProperty ( this.bean, this.property );
        updateData ( new Variant ( value ), new HashMap<String, Variant> (), AttributeMode.SET );
        notifyData ( new Variant ( value ), new HashMap<String, Variant> (), true );
    }

    @Override
    protected NotifyFuture<WriteResult> startWriteCalculatedValue ( final UserSession session, final Variant value )
    {
        final FutureTask<WriteResult> task = new FutureTask<WriteResult> ( new Callable<WriteResult> () {

            public WriteResult call () throws Exception
            {
                processWriteCalculatedValue ( value );
                return new WriteResult ();
            }
        } );
        this.executor.execute ( task );
        return task;
    }

    protected void processWriteCalculatedValue ( final Variant value ) throws NotConvertableException, InvalidOperationException
    {
        try
        {
            if ( VariantType.NULL.equals ( value.getType () ) )
            {
                BeanUtils.setProperty ( value, this.property, null );
            }
            else
            {
                BeanUtils.setProperty ( value, this.property, value.asString () );
            }
            updateData ( value, new HashMap<String, Variant> (), AttributeMode.UPDATE );
            notifyData ( value, new HashMap<String, Variant> (), false );
        }
        catch ( final Throwable throwable )
        {
            logger.info ( "value could not be set for property " + this.property + " to " + value );
            logger.info ( throwable.getMessage () );
        }
    }

    /**
     * @return
     */
    public Object getBean ()
    {
        return this.bean;
    }

    /**
     * @param bean
     */
    public void setBean ( final Object bean )
    {
        this.bean = bean;
    }

    /**
     * @return
     */
    public String getProperty ()
    {
        return this.property;
    }

    /**
     * @param property
     */
    public void setProperty ( final String property )
    {
        this.property = property;
    }
}