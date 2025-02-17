package ml.melun.mangaview;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import ml.melun.mangaview.activity.MainActivity;
import ml.melun.mangaview.mangaview.Decoder;
import ml.melun.mangaview.mangaview.DownloadTitle;
import ml.melun.mangaview.mangaview.Manga;

import static ml.melun.mangaview.MainApplication.httpClient;
import static ml.melun.mangaview.MainApplication.p;
import static ml.melun.mangaview.Utils.CODE_SCOPED_STORAGE;
import static ml.melun.mangaview.Utils.filterFolder;

public class Downloader extends Service {
    String homeDir;
    String baseUrl;
    ArrayList<DownloadTitle> titles;
    ArrayList<JSONArray> selected;
    float progress = 0;
    int maxProgress = 1000;
    String notiTitle = "";
    public static boolean running = false;
    public static boolean updateDownloading = false;
    NotificationCompat.Builder notification;
    public static final String ACTION_START = "ml.melun.mangaview.action.START";
    public static final String ACTION_STOP = "ml.melun.mangaview.action.STOP";
    public static final String ACTION_QUEUE = "ml.melun.mangaview.action.QUEUE";
    public static final String ACTION_UPDATE = "ml.melun.mangaview.action.UPDATE";
    public static final String ACTION_FORCE_STOP = "ml.melun.mangaview.action.FORCE_STOP";
    public static final String BROADCAST_STOP = "ml.melun.mangaview.broadcast.STOP";
    downloadTitle dt;
    Download d;
    NotificationManager notificationManager;
    public static final int nid = 16848323;
    public static final String channeld = "MangaViewDL";
    PendingIntent pendingIntent;
    PendingIntent stopIntent;
    Context serviceContext;
    Map<String, String> cookies;
    int failures = 0;

