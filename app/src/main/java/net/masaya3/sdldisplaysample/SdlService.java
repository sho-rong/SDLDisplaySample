package net.masaya3.sdldisplaysample;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.smartdevicelink.managers.CompletionListener;
import com.smartdevicelink.managers.SdlManager;
import com.smartdevicelink.managers.SdlManagerListener;
import com.smartdevicelink.managers.file.filetypes.SdlArtwork;
import com.smartdevicelink.managers.screen.SoftButtonObject;
import com.smartdevicelink.managers.screen.SoftButtonState;
import com.smartdevicelink.protocol.enums.FunctionID;
import com.smartdevicelink.proxy.RPCNotification;
import com.smartdevicelink.proxy.RPCResponse;
import com.smartdevicelink.proxy.rpc.DisplayCapabilities;
import com.smartdevicelink.proxy.rpc.OnButtonEvent;
import com.smartdevicelink.proxy.rpc.OnButtonPress;
import com.smartdevicelink.proxy.rpc.OnHMIStatus;
import com.smartdevicelink.proxy.rpc.SetDisplayLayout;
import com.smartdevicelink.proxy.rpc.SetDisplayLayoutResponse;
import com.smartdevicelink.proxy.rpc.enums.AppHMIType;
import com.smartdevicelink.proxy.rpc.enums.FileType;
import com.smartdevicelink.proxy.rpc.enums.HMILevel;
import com.smartdevicelink.proxy.rpc.enums.PredefinedLayout;
import com.smartdevicelink.proxy.rpc.enums.SystemCapabilityType;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCResponseListener;
import com.smartdevicelink.transport.BaseTransportConfig;
import com.smartdevicelink.transport.MultiplexTransportConfig;
import com.smartdevicelink.transport.TCPTransportConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

public class SdlService extends Service {

	private static final String TAG 					= "SDL Service";

	private static final String APP_NAME 				= "SDL Display";
	private static final String APP_ID 					= "8678309";

	private static final String ICON_FILENAME 			= "hello_sdl_icon.png";

	private static final int FOREGROUND_SERVICE_ID = 111;

	// TCP/IP transport config
	// The default port is 12345
	// The IP is of the machine that is running SDL Core
	private static final int TCP_PORT = 12345;
	private static final String DEV_MACHINE_IP_ADDRESS = "192.168.1.105";

