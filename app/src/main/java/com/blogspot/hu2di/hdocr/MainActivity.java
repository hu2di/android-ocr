package com.blogspot.hu2di.hdocr;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private ImageView imageView;

    private String[] arrLanguagesCode;
    private String[] arrLanguagesLink;

    private Bitmap image; //our image
    private String datapath = ""; //path to folder containing language data file

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
    }

    private void initView() {
        imageView = (ImageView) findViewById(R.id.imageView);

        RelativeLayout rlOCR = (RelativeLayout) findViewById(R.id.rlOCR);
        rlOCR.setOnClickListener(this);

        arrLanguagesCode = getResources().getStringArray(R.array.language_code);
        arrLanguagesLink = getResources().getStringArray(R.array.language_link);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.rlOCR:
                showDialog();
                break;
        }
    }

    private void showDialog() {
        new MaterialDialog.Builder(this)
                .title(R.string.language)
                .items(R.array.language_name)
                .itemsCallbackSingleChoice(-1, new MaterialDialog.ListCallbackSingleChoice() {
                    @Override
                    public boolean onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                        dialog.dismiss();
                        runOCR(which);
                        return true;
                    }
                })
                .positiveText(R.string.choose)
                .show();
    }

    private void runOCR(int position) {
        datapath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/HDOCR/";
        File f = new File(datapath);
        if (f.exists() || (!f.exists() && f.mkdir())) {
            checkFile(new File(datapath + "tessdata/"), position);
        }
    }

    private void checkFile(File dir, int position) {
        //directory does not exist, but we can successfully create it
        if (!dir.exists() && dir.mkdir()) {
            copyFiles(position);
        }

        //The directory exists, but there is no data file in it
        if (dir.exists()) {
            String langCode = arrLanguagesCode[position];

            String datafilepath = datapath + "/tessdata/" + langCode + ".traineddata";
            File datafile = new File(datafilepath);
            if (!datafile.exists()) {
                copyFiles(position);
            } else {
                new AsyncOCR().execute(langCode);
            }
        }
    }

    private void copyFiles(int position) {
        new AsyncDownload().execute(arrLanguagesCode[position], arrLanguagesLink[position]);
    }

    private class AsyncDownload extends AsyncTask<String, Void, String> {

        private ProgressDialog dialog;

        private String langCode;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog = new ProgressDialog(MainActivity.this);
            dialog.setMessage("Downloading...");
            dialog.show();
        }

        @Override
        protected String doInBackground(String... strings) {
            langCode = strings[0];
            String langLink = strings[1];

            try {
                //location we want the file to be at
                String filepath = datapath + "/tessdata/" + langCode + ".traineddata";

                URL url = new URL(langLink);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // this will be useful so that you can show a typical 0-100% progress bar
                int fileLength = connection.getContentLength();

                // download the file
                InputStream input = new BufferedInputStream(connection.getInputStream());
                OutputStream output = new FileOutputStream(filepath);

                byte data[] = new byte[8192];
                long total = 0;
                int count;
                int i = 0;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    i++;
                    if (i > 5) {
                        // 5 times download to update UI 1 times, to boost speed
                        i = 0;
                        //Notification
                        int progress = (int) (total * 100 / fileLength);
                        Log.d("myLog", "PERCENT: " + progress);
                    }
                    output.write(data, 0, count);

                    if (isCancelled()) {
                        break;
                    }
                }

                output.flush();
                output.close();
                input.close();
                return "OK";
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }

            if (result.equals("OK")) {
                new AsyncOCR().execute(langCode);
            } else {
                Toast.makeText(MainActivity.this, "Download failure!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class AsyncOCR extends AsyncTask<String, Void, String> {

        private ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            //init image
            image = ((BitmapDrawable) imageView.getDrawable()).getBitmap();

            dialog = new ProgressDialog(MainActivity.this);
            dialog.setMessage("Analyzing...");
            dialog.show();
        }

        @Override
        protected String doInBackground(String... strings) {
            String OCRresult = null;
            try {
                //init Tesseract API
                String language = strings[0];

                TessBaseAPI mTess = new TessBaseAPI();
                mTess.init(datapath, language);

                mTess.setImage(image);
                OCRresult = mTess.getUTF8Text();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return OCRresult;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }

            if (result != null) {
                showResultOCR(result);
            } else {
                Toast.makeText(MainActivity.this, "Analyze failure!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showResultOCR(final String result) {
        new MaterialDialog.Builder(this)
                .title("Result")
                .content(result)
                .negativeText("Edit")
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        Intent i = new Intent(MainActivity.this, KnifeActivity.class);
                        i.putExtra("resultOCR", result);
                        startActivity(i);
                    }
                })
                .positiveText("Copy")
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Copied Text", result);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(MainActivity.this, "Copied to Clipboard", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }
}
