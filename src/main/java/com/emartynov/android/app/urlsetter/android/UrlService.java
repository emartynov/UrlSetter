/*
 * Copyright 2013 Eugen Martynov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.emartynov.android.app.urlsetter.android;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.text.format.DateUtils;
import android.widget.Toast;

import com.emartynov.android.app.urlsetter.R;
import com.emartynov.android.app.urlsetter.model.UrlResolver;
import com.emartynov.android.app.urlsetter.model.event.DownloadingError;
import com.emartynov.android.app.urlsetter.model.event.FoundURL;
import com.emartynov.android.app.urlsetter.model.event.ResolveFacebookURL;
import com.emartynov.android.app.urlsetter.model.event.ResolveURL;
import com.emartynov.android.app.urlsetter.model.event.UrlEvent;
import com.emartynov.android.app.urlsetter.service.Crashlytics;
import com.emartynov.android.app.urlsetter.service.Mixpanel;
import com.jakewharton.disklrucache.DiskLruCache;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import javax.inject.Inject;

public class UrlService extends Service
{
    public static final long TIMEOUT_IN_SECONDS = 5 * DateUtils.SECOND_IN_MILLIS;
    private static final String FACEBOOK_HOST = "m.facebook.com";

    @Inject
    UrlResolver urlResolver;
    @Inject
    Bus bus;
    @Inject
    Mixpanel logger;
    @Inject
    DiskLruCache cache;
    @Inject
    Crashlytics crashlytics;

    private Timer timer;
    private Handler handler;

    @Override
    public void onCreate ()
    {
        ( (UrlApplication) getApplication() ).inject( this );

        crashlytics.start( this );

        bus.register( this );
        logger.init( this );
    }

    @Subscribe
    public void resolveUrl ( ResolveURL event )
    {
        createLongOperationTimer();

        showToastOnUI( getString( R.string.resolving_url, event.getUri() ) );
    }

    @Override
    public IBinder onBind ( Intent intent )
    {
        return null;
    }

    @Override
    public int onStartCommand ( Intent intent, int flags, int startId )
    {
        handler = new Handler();

        if ( intent != null )
        {
            resolveUrl( intent.getData() );
        }

        return START_NOT_STICKY;
    }

    private void resolveUrl ( Uri uri )
    {
        if ( FACEBOOK_HOST.equals( uri.getHost() ) )
        {
            getFromCacheOrResolve( new ResolveFacebookURL( uri ) );
        }
        else
        {
            getFromCacheOrResolve( new ResolveURL( uri ) );
        }
    }

    private void getFromCacheOrResolve ( UrlEvent event )
    {
        String key = getUriKey( event.getUri() );

        try
        {
            DiskLruCache.Snapshot snapshot = cache.get( key );
            if ( snapshot == null )
            {
                bus.post( event );
            }
            else
            {
                Uri resolvedUri = Uri.parse( snapshot.getString( 0 ) );
                bus.post( new FoundURL( event.getUri(), resolvedUri ) );
            }
        }
        catch ( IOException e )
        {
            bus.post( event );
        }
    }

    private String getUriKey ( Uri uri )
    {
        String key = String.valueOf( uri.hashCode() );
        return key.length() > 64 ? key.substring( 0, 64 ) : key;
    }

    private void createLongOperationTimer ()
    {
        cancelTimer();

        timer = new Timer();
        timer.schedule( new TimerTask()
        {
            @Override
            public void run ()
            {
                showToastOnUI( getString( R.string.operation_takes_longer ) );
            }
        }, TIMEOUT_IN_SECONDS );
    }

    private void showToastOnUI ( final String toastText )
    {
        runOnUiThread( new Runnable()
        {
            @Override
            public void run ()
            {
                showToast( toastText );
            }
        } );
    }

    private void runOnUiThread ( Runnable runnable )
    {
        handler.post( runnable );
    }

    private void showToast ( String toastText )
    {
        Toast toast = Toast.makeText( this, toastText, Toast.LENGTH_SHORT );
        toast.show();
    }

    @Override
    public void onDestroy ()
    {
        super.onDestroy();

        bus.unregister( this );

        logger.flush();
    }

    @Subscribe
    public void launchURL ( FoundURL event )
    {
        cancelTimer();

        showUri( event.getResolvedUri() );

        logger.trackEvent( "Resolved", event.getLoggingParams() );

        cacheUri( event );
    }

    private void cacheUri ( FoundURL event )
    {
        try
        {
            cache.edit( getUriKey( event.getUri() ) ).set( 0, event.getResolvedUri().toString() );
        }
        catch ( IOException ignored )
        {
        }
    }

    private void showUri ( Uri uri )
    {
        Intent intent = new Intent( Intent.ACTION_VIEW );
        intent.setData( uri );
        intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK );

        startActivity( intent );

        checkToStop();
    }

    private void checkToStop ()
    {
        if ( urlResolver.isIdle() )
        {
            stopSelf();
        }
    }

    private void cancelTimer ()
    {
        if ( timer != null )
        {
            timer.cancel();
        }
    }

    @Subscribe
    public void downloadError ( final DownloadingError event )
    {
        cancelTimer();

        crashlytics.logException( event.getException() );

        String errorString = getString( R.string.error_while_resolving_url, event.getException() );

        showToastOnUI( errorString );

        showUri( event.getUri() );

        logger.trackEvent( "Error", event.getLoggingParams() );

        checkToStop();
    }
}
