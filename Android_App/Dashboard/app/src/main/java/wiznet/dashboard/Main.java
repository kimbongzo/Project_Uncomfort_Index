package wiznet.dashboard;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import me.itangqi.waveloadingview.WaveLoadingView;

public class  Main extends AppCompatActivity implements View.OnClickListener{

    private Thread connectionThread = null;
    private CountDownTimer countDownTimer;
    private boolean once;
    String TAG = "daniel";
    private Handler mHandler;
    private static Socket socket = null;
    private byte[] readmsg;
    private byte d_onstatus = (byte)0x01;
    private byte d_offstatus = (byte)0x00;
    private byte g_recv_status = d_offstatus;

    private byte g_current_status = d_offstatus;
    private boolean g_auto = false;

    String device_on_msg = "8801010001000000000055";
    String device_off_msg = "8801010000000000000055";

    private Handler autoHandler = null;
    private String ip = "192.168.1.19";
    //    private String ip = "222.98.173.194";
    private int port = 5000;

    //    10초동안 데이터 수신이 없을 시 Disconnect로 간주하기 위한 Timer
    private long g_wait_time = 10 * 1000;


    private WaveLoadingView mWaveSensor1;

    private Button button_power;
    private Button button_auto;

    private TextView s1_value;
    private TextView s2_value;
    private TextView s3_value;

    private ImageView img_iscon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mWaveSensor1 = (WaveLoadingView) findViewById(R.id.wave_uncomfort);

        button_power = (Button) findViewById(R.id.button_power);
        button_auto = (Button) findViewById(R.id.button_auto);
        img_iscon = (ImageView) findViewById(R.id.is_con);

        button_power.setOnClickListener(this);
        button_auto.setOnClickListener(this);
        img_iscon.setOnClickListener(this);

        s1_value = (TextView) findViewById(R.id.temp);
        s2_value = (TextView) findViewById(R.id.humi);
        s3_value = (TextView) findViewById(R.id.illu);

        mWaveSensor1.setShapeType(WaveLoadingView.ShapeType.CIRCLE);
        mWaveSensor1.setTopTitle("UnComfort Index");
        mWaveSensor1.setCenterTitle("Connecting..");
        mWaveSensor1.setAmplitudeRatio(20);
        mWaveSensor1.setTopTitleColor(Color.parseColor("#FF4081"));
        mWaveSensor1.setCenterTitleColor(Color.parseColor("#FF4081"));
       // mWaveSensor1.setBottomTitle("Bottom Title");
       // mWaveSensor1.setProgressValue(50);
     //   mWaveSensor1.setBorderWidth(2);

        //mWaveSensor1.setWaveColor(2);
        //mWaveSensor1.setBorderColor(color);

        updateButtonState(button_power, false);
        updateButtonState(button_auto, false);
        mHandler = new Handler();
        once = true;
       // Toast.makeText(getApplicationContext(), "연결 시도중...", Toast.LENGTH_SHORT).show();
        // connection
        Connect();

