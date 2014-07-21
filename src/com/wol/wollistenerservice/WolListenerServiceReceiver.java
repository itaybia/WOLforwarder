package com.wol.wollistenerservice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class WolListenerServiceReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
    	if(intent.getAction() != null)
    	{
    		if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED))
    		{
				Log.i("WolListenerServiceReceiver", "Starting WOL forwarder service");
    			context.startService(new Intent(context, WolListenerService.class));
    		}
    		if(intent.getAction().equals(Intent.ACTION_LOCALE_CHANGED))
    		{
				Log.i("WolListenerServiceReceiver", "Starting WOL forwarder service");
				context.startService(new Intent(context, WolListenerService.class));
    		}    		
    	}
    }
};