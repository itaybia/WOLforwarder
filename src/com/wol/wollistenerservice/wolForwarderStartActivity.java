package com.wol.wollistenerservice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class wolForwarderStartActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);

		startService(new Intent(this, WolListenerService.class));
	}
	
	@Override
	public void onBackPressed() {
		// TODO Auto-generated method stub
		super.onBackPressed();
		finish();
	}
}