        if(countDownTimer!=null){
            countDownTimer.cancel();
        }
        countDownTimer = new CountDownTimer(g_wait_time, 1000) {
            @Override
            public void onFinish()
            {
                //Message를 수신 후 10초동안 아무런 Message가 없을 때 DisConnect 이미지 출력(실제 소켓과 무관)
                if(socket != null)
                    img_iscon.setImageResource(R.drawable.connect);
                else
                    img_iscon.setImageResource(R.drawable.disconnect);
            }
            @Override
            public void onTick(long millisUntilFinished) {
                //1초(Tick)마다 수행하는 함수
            }
        };

    }


    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            //ON/OFF Button
            case R.id.button_power:

                byte[] temp;
                Log.d(TAG,"onClick");
                if(g_recv_status == d_offstatus) {
                    temp = hexStringToByteArray(device_on_msg);
                    Log.d(TAG,"power on");
                } else {
                    temp = hexStringToByteArray(device_off_msg);
                    Log.d(TAG,"power off");
                }

                try {
                    sendBytes(temp);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            // Auto Button
            case R.id.button_auto:

                if( g_auto ) {
                    updateButtonState(button_auto, false);
                    if(autoHandler != null) {
                        autoHandler.removeCallbacks(auto_run);
                        autoHandler = null;
                    }

                } else {

                    updateButtonState(button_auto, true);
                    if(autoHandler == null){
                        autoHandler = new Handler();
                        autoHandler.post(auto_run);
                    }
                }
                break;


            // Connection Button
            case R.id.is_con:

                if( socket == null )
                    Connect();
                else
                    Disconnect();
                break;

            default:
                break;
        }
    }

    private void updateButtonState(Button tb, boolean state){

        if( state ){
            tb.setBackgroundResource(R.drawable.custom_button_on);
            tb.setTextColor(Color.parseColor("#ffffff"));

            if(tb == button_power)
                tb.setText("O N");
            else if(tb == button_auto)
                g_auto = state;

        } else {
            tb.setBackgroundResource(R.drawable.custom_button_off);
            tb.setTextColor(Color.parseColor("#006E90"));

            if(tb == button_power)
                tb.setText("OFF");
            else if(tb == button_auto)
                g_auto = state;

        }
    }

    private void updateView(int color){

        mWaveSensor1.setWaveColor(color);
        mWaveSensor1.setBorderColor(color);
        mWaveSensor1.setCenterTitleColor(Color.parseColor("#161032"));
    }

    /*   byteArray를 OutputStream에 전송한다.   */
    private void sendBytes(byte[] byteArray) throws IOException {

        try {
            if (socket.isConnected()) {
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                dos.write(byteArray);                          // byte array를 TCP Send (output stream에 전송)
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

    }

    /*   InputStream에서 11byte를 읽는다   */
    public byte[] readBytes() throws IOException {

        if (socket.isConnected()) {
            DataInputStream dis = new DataInputStream(socket.getInputStream());

            byte[] data = new byte[11];
            dis.readFully(data);                 // readyFully-> buffer array 사이즈만큼 바이트를 input stream에서 읽어옴(Reads some bytes from an input stream and stores them into the buffer array b)

            if (data[0] != (byte) 0x88)           // 11byte를 읽었얼 때 start byte가 0x88이 아니면 input stream의 남아있는 데이터를 버림.
                while (dis.available() > 0)
                    dis.readByte();
            return data;
        }
        return null;
    }


    public void Disconnect() {
        try{
            if( socket != null) {
                checkUpdate.isInterrupted();           // CheckUpdate Thread 종료시켜주는 interrupt. 안되는 경우도 있음.
                socket.close();
                socket = null;
            }

        } catch (Exception e){
            Log.d(TAG, "disconnect error");
        }
    }

    public void Connect() {
        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                try {
                    if( socket == null) {
                        socket = new Socket(ip, port);          // TCP Socket 생성
                    }
                } catch (Exception e) {
                    Log.d(TAG, "SetConnection " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    if(once) {
                        checkUpdate.start();   // 프로그램이 시작하면 receive 스레드는 단 한번만 실행되서 앱이 종료될때까지 샐행된다.
                        once = false;
                    }
                }
            }
        };

        connectionThread = new Thread(runnable);
        connectionThread.start();
    }

    /*     TCP Recieve Thread, 무한 루프      */
    private Thread checkUpdate = new Thread() {

        public void run() {

            while(true) {

                try {
                    if (socket != null) {
                        readmsg = readBytes();             // 11byte 읽어옴

                        if (readmsg[0] == (byte) 0x88 && readmsg[10] == (byte) 0x55) {
                            countDownTimer.cancel();
                            countDownTimer.start();
                            mHandler.post(showUpdate);             // 정상적인 11byte로 화면 업데이트
                        } else {
                            readmsg = null;
                        }
                    }

//                    if (checkUpdate.currentThread().isInterrupted())
//                        break;
                } catch (Exception e) {
                    Log.d(TAG, "update fail " + e);
                }



            }
//            Log.d(TAG, "thread dead");
        }
    };

    /*     화면 업데이트      */
    private Runnable showUpdate = new Runnable() {

        public void run() {

            if( readmsg != null )
            {
                int color = 0;
                float discomfort;
                int illumination = ((readmsg[5] & 0xFF) << 8) + ((readmsg[6] & 0xFF) << 0);   // byte -> int
                float humidity =  (float)(((readmsg[7] & 0xFF) << 8) + ((readmsg[8] & 0xFF) << 0));
                float tempeature = (float)(readmsg[9] & 0xFF);

                g_recv_status = readmsg[4];
                img_iscon.setImageResource(R.drawable.received);

                discomfort = (float) (1.8 * tempeature-0.55*(1- humidity/100) * (1.8*tempeature-26) + 32);

                if( discomfort <= 75 ) {
                        color = Color.parseColor("#016FB9");
                }
                else if( discomfort > 75 && discomfort <= 80 ) {
                        color = Color.parseColor("#FF9505");
                }
                else if(discomfort > 80 ) {
                        color = Color.parseColor("#EC4E20");
                }

                if( color != 0 )
                    updateView(color);

                if( g_recv_status == d_onstatus ) {
                    g_current_status = g_recv_status;
                    updateButtonState(button_power, true);
                } else {
                    g_current_status = g_recv_status;
                    updateButtonState(button_power, false);
                }

                mWaveSensor1.setProgressValue((int) discomfort);
                String dc_value_str = String.format("%.2f", discomfort);
                mWaveSensor1.setCenterTitle(dc_value_str + " %");

                //String s1_value_str = String.format("%.1f", discomfort);
                s1_value.setText(Float.toString(tempeature));
                s2_value.setText(Float.toString(humidity));
                s3_value.setText(Integer.toString(illumination));

            }
        }
    };


    /*     센서값이 임계치 이상일 때, 자동으로 COMMAND를 서버로 전송     */
    private Runnable auto_run = new Runnable() {
        public void run() {
            byte[] temp;
            Log.d(TAG, "auto_run " + g_current_status);

            int sensor1_value = mWaveSensor1.getProgressValue();

            Log.d(TAG,"value " + sensor1_value );
            if( sensor1_value >= 80 && g_current_status == d_offstatus ){

                temp = hexStringToByteArray(device_on_msg);
                Log.d(TAG,"power button - on");

                try {
                    sendBytes(temp);                             //  Send Byte Stream
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (sensor1_value <= 70 && g_current_status == d_onstatus){
                temp = hexStringToByteArray(device_off_msg);
                Log.d(TAG,"power button - off");

                try {
                    sendBytes(temp);                             //  Send Byte Stream
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            autoHandler.postDelayed(this, 1000);               // 1초 후 auto_run 재귀
        }
    };

    /*     Utility      */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

}