	// variable to create and call functions of the SyncProxy
	private SdlManager sdlManager = null;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		Log.d(TAG, "onCreate");
		super.onCreate();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			enterForeground();
		}
	}

	// Helper method to let the service enter foreground mode
	@SuppressLint("NewApi")
	public void enterForeground() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(APP_ID, "SdlService", NotificationManager.IMPORTANCE_DEFAULT);
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (notificationManager != null) {
				notificationManager.createNotificationChannel(channel);
				Notification serviceNotification = new Notification.Builder(this, channel.getId())
						.setContentTitle("Connected through SDL")
						.setSmallIcon(R.drawable.ic_sdl)
						.build();
				startForeground(FOREGROUND_SERVICE_ID, serviceNotification);
			}
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		startProxy();
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			stopForeground(true);
		}

		if (sdlManager != null) {
			sdlManager.dispose();
		}

		super.onDestroy();
	}

	private void startProxy() {

		if (sdlManager == null) {
			Log.i(TAG, "Starting SDL Proxy");

			BaseTransportConfig transport = null;
			if (BuildConfig.TRANSPORT.equals("MULTI")) {
				int securityLevel;
				if (BuildConfig.SECURITY.equals("HIGH")) {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_HIGH;
				} else if (BuildConfig.SECURITY.equals("MED")) {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_MED;
				} else if (BuildConfig.SECURITY.equals("LOW")) {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_LOW;
				} else {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF;
				}
				transport = new MultiplexTransportConfig(this, APP_ID, securityLevel);
			} else if (BuildConfig.TRANSPORT.equals("TCP")) {
				transport = new TCPTransportConfig(TCP_PORT, DEV_MACHINE_IP_ADDRESS, true);
			} else if (BuildConfig.TRANSPORT.equals("MULTI_HB")) {
				MultiplexTransportConfig mtc = new MultiplexTransportConfig(this, APP_ID, MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF);
				mtc.setRequiresHighBandwidth(true);
				transport = mtc;
			}

			// The app type to be used
			Vector<AppHMIType> appType = new Vector<>();
			appType.add(AppHMIType.MEDIA);


			// The manager listener helps you know when certain events that pertain to the SDL Manager happen
			// Here we will listen for ON_HMI_STATUS and ON_COMMAND notifications
			SdlManagerListener listener = new SdlManagerListener() {
				@Override
				public void onStart() {
					// HMI Status Listener
					sdlManager.addOnRPCNotificationListener(FunctionID.ON_HMI_STATUS, new OnRPCNotificationListener() {
						@Override
						public void onNotified(RPCNotification notification) {

							OnHMIStatus status = (OnHMIStatus) notification;
							if (status.getHmiLevel() == HMILevel.HMI_FULL && ((OnHMIStatus) notification).getFirstRun()) {

								checkTemplateType();

								setDisplayGraphicWithTextButtons();
							}
						}
					});
				}

				@Override
				public void onDestroy() {
					SdlService.this.stopSelf();
				}

				@Override
				public void onError(String info, Exception e) {
				}
			};

			// Create App Icon, this is set in the SdlManager builder
			SdlArtwork appIcon = new SdlArtwork(ICON_FILENAME, FileType.GRAPHIC_PNG, R.mipmap.ic_launcher, true);

			// The manager builder sets options for your session
			SdlManager.Builder builder = new SdlManager.Builder(this, APP_ID, APP_NAME, listener);
			builder.setAppTypes(appType);
			builder.setTransportType(transport);
			builder.setAppIcon(appIcon);
			sdlManager = builder.build();
			sdlManager.start();


		}
	}

	/**
	 * 利用可能なテンプレートをチェックする
	 */
	private void checkTemplateType(){

		Object result = sdlManager.getSystemCapabilityManager().getCapability(SystemCapabilityType.DISPLAY);
		if( result instanceof DisplayCapabilities){
			List<String> templates = ((DisplayCapabilities) result).getTemplatesAvailable();

			Log.i("Templete", templates.toString());

		}
	}

	/**
	 * GRAPHIC_WITH_TEXTBUTTONSテンプレートのサンプル
	 */
	private void setDisplayGraphicWithTextButtons(){
		SetDisplayLayout setDisplayLayoutRequest = new SetDisplayLayout();

		//GRAPHIC_WITH_TEXT_AND_SOFTBUTTONS
		setDisplayLayoutRequest.setDisplayLayout(PredefinedLayout.GRAPHIC_WITH_TEXTBUTTONS.toString());
		setDisplayLayoutRequest.setOnRPCResponseListener(new OnRPCResponseListener() {
			@Override
			public void onResponse(int correlationId, RPCResponse response) {
				if(((SetDisplayLayoutResponse) response).getSuccess()){
					Log.i("SdlService", "Display layout set successfully.");

					sdlManager.getScreenManager().beginTransaction();

					//テキストを登録する場合
					//sdlManager.getScreenManager().setTextField1("Hello, this is MainField1.");

					//画像を登録する
					SdlArtwork artwork = new SdlArtwork("sample01.png", FileType.GRAPHIC_PNG, R.drawable.sample01, true);

					sdlManager.getScreenManager().setPrimaryGraphic(artwork);
					sdlManager.getScreenManager().commit(new CompletionListener() {
						@Override
						public void onComplete(boolean success) {
							if (success) {
								Log.i(TAG, "welcome show successful");
							}
						}
					});

					//ボタンの設定
					SoftButtonState softButton01State1 = new SoftButtonState("button01_state1", "button01_state1", null);

					SdlArtwork state_artwork = new SdlArtwork("state2.png", FileType.GRAPHIC_PNG, R.drawable.ic_sdl, true);
					SoftButtonState softButton01State2 = new SoftButtonState("button01_state2", "button01_state2", state_artwork);

					List<SoftButtonState> softButtonStates = Arrays.asList(softButton01State1, softButton01State2);

					SoftButtonObject softButtonObject1 = new SoftButtonObject("softButtonObject01", softButtonStates, softButton01State1.getName(), null);

					softButtonObject1.setOnEventListener(new SoftButtonObject.OnEventListener() {
						@Override
						public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
							softButtonObject.transitionToNextState();
						}

						@Override
						public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

						}
					});

					SoftButtonState softButton02State1 = new SoftButtonState("button02_state1", "button02_state1", null);

					SoftButtonObject softButtonObject2 = new SoftButtonObject("softButtonObject02",  Collections.singletonList(softButton02State1), softButton02State1.getName(), null);
					softButtonObject2.setOnEventListener(new SoftButtonObject.OnEventListener() {
						@Override
						public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {

							sdlManager.getScreenManager().beginTransaction();

							//画像を登録する
							SdlArtwork artwork = new SdlArtwork("sample02.png", FileType.GRAPHIC_PNG, R.drawable.sample02, true);
							sdlManager.getScreenManager().setPrimaryGraphic(artwork);
							sdlManager.getScreenManager().commit(new CompletionListener() {
								@Override
								public void onComplete(boolean success) {
									if (success) {
										Log.i(TAG, "welcome show successful");
									}
								}
							});
						}

						@Override
						public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {
						}
					});

					List<SoftButtonObject> buttons = Arrays.asList(softButtonObject1, softButtonObject2);

					//ボタンを登録する
					sdlManager.getScreenManager().setSoftButtonObjects(buttons);

				}else{
					Log.i("SdlService", "Display layout request rejected.");
				}
			}
		});

		sdlManager.sendRPC(setDisplayLayoutRequest);
	}

}
