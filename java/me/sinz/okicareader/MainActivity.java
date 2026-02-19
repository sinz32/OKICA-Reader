package me.sinz.okicareader;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcF;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private NfcAdapter adapter;
    private PendingIntent intent;
    private TextView txt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        txt = new TextView(this);
        txt.setText("Enable NFC and scan OKICA");
        txt.setGravity(Gravity.CENTER);
        layout.addView(txt);
        int pad = dip2px(this, 16);
        layout.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);

        adapter = NfcAdapter.getDefaultAdapter(this);
        this.intent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
    }

    private void readOkica(NfcF nf, byte[] IDm) {
        try {
            String sc = b2s(nf.getSystemCode());
            if (!sc.equals("8FC1")) {  //OKICA's system code is 0x8FC1
                txt.setText("Scanned card is not OKICA");
                return;
            }

            nf.connect();
            nf.setTimeout(500);

            byte[] cmd = s2b("1006" + b2s(IDm) + "018F02018000");
            //10 06 IDm 018F 02 01 8000"
            //10 - data length
            //06 - Read Without Encryption command
            //IDm - Card's IDm
            //018F - service code for OKICA history (little endian). Only this value is different from Suica.
            //01 - block count to be read. I will read only 1 block(usage history).
            //     Up to 20 usage records are stored in card, but lots of Many apps read 10 at a time, twice.
            //80 - Block element high byte
            //00 - block number
            byte[] res = nf.transceive(cmd);
            int balance = toInt(res, 13, 11, 10);
            txt.setText(balance + "円");

        } catch (Exception e) {
            toast(e.toString());
        }
    }

    private int toInt(byte[] res, int offset, int... index) {
        int num = 0;
        for (int i : index) {
            num = num << 8;
            num += ((int) res[offset + i]) & 0x0ff;
        }
        return num;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        try {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag == null) return;

            NfcF nf = NfcF.get(tag);
            if (nf == null) return;
            byte[] id = tag.getId();
            if (id != null) readOkica(nf, id);

        } catch (Exception e) {
            toast(e.toString());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) adapter.enableForegroundDispatch(this, intent, null, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (adapter != null) adapter.disableForegroundDispatch(this);
    }

    private String b2s(byte[] bytes) {
        char[] hex = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int n = 0; n < bytes.length; n++) {
            int v = bytes[n] & 0xFF;
            hexChars[n * 2] = hex[v >>> 4];
            hexChars[n * 2 + 1] = hex[v & 0x0F];
        }
        return new String(hexChars);
    }

    private byte[] s2b(String str) {
        int length = str.length();
        byte[] bytes = new byte[length / 2];
        for (int n = 0; n < length; n += 2) {
            bytes[n / 2] = (byte) ((Character.digit(str.charAt(n), 16) << 4) + Character.digit(str.charAt(n + 1), 16));
        }
        return bytes;
    }

    private void toast(final String msg) {
        runOnUiThread(()-> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private int dip2px(Context ctx, int dips) {
        return (int) Math.ceil(dips * ctx.getResources().getDisplayMetrics().density);
    }

}