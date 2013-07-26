package org.openscada.mqtt;

import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttDefaultFilePersistence;
import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.openscada.ca.ConfigurationDataHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttBrokerImpl implements MqttBroker, MqttCallback
{
    private static final Logger logger = LoggerFactory.getLogger ( MqttBrokerImpl.class );

    private static final String defaultClientId = MqttClient.generateClientId ();

    private final ExecutorService executor;

    private String clientId;

    private URI uri;

    private File persistencePath;

    private Character delimiter = '.';

    private String prefix;

    private String writeSuffix;

    private String username;

    private String password;

    private NameConverter itemToTopicConverter;

    private NameConverter topicToItemConverter;

    private Future<MqttClient> startClientFuture;

    private MqttClient client;

    private final ConcurrentMap<String, Set<TopicListener>> topicListeners = new ConcurrentHashMap<String, Set<TopicListener>> ();

    public MqttBrokerImpl ( final ExecutorService executor )
    {
        this.executor = executor;
    }

    public void update ( final Map<String, String> parameters )
    {
        logger.trace ( "update called" );
        stopClient ();
        parseConfig ( parameters );
        startClient ();

    }

    @Override
    public MqttClient getClient ()
    {
        if ( this.startClientFuture == null )
        {
            return null;
        }
        try
        {
            return this.startClientFuture.get ( 30, TimeUnit.SECONDS );
        }
        catch ( final Exception e )
        {
            logger.error ( "could not get MQTT client", e );
            return null;
        }
    }

    public void dispose ()
    {
        stopClient ();
    }

    public Character getDelimiter ()
    {
        return this.delimiter;
    }

    public String getPrefix ()
    {
        return this.prefix;
    }

    public String getWriteValueSuffix ()
    {
        return this.writeSuffix;
    }

    @Override
    public NameConverter getItemToTopicConverter ()
    {
        return this.itemToTopicConverter;
    }

    @Override
    public NameConverter getTopicToItemConverter ()
    {
        return this.topicToItemConverter;
    }

    private void startClient ()
    {
        logger.trace ( "startClient () called" );
        this.startClientFuture = this.executor.submit ( new Callable<MqttClient> () {
            @Override
            public MqttClient call () throws Exception
            {
                logger.trace ( "starting client" );
                // check parameters
                final String effectiveClientId = MqttBrokerImpl.this.clientId == null ? getDefaultClientId () : MqttBrokerImpl.this.clientId;
                logger.info ( "using clientId {}", effectiveClientId );
                final URI effectiveUri = MqttBrokerImpl.this.uri == null ? getDefaultUri () : MqttBrokerImpl.this.uri;
                logger.info ( "using URI {}", effectiveUri );
                final File effectivePersistencePath = MqttBrokerImpl.this.persistencePath == null ? getDefaultPersistencePath () : MqttBrokerImpl.this.persistencePath;
                logger.info ( "using persistence path {}", effectivePersistencePath );

                // set up setting
                if ( effectivePersistencePath.mkdirs () )
                {
                    logger.info ( "created mqtt persistence directory {}", effectivePersistencePath );
                }
                final MqttConnectOptions options = new MqttConnectOptions ();

                // start client
                logger.debug ( "starting MQTT client" );
                MqttBrokerImpl.this.client = null;
                try
                {
                    options.setUserName ( MqttBrokerImpl.this.username );
                    options.setPassword ( MqttBrokerImpl.this.password.toCharArray () );
                    MqttBrokerImpl.this.client = new MqttClient ( effectiveUri.toString (), effectiveClientId, new MqttDefaultFilePersistence ( effectivePersistencePath.getAbsolutePath () ) );
                    MqttBrokerImpl.this.client.setCallback ( MqttBrokerImpl.this );
                    logger.trace ( "connecting client" );
                    MqttBrokerImpl.this.client.connect ( options );
                }
                catch ( final MqttException e )
                {
                    logger.error ( "failed to start MQTT client", e );
                }

                return MqttBrokerImpl.this.client;
            }
        } );
    }

    private void stopClient ()
    {
        logger.trace ( "stopClient () called" );
        this.executor.submit ( new Callable<MqttClient> () {
            @Override
            public MqttClient call () throws Exception
            {
                logger.trace ( "stopping client" );
                if ( MqttBrokerImpl.this.client == null )
                {
                    logger.trace ( "client itself is null" );
                    return null;
                }
                logger.trace ( "disconnect client" );
                MqttBrokerImpl.this.client.disconnect ( TimeUnit.SECONDS.toMillis ( 30 ) );
                logger.trace ( "clear listeners" );
                MqttBrokerImpl.this.topicListeners.clear ();
                return MqttBrokerImpl.this.client;
            };
        } );
    }

    private void parseConfig ( final Map<String, String> parameters )
    {
        logger.trace ( "parseConfig () called" );
        this.executor.submit ( new Callable<Void> () {
            @Override
            public Void call () throws Exception
            {
                logger.trace ( "parsing config" );
                final ConfigurationDataHelper cfg = new ConfigurationDataHelper ( parameters );
                if ( cfg.getString ( "clientId" ) != null )
                {
                    MqttBrokerImpl.this.clientId = cfg.getString ( "clientId" );
                }
                if ( cfg.getString ( "uri" ) != null )
                {
                    MqttBrokerImpl.this.uri = URI.create ( cfg.getString ( "uri" ) );
                }
                if ( cfg.getString ( "persistencePath" ) != null )
                {
                    MqttBrokerImpl.this.persistencePath = new File ( cfg.getString ( "persistencePath" ) );
                }
                if ( cfg.getString ( "delimiter" ) != null )
                {
                    MqttBrokerImpl.this.delimiter = cfg.getString ( "delimiter", "." ).charAt ( 0 );
                }
                if ( cfg.getString ( "prefix" ) != null )
                {
                    MqttBrokerImpl.this.prefix = cfg.getString ( "prefix" );
                }
                MqttBrokerImpl.this.writeSuffix = cfg.getString ( "writeSuffix", "$write" );
                MqttBrokerImpl.this.username = cfg.getString ( "username", "mqtt" );
                MqttBrokerImpl.this.password = cfg.getString ( "password", "" );

                MqttBrokerImpl.this.itemToTopicConverter = new ItemToTopicConverter ( MqttBrokerImpl.this.delimiter, MqttBrokerImpl.this.prefix, MqttBrokerImpl.this.writeSuffix );
                MqttBrokerImpl.this.topicToItemConverter = new TopicToItemConverter ( MqttBrokerImpl.this.delimiter, MqttBrokerImpl.this.prefix, MqttBrokerImpl.this.writeSuffix );

                logger.trace ( "clientId = {}", MqttBrokerImpl.this.clientId );
                logger.trace ( "uri = {}", MqttBrokerImpl.this.uri );
                logger.trace ( "persistencePath = {}", MqttBrokerImpl.this.persistencePath );
                logger.trace ( "delimiter = {}", MqttBrokerImpl.this.delimiter );
                logger.trace ( "prefix = {}", MqttBrokerImpl.this.prefix );
                logger.trace ( "writeSuffix = {}", MqttBrokerImpl.this.writeSuffix );
                logger.trace ( "username = {}", MqttBrokerImpl.this.username );
                logger.trace ( "password = {}", ( MqttBrokerImpl.this.password == null ? "null" : "********" ) );
                return null;
            };
        } );
    }

    /**
     * @return default clientId given through system property
     */
    private String getDefaultClientId ()
    {
        return System.getProperty ( "org.openscada.mqtt.clientId", defaultClientId );
    }

    /**
     * @return default persistence property given through system property
     */
    private File getDefaultPersistencePath ()
    {
        return new File ( System.getProperty ( "org.openscada.mqtt.persistence", System.getProperty ( "user.home" ) + File.separator + ".openscada" + File.separator + "mqtt" ) );
    }

    /**
     * @return default mqtt uri given through system property
     */
    private URI getDefaultUri ()
    {
        return URI.create ( System.getProperty ( "org.openscada.mqtt.uri", "tcp://localhost:1883" ) );
    }

    @Override
    public void connectionLost ( final Throwable th )
    {
        logger.warn ( "connection lost", th );
        for ( final Set<TopicListener> listeners : this.topicListeners.values () )
        {
            for ( final TopicListener listener : listeners )
            {
                listener.connectionLost ( th );
            }
        }

    }

    @Override
    public void deliveryComplete ( final MqttDeliveryToken token )
    {
        // ignore for now
    }

    @Override
    public void messageArrived ( final MqttTopic topic, final MqttMessage message ) throws Exception
    {
        logger.trace ( "received message {} on topic {}", message, topic );
        final Set<TopicListener> listeners = this.topicListeners.get ( topic.getName () );
        if ( listeners != null )
        {
            for ( final TopicListener listener : listeners )
            {
                listener.update ( message.getPayload (), message.isDuplicate () );
            }
        }

    }

    @Override
    public void addListener ( final String topic, final TopicListener listener )
    {
        logger.trace ( "addListener () called with topic '{}' and listener '{}'", topic, listener );
        this.topicListeners.putIfAbsent ( topic, new ConcurrentSkipListSet<TopicListener> () );
        this.topicListeners.get ( topic ).add ( listener );
        if ( this.startClientFuture != null )
        {
            try
            {
                this.startClientFuture.get ( 30, TimeUnit.SECONDS ).subscribe ( topic );
            }
            catch ( final Exception e )
            {
                logger.error ( "could not subscribe to topic {}", topic, e );
            }
        }
    }

    @Override
    public void removeListener ( final String topic, final TopicListener listener )
    {
        logger.trace ( "removeListener () called with topic '{}' and listener '{}'", topic, listener );
        final Set<TopicListener> listeners = this.topicListeners.get ( topic );
        if ( listeners != null )
        {
            listeners.remove ( listener );
        }
        if ( ( listeners == null ) || listeners.isEmpty () )
        {
            try
            {
                this.startClientFuture.get ( 30, TimeUnit.SECONDS ).unsubscribe ( topic );
            }
            catch ( final Exception e )
            {
                logger.error ( "could not unsubscribe from topic {}", topic, e );
            }
        }
    }
}