    public static boolean isRunning() {
        return updateDownloading || running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        serviceContext = this;
        if (titles == null)
            titles = new ArrayList<>();
        if (selected == null)
            selected = new ArrayList<>();
        homeDir = serviceContext.getSharedPreferences("mangaView", Context.MODE_PRIVATE).getString("homeDir", "");
        baseUrl = serviceContext.getSharedPreferences("mangaView", Context.MODE_PRIVATE).getString("url", "");
        if (dt == null)
            dt = new downloadTitle();
        // android O bullshit
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            // notificationManager.deleteNotificationChannel("mangaView");
            NotificationChannel mchannel = new NotificationChannel(channeld, "MangaView",
                    NotificationManager.IMPORTANCE_LOW);
            mchannel.setDescription("다운로드 상태");
            mchannel.enableLights(true);
            mchannel.setLightColor(Color.MAGENTA);
            mchannel.enableVibration(false);
            mchannel.setSound(null, null);
            mchannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            notificationManager.createNotificationChannel(mchannel);
        }
        Intent notificationIntent = new Intent(this, MainActivity.class);
        pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        Intent previousIntent = new Intent(this, Downloader.class);
        previousIntent.setAction(ACTION_STOP);
        stopIntent = PendingIntent.getService(this, 0, previousIntent, PendingIntent.FLAG_IMMUTABLE);
        startNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            switch (intent.getAction()) {
                case ACTION_START:
                    break;
                case ACTION_QUEUE:
                    startNotification();
                    if (dt == null)
                        dt = new downloadTitle();
                    try {
                        DownloadTitle target = new Gson().fromJson(intent.getStringExtra("title"),
                                new TypeToken<DownloadTitle>() {
                                }.getType());
                        JSONArray selection = new JSONArray(intent.getStringExtra("selected"));
                        queueTitle(target, selection);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case ACTION_STOP:
                case ACTION_FORCE_STOP:
                    dt.cancel(true);
                    break;
                case ACTION_UPDATE:
                    if (dt == null)
                        dt = new downloadTitle();
                    String url = intent.getStringExtra("url");
                    update(url);
                    break;
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        finishNotification();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void queueTitle(DownloadTitle title, JSONArray selection) {
        titles.add(title);
        selected.add(selection);
        updateNotification("");
        if (dt.getStatus() == AsyncTask.Status.PENDING || dt.getStatus() == AsyncTask.Status.FINISHED) {
            dt = new downloadTitle();
            dt.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            running = true;
        }
    }

    public void update(String url) {
        // saves to android default download dir
        if (d == null)
            d = new Download();
        if (d.getStatus() == AsyncTask.Status.PENDING || d.getStatus() == AsyncTask.Status.FINISHED) {
            d = new Download();
            d.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url);
        } else {
            updateDownloading = true;
            Toast.makeText(serviceContext, "이미 다운로드 중 입니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private class Download extends AsyncTask<String, Integer, Integer> {
        File downloaded;
        int prevProgress = 0;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            updateDownloading = true;
            Intent intent = new Intent(serviceContext, MainActivity.class);
            PendingIntent intentP = PendingIntent.getActivity(serviceContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder noti = new NotificationCompat.Builder(serviceContext, channeld)
                    .setContentIntent(intentP)
                    .setContentTitle("업데이트 다운로드중")
                    .setOngoing(true);
            if (Build.VERSION.SDK_INT >= 26)
                noti.setSmallIcon(R.drawable.ic_logo);
            else
                noti.setSmallIcon(R.drawable.notification_logo);
            notificationManager.notify(nid + 3, noti.build());
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            if (values[0] > 99) {
                notificationManager.cancel(nid + 3);
            } else {
                Intent intent = new Intent(serviceContext, MainActivity.class);
                PendingIntent intentP = PendingIntent.getActivity(serviceContext, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE);
                NotificationCompat.Builder noti = new NotificationCompat.Builder(serviceContext, channeld)
                        .setContentIntent(intentP)
                        .setContentTitle("업데이트 다운로드중")
                        .setContentText(values[0] + "%")
                        .setOngoing(true);
                if (Build.VERSION.SDK_INT >= 26)
                    noti.setSmallIcon(R.drawable.ic_logo);
                else
                    noti.setSmallIcon(R.drawable.notification_logo);
                notificationManager.notify(nid + 3, noti.build());
            }
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);
            downloaded.setReadable(true, false);
            Uri fileUri = Uri.fromFile(downloaded);
            if (Build.VERSION.SDK_INT >= 24) {
                fileUri = FileProvider.getUriForFile(serviceContext, serviceContext.getPackageName() + ".provider",
                        downloaded);
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            PendingIntent installP = PendingIntent.getActivity(serviceContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder noti = new NotificationCompat.Builder(serviceContext, channeld)
                    .setContentIntent(installP)
                    .setContentTitle("업데이트 다운로드 완료")
                    .setContentText("지금 설치하려면 터치")
                    .setOngoing(false);
            if (Build.VERSION.SDK_INT >= 26)
                noti.setSmallIcon(R.drawable.ic_logo);
            else
                noti.setSmallIcon(R.drawable.notification_logo);
            notificationManager.notify(nid + 4, noti.build());
            updateDownloading = false;
            if (!running)
                stopSelf();
        }

        @Override

        protected Integer doInBackground(String... urls) {
            String url = urls[0];
            downloaded = downloadFile(url,
                    new File(serviceContext.getExternalFilesDir(null).getAbsolutePath(), "mangaview-update"),
                    progress -> {
                        if (progress > prevProgress) {
                            prevProgress = progress;
                            publishProgress(progress);
                        }
                    });
            return null;
        }

        Uri getFileUri(Context context, File file) {
            return FileProvider.getUriForFile(context,
                    context.getPackageName() + ".", file);
        }
    }

    private class downloadTitle extends AsyncTask<Void, Void, Integer> {
        protected void onPreExecute() {
            super.onPreExecute();
            cookies = new HashMap<>();
            running = true;
        }

        protected Integer doInBackground(Void... params) {
            File home = null;
            DocumentFile homed = null;
            try {
                if (Build.VERSION.SDK_INT >= CODE_SCOPED_STORAGE) {
                    homed = DocumentFile.fromTreeUri(serviceContext, Uri.parse(homeDir));
                    if (homed == null) {
                        this.cancel(true);
                        return 1;
                    }
                } else {
                    home = new File(homeDir);
                    if (!home.exists()) {
                        this.cancel(true);
                        return 1;
                    }
                }
            } catch (Exception e) {
                // home folder not set
                this.cancel(true);
                return 4;
            }
            try {
                while (titles.size() > 0) {
                    // reset progress
                    progress = 0;

                    // mget item from queue
                    DownloadTitle title = titles.get(0);
                    JSONArray selectedEps = selected.get(0);

                    notiTitle = title.getName();
                    updateNotification("준비중");

                    // if (title.getEps() == null) title.fetchEps(httpClient);
                    List<Manga> mangas = title.getEps();
                    // todo: minimize eps object(remove 'mode')

                    float stepSize = maxProgress / selectedEps.length();
                    for (int queueIndex = selectedEps.length() - 1; queueIndex >= 0; queueIndex--) {
                        if (isCancelled())
                            return 0;

                        if (Build.VERSION.SDK_INT >= CODE_SCOPED_STORAGE) {
                            // scoped storage
                            DocumentFile titleDir = homed.findFile(filterFolder(title.getName()));
                            if (titleDir == null)
                                titleDir = homed.createDirectory(filterFolder(title.getName()));

                            // if first manga, save title data
                            if (queueIndex == selectedEps.length() - 1) {
                                try {
                                    // save thumbnail
                                    DocumentFile thumb = downloadFile(title.getThumb(), titleDir, "thumb", null);
                                    title.setThumb(thumb.getName());

                                    // save the whole title as gson
                                    DocumentFile dataf = titleDir.findFile("title.gson");
                                    if (dataf != null)
                                        dataf.delete();
                                    Uri data = titleDir.createFile("application", "title.gson").getUri();

                                    OutputStream stream = serviceContext.getContentResolver().openOutputStream(data);
                                    stream.write(new Gson().toJson(title).getBytes());
                                    stream.flush();
                                    stream.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            // mget index from JSONArray
                            int listIndex = 0;
                            try {
                                listIndex = selectedEps.getInt(queueIndex);
                            } catch (Exception e) {
                                this.cancel(true);
                                return 2;
                            }

                            Manga target = mangas.get(listIndex);
                            // error
                            int cf_tries = 3;
                            while (cf_tries > 0) {
                                target.fetch(httpClient, cookies);
                                // todo: cf scrape
                                cf_tries--;
                            }

                            Decoder d = new Decoder(target.getSeed(), target.getId());
                            List<String> urls = target.getImgs(getApplicationContext());

                            // set stepsize
                            float imgStepSize = stepSize / urls.size();

                            // create dir for manga
                            int realIndex = mangas.size() - mangas.indexOf(target);
                            String name = filterFolder(
                                    new DecimalFormat("0000").format(realIndex) + "." + target.getName()) + "."
                                    + target.getId();
                            DocumentFile dir = titleDir.findFile(name);
                            if (dir != null)
                                dir.delete();
                            dir = titleDir.createDirectory(name);

                            // create download flag
                            DocumentFile downloadFlag = dir.findFile("downloading");
                            if (downloadFlag != null)
                                downloadFlag.delete();
                            downloadFlag = dir.createFile("application", "downloading");

                            // download images
                            for (int i = 0; i < urls.size(); i++) {
                                int tries = 0;
                                while (tries < 5) {
                                    // retry for 5 cycles
                                    if (isCancelled())
                                        return 0;
                                    String url = urls.get(i);

                                    if (!downloadImage(url, dir, new DecimalFormat("0000").format(i), d)) {
                                        // change image server name and retry
                                        tries++;
                                    } else // else : success
                                        break;
                                }
                                progress += imgStepSize;
                                updateNotification((selectedEps.length() - queueIndex) + "/" + selectedEps.length());
                            }
                            // check for download failures
                            if (dir.listFiles().length == 0 || dir.listFiles().length < urls.size())
                                failures++;

                            downloadFlag.delete();

                        } else {

                            // create dir for title
                            File titleDir = new File(homeDir, filterFolder(title.getName()));
                            if (!titleDir.exists())
                                titleDir.mkdirs();

                            // if first manga, save title data
                            if (queueIndex == selectedEps.length() - 1) {
                                try {
                                    // save thumbnail
                                    String thumb = downloadFile(title.getThumb(), new File(titleDir, "thumb"))
                                            .getName();
                                    title.setThumb(thumb);

                                    // if old title.data exist, remove file
                                    File old = new File(titleDir, "title.data");
                                    if (old.exists())
                                        old.delete();

                                    // save the whole title as gson
                                    File summary = new File(titleDir, "title.gson");
                                    summary.createNewFile();

                                    FileOutputStream stream = new FileOutputStream(summary);
                                    stream.write(new Gson().toJson(title).getBytes());
                                    stream.flush();
                                    stream.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            // mget index from JSONArray
                            int listIndex = 0;
                            try {
                                listIndex = selectedEps.getInt(queueIndex);
                            } catch (Exception e) {
                                this.cancel(true);
                                return 2;
                            }

                            Manga target = mangas.get(listIndex);
                            // error
                            int cf_tries = 3;
                            while (cf_tries > 0) {
                                target.fetch(httpClient, cookies);
                                cf_tries--;
                            }

                            Decoder d = new Decoder(target.getSeed(), target.getId());
                            List<String> urls = target.getImgs(getApplicationContext());

                            // set stepsize
                            float imgStepSize = stepSize / urls.size();

                            // create dir for manga
                            int realIndex = mangas.size() - mangas.indexOf(target);
                            File dir = new File(titleDir,
                                    filterFolder(new DecimalFormat("0000").format(realIndex) + "." + target.getName())
                                            + "." + target.getId());
                            if (!dir.exists())
                                dir.mkdirs();

                            // create download flag
                            File downloadFlag = new File(dir, "downloading");
                            downloadFlag.createNewFile();
                            // download images
                            for (int i = 0; i < urls.size(); i++) {
                                int tries = 0;
                                while (tries < 5) {
                                    // retry for 5 cycles
                                    if (isCancelled())
                                        return 0;
                                    String url = urls.get(i);

                                    if (!downloadImage(url, new File(dir, new DecimalFormat("0000").format(i)), d)) {
                                        // change image server name and retry
                                        tries++;
                                    } else // else : success
                                        break;
                                }
                                progress += imgStepSize;
                                updateNotification((selectedEps.length() - queueIndex) + "/" + selectedEps.length());
                            }
                            // check for download failures
                            if (dir.listFiles().length == 0 || dir.listFiles().length < urls.size())
                                failures++;

                            downloadFlag.delete();
                        }
                    }
                    titles.remove(0);
                    selected.remove(0);
                }
            } catch (Exception e) {
                // unexpected exception
                e.printStackTrace();
                this.cancel(true);
                return 3;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Integer res) {
            super.onPostExecute(res);
            endNotification();
            running = false;
            if (!updateDownloading)
                stopSelf();
            sendBroadcast(new Intent().setAction(ACTION_STOP));
        }

        @Override
        protected void onCancelled(Integer mode) {
            super.onCancelled();
            running = false;
            String why = "";
            switch (mode) {
                case 0:
                    why = "유저 취소";
                    break;
                case 1:
                    why = "쓰기 실패";
                    break;
                case 2:
                    why = "만화 정보 파싱 실패";
                    break;
                case 3:
                    why = "예상치 못한 오류";
                    break;
                case 4:
                    why = "다운로드 위치를 설정해 주세요";
                    break;
            }
            notificationManager.cancel(nid);
            stopNotification(why);
            if (!updateDownloading)
                stopSelf();
            sendBroadcast(new Intent().setAction(BROADCAST_STOP));
        }
    }

    boolean downloadImage(String urlStr, File outputFile, Decoder d) {
        try {
            URL url = new URL(urlStr);
            if (url.getProtocol().toLowerCase().equals("https")) {
                HttpsURLConnection init = (HttpsURLConnection) url.openConnection();
                init.setRequestProperty("Referer", p.getUrl());
                int responseCode = init.getResponseCode();
                if (responseCode >= 300 && responseCode < 400) {
                    url = new URL(init.getHeaderField("location"));
                } else if (responseCode >= 400) {
                    return false;
                }
            } else {
                HttpURLConnection init = (HttpURLConnection) url.openConnection();
                init.setRequestProperty("Referer", p.getUrl());
                int responseCode = init.getResponseCode();
                if (responseCode >= 300 && responseCode < 400) {
                    url = new URL(init.getHeaderField("location"));
                } else if (responseCode >= 400) {
                    return false;
                }
            }
            // String fileType = url.toString().substring(url.toString().lastIndexOf('.') +
            // 1);
            URLConnection connection = url.openConnection();
            connection.setRequestProperty("Referer", p.getUrl());

            // manatoki gives image files as document
            // String type = connection.getHeaderField("Content-Type");
            //
            // if(!type.startsWith("image/")) {
            // //following file is not image
            // return false;
            // }

            // load image as bitmap
            InputStream in = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            // decode image
            bitmap = d.decode(bitmap);
            // save image
            OutputStream outputStream = new FileOutputStream(outputFile.getAbsolutePath() + ".jpg");
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream); // saving the Bitmap to a file compressed as
                                                                           // a JPEG with 85% compression rate
            in.close();
            outputStream.flush(); // Not really required
            outputStream.close(); // do not forget to close the stream
        } catch (Exception e) {
            e.printStackTrace();
            // retry if old image server
            return false;
        }
        return true;
    }

    boolean downloadImage(String urlStr, DocumentFile parent, String name, Decoder d) {
        try {
            URL url = new URL(urlStr);
            if (url.getProtocol().toLowerCase().equals("https")) {
                HttpsURLConnection init = (HttpsURLConnection) url.openConnection();
                init.setRequestProperty("Referer", p.getUrl());

                int responseCode = init.getResponseCode();
                if (responseCode >= 300 && responseCode < 400) {
                    url = new URL(init.getHeaderField("location"));
                } else if (responseCode >= 400) {
                    return false;
                }
            } else {
                HttpURLConnection init = (HttpURLConnection) url.openConnection();
                init.setRequestProperty("Referer", p.getUrl());
                int responseCode = init.getResponseCode();
                if (responseCode >= 300 && responseCode < 400) {
                    url = new URL(init.getHeaderField("location"));
                } else if (responseCode >= 400) {
                    return false;
                }
            }
            // String fileType = url.toString().substring(url.toString().lastIndexOf('.') +
            // 1);
            URLConnection connection = url.openConnection();
            connection.setRequestProperty("Referer", p.getUrl());

            // load image as bitmap
            InputStream in = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            // decode image
            bitmap = d.decode(bitmap);
            // save image
            String fname = name + ".jpg";
            DocumentFile outputFile = parent.findFile(fname);
            if (outputFile != null)
                outputFile.delete();
            outputFile = parent.createFile("image/jpeg", fname);

            OutputStream outputStream = serviceContext.getContentResolver().openOutputStream(outputFile.getUri());
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream); // saving the Bitmap to a file compressed as
                                                                           // a JPEG with 85% compression rate
            in.close();
            outputStream.flush(); // Not really required
            outputStream.close(); // do not forget to close the stream
        } catch (Exception e) {
            e.printStackTrace();
            // retry if old image server
            return false;
        }
        return true;
    }

    File downloadFile(String urlStr, File outputFile) {
        return downloadFile(urlStr, outputFile, null);
    }

    File downloadFile(String urlStr, File outputFile, ProgressInterface publisher) {
        // returns file name with extension
        String name = "";
        int filesize;
        try {
            URL url = new URL(urlStr);
            if (url.getProtocol().toLowerCase().equals("https")) {
                HttpsURLConnection init = (HttpsURLConnection) url.openConnection();
                int responseCode = init.getResponseCode();
                if (responseCode >= 300) {
                    url = new URL(init.getHeaderField("location"));
                }
            } else {
                HttpURLConnection init = (HttpURLConnection) url.openConnection();
                int responseCode = init.getResponseCode();
                if (responseCode >= 300) {
                    url = new URL(init.getHeaderField("location"));
                }
            }
            String fileType = url.toString().substring(url.toString().lastIndexOf('.') + 1);
            URLConnection connection = url.openConnection();
            filesize = connection.getContentLength();

            // load file
            InputStream in = connection.getInputStream();
            outputFile = new File(outputFile.getAbsolutePath() + '.' + fileType);
            name = outputFile.getName();
            OutputStream outputStream = new FileOutputStream(outputFile);
            // save file
            byte[] buf = new byte[1024];
            int len = 0;
            int cursize = 0;
            while ((len = in.read(buf)) > 0) {
                outputStream.write(buf, 0, len);
                cursize += len;
                if (publisher != null)
                    publisher.publish((int) (((double) cursize / (double) filesize) * 100d));
            }
            in.close();
            outputStream.flush(); // Not really required
            outputStream.close(); // do not forget to close the stream
        } catch (Exception e) {
            //
            e.printStackTrace();
        }
        return outputFile;
    }

    DocumentFile downloadFile(String urlStr, DocumentFile parent, String name, ProgressInterface publisher) {
        // returns file name with extension
        DocumentFile outputFile = null;
        int filesize;
        try {
            URL url = new URL(urlStr);
            if (url.getProtocol().toLowerCase().equals("https")) {
                HttpsURLConnection init = (HttpsURLConnection) url.openConnection();
                int responseCode = init.getResponseCode();
                if (responseCode >= 300) {
                    url = new URL(init.getHeaderField("location"));
                }
            } else {
                HttpURLConnection init = (HttpURLConnection) url.openConnection();
                int responseCode = init.getResponseCode();
                if (responseCode >= 300) {
                    url = new URL(init.getHeaderField("location"));
                }
            }
            String fileType = url.toString().substring(url.toString().lastIndexOf('.') + 1);
            URLConnection connection = url.openConnection();
            filesize = connection.getContentLength();

            // load file
            InputStream in = connection.getInputStream();
            // create file
            DocumentFile pfile = parent.findFile(name + '.' + fileType);
            if (pfile != null)
                pfile.delete();
            outputFile = parent.createFile("image", name + "." + fileType);
            // open stream
            OutputStream outputStream = serviceContext.getContentResolver().openOutputStream(outputFile.getUri());
            // save file
            byte[] buf = new byte[1024];
            int len = 0;
            int cursize = 0;
            while ((len = in.read(buf)) > 0) {
                outputStream.write(buf, 0, len);
                cursize += len;
                if (publisher != null)
                    publisher.publish((int) (((double) cursize / (double) filesize) * 100d));
            }
            in.close();
            outputStream.flush(); // Not really required
            outputStream.close(); // do not forget to close the stream
        } catch (Exception e) {
            //
            e.printStackTrace();
        }
        return outputFile;
    }

    public int getIndex(List<Manga> eps, int id) {
        for (int i = 0; i < eps.size(); i++) {
            if (eps.get(i).getId() == id) {
                return eps.size() - i;
            }
        }
        return 0;
    }

    private void startNotification() {
        notification = new NotificationCompat.Builder(this, channeld)
                .setContentIntent(pendingIntent)
                .setContentTitle("다운로드를 시작합니다")
                .setOngoing(true);
        if (Build.VERSION.SDK_INT >= 26)
            notification.setSmallIcon(R.drawable.ic_logo);
        else
            notification.setSmallIcon(R.drawable.notification_logo);
        startForeground(nid, notification.build());
    }

    private void updateNotification(String text) {
        notification = new NotificationCompat.Builder(this, channeld)
                .setContentIntent(pendingIntent)
                .setContentTitle(notiTitle)
                .setSubText("대기열: " + titles.size())
                .setContentText(text)
                .addAction(R.drawable.blank, "중지", stopIntent)
                .setProgress(maxProgress, (int) progress, !(progress > 0))
                .setOngoing(true);
        if (Build.VERSION.SDK_INT >= 26)
            notification.setSmallIcon(R.drawable.ic_logo);
        else
            notification.setSmallIcon(R.drawable.notification_logo);
        notificationManager.notify(nid, notification.build());
    }

    private void endNotification() {
        notification = new NotificationCompat.Builder(this, channeld)
                .setContentIntent(pendingIntent)
                .setContentTitle("다운로드 완료")
                .setOngoing(false);
        if (Build.VERSION.SDK_INT >= 26)
            notification.setSmallIcon(R.drawable.ic_logo);
        else
            notification.setSmallIcon(R.drawable.notification_logo);
        notificationManager.notify(nid, notification.build());
    }

    private void finishNotification() {
        notification = new NotificationCompat.Builder(this, channeld)
                .setContentIntent(pendingIntent)
                .setContentTitle("모든 다운로드가 완료되었습니다.")
                .setOngoing(false);
        if (Build.VERSION.SDK_INT >= 26)
            notification.setSmallIcon(R.drawable.ic_logo);
        else
            notification.setSmallIcon(R.drawable.notification_logo);
        if (failures > 0) {
            notification.setContentText("누락: " + failures);
            failures = 0;
        }
        notificationManager.notify(nid + 1, notification.build());
    }

    private void stopNotification(String why) {
        notification = new NotificationCompat.Builder(this, channeld)
                .setContentIntent(pendingIntent)
                .setContentText(why)
                .setContentTitle("다운로드가 취소되었습니다.")
                .setOngoing(false);
        if (Build.VERSION.SDK_INT >= 26)
            notification.setSmallIcon(R.drawable.ic_logo);
        else
            notification.setSmallIcon(R.drawable.notification_logo);
        notificationManager.notify(nid + 2, notification.build());
    }

    private interface ProgressInterface {
        void publish(int progress);
    }

}
