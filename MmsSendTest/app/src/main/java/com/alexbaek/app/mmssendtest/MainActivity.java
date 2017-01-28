package com.alexbaek.app.mmssendtest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.alexbaek.app.mmssendtest.util.PhoneEx;
import com.alexbaek.app.mmssendtest.util.nokia.IMMConstants;
import com.alexbaek.app.mmssendtest.util.nokia.MMContent;
import com.alexbaek.app.mmssendtest.util.nokia.MMEncoder;
import com.alexbaek.app.mmssendtest.util.nokia.MMMessage;
import com.alexbaek.app.mmssendtest.util.nokia.MMResponse;
import com.alexbaek.app.mmssendtest.util.nokia.MMSender;
import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;

public class MainActivity extends Activity {

    private static final String TAG = "SendMMSActivity";

    private ConnectivityManager mConnMgr;
    private PowerManager.WakeLock mWakeLock;
    private ConnectivityBroadcastReceiver mReceiver;

    private NetworkInfo mNetworkInfo;
    private NetworkInfo mOtherNetworkInfo;

    // 문자 발송 정보.
    private String MMSCenterUrl;
    private String MMSProxy;
    private int MMSPort;

    //
    private boolean mIsSending;         // MMS 발송중인지에 대한 여부.

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 초기화.
        mIsSending = false;

        // TODO. 통신사를 확인하여 APN정보 세팅.

        // LG U+ MMS Type APN정보 셋팅 (LG는 2015년 설정은 비공개)
//        MMSCenterUrl = "http://omammsc.uplus.co.kr:9084";
//        MMSProxy = "";
//        MMSPort = 9084;

        // SK LTE MMS Type APN정보 셋팅
        MMSCenterUrl = "http://omms.nate.com:9082/oma_mms";
        MMSProxy = "lteoma.nate.com";
        MMSPort = 9093;

