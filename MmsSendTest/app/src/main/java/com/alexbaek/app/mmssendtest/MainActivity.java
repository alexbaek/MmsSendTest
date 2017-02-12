package com.alexbaek.app.mmssendtest;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.alexbaek.app.mmssendtest.common.AppDefine;
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

    public static final int REQUEST_PERMISTION_RESULT = 20000;

    public static final int MMS_SEND_RESULT_SUCCESS = 50001;
    public static final int MMS_SEND_RESULT_FAIL = 50002;
    public static final int MMS_SEND_RESULT_ERROR = 50003;

    // 문자 발송 정보.
    private String MMSCenterUrl;
    private String MMSProxy;
    private int MMSPort;

    //
    private boolean mIsSending;         // MMS 발송중인지에 대한 여부.
    private Context mContext;

    public Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MMS_SEND_RESULT_SUCCESS:
                    Toast.makeText(mContext, "발송 성공", Toast.LENGTH_SHORT).show();
                    break;

                case MMS_SEND_RESULT_FAIL:
                    Toast.makeText(mContext, "발송 실패", Toast.LENGTH_SHORT).show();
                    break;

                case MMS_SEND_RESULT_ERROR:
                    Toast.makeText(mContext, "발송 도중 에러가 발생하였습니다.", Toast.LENGTH_SHORT).show();
                    break;
            }
            return false;
        }
    });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 초기화.
        mContext = this;
        mIsSending = false;

        // TODO. 통신사를 확인하여 APN정보 세팅.
        TelephonyManager telephonyManager =((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE));
        String operatorName = telephonyManager.getNetworkOperatorName();

        Log.d(TAG, "getNetworkOperatorName :: " + operatorName);

        if (operatorName == null) {
            // TODO. 통신 사업자가 없을때.
        } else if (operatorName.equals(AppDefine.NETWORK_OPERATOR_NAME_SKT)) {
            // SKT.
            MMSCenterUrl = "http://omms.nate.com:9082/oma_mms";
            MMSProxy = "lteoma.nate.com";
            MMSPort = 9093;
        } else if (operatorName.equals(AppDefine.NETWORK_OPERATOR_NAME_KT)
                || operatorName.equals(AppDefine.NETWORK_OPERATOR_NAME_KT_SUB)) {
            // KT
            MMSCenterUrl = "http://mmsc.ktfwing.com:9082";
            MMSProxy = "";
            MMSPort = 9093;
        } else if (operatorName.matches(AppDefine.NETWORK_OPERATOR_NAME_LGT)) {
            // LGT
            MMSCenterUrl = "http://omammsc.uplus.co.kr:9084";
            MMSProxy = "";
            MMSPort = 9084;
        }

        // 버튼 세팅.
        Button btnSendMMS = (Button) findViewById(R.id.btn_send_mms);
        btnSendMMS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMMS("", "", null);
            }
        });

        // 퍼미션 체크.
        checkAndroidPermission();
    }

    public boolean checkAndroidPermission(){

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> permissionList = new ArrayList<String>();
            if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.READ_SMS);
            }
            if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.SEND_SMS);
            }
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.RECEIVE_SMS);
            }
            if (checkSelfPermission(Manifest.permission.RECEIVE_MMS) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.RECEIVE_MMS);
            }
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.READ_PHONE_STATE);
            }

            if (permissionList.size() > 0) {
                ActivityCompat.requestPermissions(this, permissionList.toArray(new String[permissionList.size()]), REQUEST_PERMISTION_RESULT);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == REQUEST_PERMISTION_RESULT) {
            for(int i=0;i<permissions.length;i++) {

                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    // 퍼미션 세팅 팝업 생성.
                    //

                    AlertDialog.Builder alertdialog = new AlertDialog.Builder(this);
                    alertdialog.setMessage("안드로이드 권한을 주세요.");
                    alertdialog.setPositiveButton("확인", new DialogInterface.OnClickListener(){
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                            finish();
                        }
                    });
                    alertdialog.setNegativeButton("종료", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
                    AlertDialog alert = alertdialog.create();
                    alert.setTitle("안드로이드 퍼미션");
                    alert.show();

                    return;
                }
            }
        }
    }

    /**
     * Button Click시 MMS 발송.
     */
    private void sendMMS(String phoneNumber, String message, String fileName) {
        mIsSending = false;

        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (!networkInfo.isConnected()) {
            // TODO. 네트워크 연결되지 않았을때 팝업 처리.
            Log.v(TAG, "TYPE_MOBILE_MMS not connected, bail");
            return;
        }

        // WIFI 모드 Check
        WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        boolean wifiEnabled = wifiManager.isWifiEnabled();
        // WIFI 모드일 경우 강제 Off
        if (wifiEnabled) {
            wifiManager.setWifiEnabled(false);
        }

        if (mIsSending == false) {
            mIsSending = true;
            new Thread() {
                public void run() {
                    sendMMSUsingNokiaAPI();
                }
            }.start();

        }

    }

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

        mm.setFrom("01094984279/TYPE=PLMN");
        mm.addToAddress("01094984279/TYPE=PLMN");
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
        part1.setContentId("<1>");
        part1.setType(IMMConstants.CT_IMAGE_PNG);

        // 발송 문자 내역 첨부
        String content = "치성이형 바보";
        MMContent part2 = new MMContent();
        byte[] buf2 = new byte[]{};
        try {
            buf2 = content.getBytes("euc-kr");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        part2.setContent(buf2, 0, buf2.length);
        part2.setContentId("<0>");
        part2.setType(IMMConstants.CT_TEXT_PLAIN + "; charset=\"euc-kr\";");


        mm.addContent(part2);
        mm.addContent(part1);
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

            // 응답코드 체크 200 OK
            if (mmResponse.getResponseCode() == 200) {
                // 발송 Toast 생성.
                handler.sendEmptyMessage(MMS_SEND_RESULT_SUCCESS);

                // MMS 발송 관련 통신이 정상 완료후 프로세스
                Log.d("TAG", "releaseWakeLock");
            } else {
                handler.sendEmptyMessage(MMS_SEND_RESULT_FAIL);

                Log.d("TAG", "kill");
            }
        } catch (Exception e) {
            handler.sendEmptyMessage(MMS_SEND_RESULT_ERROR);


            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionAsStrting = sw.toString();

            Log.e("StackTraceExampleActivity", exceptionAsStrting);
        }
    }
}