        // 버튼 세팅.
        Button btnSendMMS = (Button) findViewById(R.id.btn_send_mms);
        btnSendMMS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMMS();
            }
        });

        // 퍼미션 체크.
        new TedPermission(this)
                .setPermissionListener(permissionlistener)
                .setDeniedMessage("If you reject permission,you can not use this service\n\nPlease turn on permissions at [Setting] > [Permission]")
                .setPermissions(Manifest.permission.READ_SMS,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.RECEIVE_MMS,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_PHONE_STATE)
                .check();
    }

    /**
     * 퍼미션 체크 Listener.
     */
    PermissionListener permissionlistener = new PermissionListener() {
        @Override
        public void onPermissionGranted() {
            // 퍼미션 확인.
        }

        @Override
        public void onPermissionDenied(ArrayList<String> deniedPermissions) {
            // 퍼미션 허용 안함.
        }
    };

    /**
     * Button Click시 MMS 발송.
     */
    private void sendMMS() {
        mIsSending = false;
        mConnMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        mReceiver = new ConnectivityBroadcastReceiver();

        // Receiver 등록.
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mReceiver, filter);
    }

    /**
     *
     */
    private class ConnectivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                // TODO. 해당 .
                Log.i(TAG, "ConnectivityBroadcastReceiver ===== onReceived()");
                return;
            }

            mNetworkInfo = (NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
            mOtherNetworkInfo = (NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_OTHER_NETWORK_INFO);

            if (!mNetworkInfo.isConnected()) {
                // TODO. 네트워크 연결되지 않았을때 팝업 처리.
                Log.v(TAG, "   TYPE_MOBILE_MMS not connected, bail");

                return;
            } else {
                Log.v(TAG, "connected..");

                if (mIsSending == false) {
                    mIsSending = true;
                    new Thread() {
                        public void run() {
                            sendMMSUsingNokiaAPI();
                        }
                    }.start();

                }
            }
        }
    }

    ;

    /**
     * MMS 발송 메시지 설정(Header)
     */
    private void setMMSHeader(MMMessage mm) {
        mm.setVersion(IMMConstants.MMS_VERSION_10);
        mm.setMessageType(IMMConstants.MESSAGE_TYPE_M_SEND_REQ);
        // TODO. Transction ID 값이 어떤것을 의미하는지 확인.
        mm.setTransactionId("0000000066");
        mm.setDate(new Date(System.currentTimeMillis()));

        // 착신자 번호 "-" 제거, 공백 제거(가운데 번호가 3자리일 경우)
        //mm.addToAddress(DEST_NUMBER.replaceAll("-", "").replaceAll(" ", "")+"/TYPE=PLMN");
        // 발신번호, 통신유형 PLMN,IPv4,IPv6

        mm.setFrom("01045150301/TYPE=PLMN");
        mm.addToAddress("01045150301/TYPE=PLMN");
        // 발신번호(+82 삭제, 보통 한국에서 보내는것처럼), 통신유형 PLMN,IPv4,IPv6

        //
        mm.setDeliveryReport(true);
        mm.setReadReply(false);
        // 번호를 감춤
        mm.setSenderVisibility(IMMConstants.SENDER_VISIBILITY_SHOW);

        mm.setSubject("MMS");  // 발송 메시지 제목
        mm.setMessageClass(IMMConstants.MESSAGE_CLASS_PERSONAL);        // 발송메시지 유형(정보성,광고,개인메시지)
        mm.setPriority(IMMConstants.PRIORITY_LOW);                      // 발송메시지 우선순위
        mm.setContentType(IMMConstants.CT_APPLICATION_MULTIPART_MIXED); // 첨부파일 포함유형
    }

    /**
     * MMS 발송 메시지 설정(Content)
     */
    private void setMMSContent(MMMessage mm) {
	    Bitmap image = BitmapFactory.decodeResource(MainActivity.this.getResources(), R.drawable.logo);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] bitmapdata = stream.toByteArray();

        // Adds text content
        MMContent part1 = new MMContent();
        byte[] buf1 = bitmapdata;
        part1.setContent(buf1, 0, buf1.length);
        part1.setContentId("<0>");
        part1.setType(IMMConstants.CT_IMAGE_PNG);
        mm.addContent(part1);
    }


    /**********************************************************************************************************************************
     * MMS Type 통신 시작
     ***********************************************************************************************************************************/
    protected int beginMmsConnectivity() throws IOException {
        // MMS 발송 진행중 휴대폰이 잠금상태가 되지 않도록 전원상태 On유지
        createWakeLock();

        // WIFI 모드 Check
        WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        boolean wifiEnabled = wifiManager.isWifiEnabled();
        // WIFI 모드일 경우 강제 Off
        if (wifiEnabled) {
            wifiManager.setWifiEnabled(false);
        }

        // 단말기 통신상태를  LTE ->MMS Type으로 전환
        int result = mConnMgr.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, PhoneEx.FEATURE_ENABLE_MMS);
        switch (result) {
            case PhoneEx.APN_ALREADY_ACTIVE:
            case PhoneEx.APN_REQUEST_STARTED:
                acquireWakeLock();
                return result;
        }
        throw new IOException("Cannot establish MMS connectivity");
    }

    private synchronized void createWakeLock() {
        // Create a new wake lock if we haven't made one yet.
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MMS Connectivity");
            mWakeLock.setReferenceCounted(false);
        }
    }

    private void acquireWakeLock() {
        // It's okay to double-acquire this because we are not using it
        // in reference-counted mode.
        mWakeLock.acquire();
    }

    private void releaseWakeLock() {
        // Don't release the wake lock if it hasn't been created and acquired.
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    protected void endMmsConnectivity() {
        // End the connectivity
        try {
            Log.v(TAG, "endMmsConnectivity");
            if (mConnMgr != null) {
                mConnMgr.stopUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, PhoneEx.FEATURE_ENABLE_MMS);
            }
        } finally {
            releaseWakeLock();
        }
    }

    /**********************************************************************************************************************************
     * MMS 발송 메시지 준비 및 발송
     ***********************************************************************************************************************************/
    private void sendMMSUsingNokiaAPI() {
        // MMS 전송 메시지 전송 설정준비

        MMMessage mm = new MMMessage();
        setMMSHeader(mm);  // MMS Header 설정
        setMMSContent(mm); // MMS 내용 세팅

        MMEncoder encoder = new MMEncoder();
        encoder.setMessage(mm);  // 메시지 인코딩
        try {
            encoder.encodeMessage();
            byte[] out = encoder.getMessage();
            // MMS 발송 객체
            MMSender sender = new MMSender();
            Boolean isProxySet = (MMSProxy != null);
            sender.setMMSCURL(MMSCenterUrl);
            sender.addHeader("X-NOKIA-MMSC-Charging", "100");
            // MMS 메시지 발송
            MMResponse mmResponse = sender.send(out, isProxySet, MMSProxy, MMSPort);
            // 응답상태 체크
            Log.d(TAG, "Message sent to " + sender.getMMSCURL());
            Log.d(TAG, "Response code: " + mmResponse.getResponseCode() + " " + mmResponse.getResponseMessage());
//            Iterator keys = (Iterator) mmResponse.getHeadersList();
//            while (keys.hasNext()) {
//                String key = (String) keys.next();
//                String value = (String) mmResponse.getHeaderValue(key);
//                Log.d(TAG, (key + ": " + value));
//            }

            // 응답코드 체크 200 OK
            if (mmResponse.getResponseCode() == 200) {
                // MMS 발송 관련 통신이 정상 완료후 프로세스
                endMmsConnectivity();
                Log.d("TAG", "releaseWakeLock");
            } else {
                Log.d("TAG", "kill");
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionAsStrting = sw.toString();

            Log.e("StackTraceExampleActivity", exceptionAsStrting);
        }
    }
}